package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.AppLaunchTarget
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.ProactiveDigestKeys
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delivers alarm-fired notifications off the broadcast receiver thread.
 * Uses [android.content.BroadcastReceiver.goAsync] from [AlarmReceiver] rather than WorkManager
 * so reminders post immediately while the app has its alarm wakeup budget.
 */
@Singleton
class AlarmDeliveryCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmRequestRepository: AlarmRequestRepository,
    private val taskRepository: TaskRepository,
    private val timeBlockRepository: TimeBlockRepository,
    private val habitRepository: HabitRepository,
    private val alarmScheduler: AlarmScheduler,
    private val currentBlockNotificationCoordinator: CurrentBlockNotificationCoordinator,
    private val foldedReminderResolver: FoldedReminderResolver,
    private val stableNotificationCodes: StableNotificationCodes
) {
    suspend fun deliverFromAlarmIntent(intent: Intent, receiverClass: Class<*>) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "ChronosFlow Reminder"
        val defaultMessage = intent.getStringExtra(EXTRA_MESSAGE) ?: "It's time for your scheduled task."
        val requestId = intent.getStringExtra(EXTRA_ID)

        val alarmRequest = requestId?.let { alarmRequestRepository.getAlarmRequest(it) }

        // Suppress cancelled alarms (de-listed after the owning task/rule changed) without posting.
        if (alarmRequest?.deliveryState == AlarmDeliveryState.CANCELLED) return

        // Unified "now" surface: a plain planner-block start refreshes the live current-block
        // notification (which alerts once as the block begins) instead of posting a separate
        // transient reminder + group summary. Habit/task/medication reminders are untouched, and
        // this falls back to the normal reminder when the user has turned the "now" notification off.
        if (isReroutableBlockStart(alarmRequest) && currentBlockNotificationCoordinator.isEnabled()) {
            currentBlockNotificationCoordinator.refresh(alert = true)
            markDelivered(requestId)
            if (requestId != null) {
                alarmScheduler.cancelAlarm(requestId)
            }
            return
        }
        // For the end-of-day review reminder, prefer the proactive digest that Gemini Nano
        // pre-generated while the app was foregrounded (cached in shared prefs) over the static copy.
        val message = dailyReviewDigestOverride(alarmRequest?.type) ?: defaultMessage
        val channelId = ReminderNotificationChannels.channelIdFor(
            receiverClass = receiverClass,
            requestType = alarmRequest?.type
        )

        val task = loadTaskForRequest(requestId)
        val isMedication = alarmRequest?.type == AlarmRequestType.MEDICATION ||
            receiverClass == MedicationAlarmReceiver::class.java
        val medicationPlanId = alarmRequest?.medicationPlanId
        val blockId = alarmRequest?.blockId
            ?: requestId?.takeIf { it.startsWith("daydial:") }
                ?.split(":")
                ?.getOrNull(2)
                ?.takeUnless { it == "day" }
        val habitId = blockId?.takeIf { it.startsWith("habit-") }?.removePrefix("habit-")
            ?: blockId?.takeIf { habitRepository.getHabitById(it) != null }

        // Fold the reminder into the live "now" surface when folding is on AND that surface is
        // actually visible. While a focus session runs, the now notification is suppressed —
        // refreshing it is a no-op that cancels — so folding here would swallow the reminder
        // entirely (marked DELIVERED, alarm cancelled, surfaced nowhere). In that state the
        // reminder falls through to its own normal notification instead.
        if (currentBlockNotificationCoordinator.isFoldRemindersEnabled() &&
            !currentBlockNotificationCoordinator.isSuppressedByFocus()
        ) {
            val folded = foldedReminderResolver.resolve(
                LocalDateTime.now(),
                ZoneId.systemDefault(),
                FoldToggles.fromPreferences(context)
            )
            if (shouldSuppressFoldedReminderBanner(
                    folded = foldedEntityKeys(folded),
                    medicationPlanId = medicationPlanId?.takeIf { isMedication },
                    taskId = task?.id,
                    habitId = habitId
                )
            ) {
                currentBlockNotificationCoordinator.refresh(alert = false)
                markDelivered(requestId)
                if (requestId != null) {
                    alarmScheduler.cancelAlarm(requestId)
                }
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Defensive channel creation before posting: if the user deleted the reminders channel in
        // system settings, notifications posted into a missing channel are dropped by the OS.
        ReminderNotificationChannels.ensureCreated(context)

        val habitLaunchTarget = if (task == null) loadHabitLaunchTargetForRequest(requestId) else null
        // Stable, collision-free codes: hashCode()-derived request codes collide across entities,
        // and PendingIntent equality ignores extras — a collision would route "Mark Done" for one
        // entity to a different one. Keys are scoped per purpose so content/action intents of the
        // same reminder never share a code.
        val contentIntent = habitLaunchTarget
            ?.let { target ->
                buildAppLaunchPendingIntent(
                    context = context,
                    target = target,
                    requestCode = stableNotificationCodes.codeFor("launch:habit:${requestId ?: target.value}")
                )
            }
            ?: buildTaskNotificationContentIntent(
                context = context,
                task = task,
                requestId = requestId,
                receiverClass = receiverClass
            )

        val notificationId = requestId?.let { stableNotificationCodes.codeFor("notif:$it") }
            ?: (System.currentTimeMillis().toInt() and Int.MAX_VALUE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_chronosflow_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setColor(accentColorFor(channelId))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setGroup(ReminderNotificationGroups.GROUP_KEY)
            .setDeleteIntent(ReminderNotificationGroups.deleteIntent(context))
            .setPriority(ReminderNotificationChannels.compatPriorityFor(channelId))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
        buildTaskNotificationActions(context, task).forEachIndexed { index, command ->
            buildTaskContextCommandPendingIntent(
                context = context,
                command = command,
                requestCode = notificationId + index + 1
            )?.let { pendingIntent ->
                notification.addAction(0, command.shortLabel, pendingIntent)
            }
        }
        buildHabitLaunchActionPendingIntent(context, habitLaunchTarget, requestId)?.let { pendingIntent ->
            notification.addAction(0, habitLaunchTarget?.label ?: "Open app", pendingIntent)
        }

        if (habitId != null) {
            val habit = habitRepository.getHabitById(habitId)
            if (habit != null) {
                val completeIntent = Intent(context, HabitActionReceiver::class.java).apply {
                    action = HabitActionReceiver.ACTION_COMPLETE
                    putExtra(HabitActionReceiver.EXTRA_HABIT_ID, habitId)
                    putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
                val completePendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId + 201,
                    completeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notification.addAction(R.drawable.ic_notif_check, "Mark Done", completePendingIntent)
            }
        }

        if (task != null && !task.isCompleted) {
            val completeIntent = Intent(context, TaskActionReceiver::class.java).apply {
                action = TaskActionReceiver.ACTION_COMPLETE
                putExtra(TaskActionReceiver.EXTRA_TASK_ID, task.id)
                putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 301,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notification.addAction(0, "Mark Done", completePendingIntent)
        }

        if (isMedication && medicationPlanId != null) {
            val takeIntent = Intent(context, MedicationActionReceiver::class.java).apply {
                action = MedicationActionReceiver.ACTION_TAKE
                putExtra(MedicationActionReceiver.EXTRA_MEDICATION_PLAN_ID, medicationPlanId)
                putExtra(MedicationActionReceiver.EXTRA_REQUEST_ID, requestId)
                putExtra(MedicationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val takePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 101,
                takeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, MedicationActionReceiver::class.java).apply {
                action = MedicationActionReceiver.ACTION_SNOOZE
                putExtra(MedicationActionReceiver.EXTRA_MEDICATION_PLAN_ID, medicationPlanId)
                putExtra(MedicationActionReceiver.EXTRA_REQUEST_ID, requestId)
                putExtra(MedicationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 102,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val skipIntent = Intent(context, MedicationActionReceiver::class.java).apply {
                action = MedicationActionReceiver.ACTION_SKIP
                putExtra(MedicationActionReceiver.EXTRA_MEDICATION_PLAN_ID, medicationPlanId)
                putExtra(MedicationActionReceiver.EXTRA_REQUEST_ID, requestId)
                putExtra(MedicationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val skipPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 103,
                skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            notification.addAction(R.drawable.ic_notif_check, "Take", takePendingIntent)
            notification.addAction(R.drawable.ic_notif_snooze, "Snooze 15m", snoozePendingIntent)
            notification.addAction(R.drawable.ic_notif_skip, "Skip", skipPendingIntent)
        }

        // "Remind me to read later": offer Mark read + Snooze straight from the notification.
        // Keyed off the requestId convention ("reading:<itemId>") rather than the persisted type,
        // because reading reminders schedule the OS alarm without saving an AlarmRequest row.
        val readingItemId = requestId?.takeIf { it.startsWith("reading:") }?.removePrefix("reading:")
        if (readingItemId != null) {
            val markReadIntent = Intent(context, ReadingReminderActionReceiver::class.java).apply {
                action = ReadingReminderActionReceiver.ACTION_MARK_READ
                putExtra(ReadingReminderActionReceiver.EXTRA_READING_ITEM_ID, readingItemId)
                putExtra(ReadingReminderActionReceiver.EXTRA_REQUEST_ID, requestId)
                putExtra(ReadingReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val markReadPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 501,
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val snoozeReadingIntent = Intent(context, ReadingReminderActionReceiver::class.java).apply {
                action = ReadingReminderActionReceiver.ACTION_SNOOZE
                putExtra(ReadingReminderActionReceiver.EXTRA_READING_ITEM_ID, readingItemId)
                putExtra(ReadingReminderActionReceiver.EXTRA_REQUEST_ID, requestId)
                putExtra(ReadingReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(ReadingReminderActionReceiver.EXTRA_TITLE, title)
            }
            val snoozeReadingPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 502,
                snoozeReadingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notification.addAction(R.drawable.ic_notif_check, "Mark read", markReadPendingIntent)
            notification.addAction(R.drawable.ic_notif_snooze, "Snooze 2h", snoozeReadingPendingIntent)
        }

        // Plain planner-block reminders (not a habit/task/medication) carry a one-tap recovery
        // action so a slipping day can be reflowed from the lock screen without opening the app.
        val isPlannerBlockReminder = blockId != null && habitId == null && task == null && !isMedication
        if (isPlannerBlockReminder) {
            val reflowIntent = Intent(context, ReflowDayActionReceiver::class.java).apply {
                action = ReflowDayActionReceiver.ACTION_REFLOW_DAY
                putExtra(ReflowDayActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val reflowPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 401,
                reflowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notification.addAction(0, "Reflow day", reflowPendingIntent)
        }

        val isHabitCategory = habitId != null || habitLaunchTarget != null
        val isTaskCategory = task != null
        // Sticky habit/task reminders: ongoing (can't be swiped away) so they persist until you act,
        // while autoCancel still clears them on a body tap or via the Mark Done action. Medication,
        // block-start, review, and low-supply reminders are intentionally left swipe-dismissable.
        notification.setOngoing(isHabitCategory || isTaskCategory)
        notification.setSubText(
            ReminderNotificationGroups.categoryLabel(
                isMedication = isMedication,
                isHabit = isHabitCategory,
                isTask = isTaskCategory
            )
        )
        notification.setLargeIcon(
            NotificationIcons.categoryBadge(
                context = context,
                iconRes = ReminderNotificationGroups.categoryIconRes(
                    isMedication = isMedication,
                    isHabit = isHabitCategory,
                    isTask = isTaskCategory
                ),
                backgroundColor = accentColorFor(channelId)
            )
        )

        val builtNotification = notification.build()
        notificationManager.notify(notificationId, builtNotification)
        ReminderNotificationGroups.refreshSummary(context)

        markDelivered(requestId)
        if (requestId != null) {
            alarmScheduler.cancelAlarm(requestId)
        }
    }

    fun deliverLowSupplyWarning(plan: MedicationPlan, remaining: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = ReminderNotificationChannels.CRITICAL_CHANNEL_ID
        ReminderNotificationChannels.ensureCreated(context)

        val notificationId = stableNotificationCodes.codeFor("supply:${plan.id}") + LOW_SUPPLY_NOTIFICATION_OFFSET
        val message = "Only $remaining ${plan.unit}(s) left of ${plan.name}. Please request a refill soon."
        val builtNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_chronosflow_notification)
            .setContentTitle("Low Medication Supply: ${plan.name}")
            .setContentText(message)
            .setSubText(ReminderNotificationGroups.categoryLabel(isMedication = true, isHabit = false, isTask = false))
            .setLargeIcon(
                NotificationIcons.categoryBadge(
                    context = context,
                    iconRes = ReminderNotificationGroups.categoryIconRes(isMedication = true, isHabit = false, isTask = false),
                    backgroundColor = accentColorFor(channelId)
                )
            )
            .setColor(accentColorFor(channelId))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setGroup(ReminderNotificationGroups.GROUP_KEY)
            .setDeleteIntent(ReminderNotificationGroups.deleteIntent(context))
            .setPriority(ReminderNotificationChannels.compatPriorityFor(channelId))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, builtNotification)
        ReminderNotificationGroups.refreshSummary(context)
    }

    /**
     * The cached proactive digest for the daily-review reminder, or null to keep the default copy.
     * Reads the shared prefs directly (no [com.ChronosFlow.VBCR.core.ai] dependency) and only returns a
     * digest generated for today, so a stale entry never shows on a later day.
     */
    private fun dailyReviewDigestOverride(type: AlarmRequestType?): String? {
        if (type != AlarmRequestType.DAILY_REVIEW) return null
        val prefs = context.getSharedPreferences(ProactiveDigestKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val text = prefs.getString(ProactiveDigestKeys.KEY_TEXT, null)?.takeIf { it.isNotBlank() } ?: return null
        val forDate = prefs.getString(ProactiveDigestKeys.KEY_FOR_DATE, null)
        return text.takeIf { forDate == LocalDate.now().toString() }
    }

    private suspend fun loadTaskForRequest(requestId: String?): Task? {
        if (requestId == null) return null
        val taskId = alarmRequestRepository.getAlarmRequest(requestId)?.blockId
            ?: requestId.takeIf { it.startsWith("task:") }
                ?.removePrefix("task:")
                ?.substringBefore(":")
        return taskId?.let { taskRepository.getTaskById(it) }
    }

    private suspend fun loadHabitLaunchTargetForRequest(requestId: String?): AppLaunchTarget? {
        if (requestId == null) return null
        val blockId = alarmRequestRepository.getAlarmRequest(requestId)?.blockId
            ?: requestId.takeIf { it.startsWith("daydial:") }
                ?.split(":")
                ?.getOrNull(2)
                ?.takeUnless { it == "day" }
        val syntheticHabitId = blockId?.takeIf { it.startsWith("habit-") }?.removePrefix("habit-")
        val syntheticTarget = syntheticHabitId?.let { habitRepository.getHabitById(it)?.launchTarget }
        if (syntheticTarget != null) return syntheticTarget
        val directHabitId = syntheticHabitId ?: blockId
        val directTarget = directHabitId?.let { habitRepository.getHabitById(it)?.launchTarget }
        if (directTarget != null) return directTarget
        val fallbackHabitId = blockId?.let { timeBlockRepository.getTimeBlockById(it)?.habitId }
        return fallbackHabitId?.let { habitRepository.getHabitById(it)?.launchTarget }
    }

    private suspend fun markDelivered(requestId: String?) {
        if (requestId == null) return
        val request = alarmRequestRepository.getAlarmRequest(requestId) ?: return
        alarmRequestRepository.saveAlarmRequest(
            request.copy(
                deliveryState = AlarmDeliveryState.DELIVERED,
                deliveredAt = Instant.now(),
                updatedAt = Instant.now(),
                failureReason = null
            )
        )
    }

    private fun buildTaskNotificationContentIntent(
        context: Context,
        task: Task?,
        requestId: String?,
        receiverClass: Class<*>
    ): android.app.PendingIntent {
        if (task == null) {
            return buildNotificationContentIntent(
                context = context,
                requestId = requestId,
                receiverClass = receiverClass
            )
        }
        val safeCommands = safeExternalTaskContextCommands(context, task)
        val singleCommand = safeCommands.singleOrNull()
        if (singleCommand != null) {
            buildTaskContextCommandPendingIntent(
                context = context,
                command = singleCommand,
                requestCode = stableNotificationCodes.codeFor("content:${requestId ?: task.id}")
            )?.let { return it }
        }
        return buildNotificationContentIntent(
            context = context,
            launch = NotificationLaunch(
                section = SECTION_TASKS,
                taskId = task.id,
                target = TASK_LAUNCH_TARGET_CONTEXT
            ),
            requestCode = stableNotificationCodes.codeFor("content:${requestId ?: task.id}")
        )
    }

    private fun buildHabitLaunchActionPendingIntent(
        context: Context,
        target: AppLaunchTarget?,
        requestId: String?
    ): android.app.PendingIntent? {
        if (target == null) return null
        return buildAppLaunchPendingIntent(
            context = context,
            target = target,
            requestCode = stableNotificationCodes.codeFor("habitapp:${requestId ?: target.value}") +
                HABIT_APP_ACTION_REQUEST_OFFSET
        )
    }

    private fun buildTaskNotificationActions(
        context: Context,
        task: Task?
    ): List<TaskContextCommand> {
        if (task == null) return emptyList()
        return safeExternalTaskContextCommands(context, task, limit = 3)
    }

    /**
     * True for a plain planner time-block start (the alarm that used to post "Your planned block
     * starts now"). Habit starts (blockId "habit-…"), medication doses, and task reminders keep
     * their own dedicated notifications, so they are excluded from the "now" reroute.
     */
    private fun isReroutableBlockStart(request: AlarmRequest?): Boolean {
        if (request?.type != AlarmRequestType.BLOCK_START) return false
        if (request.medicationPlanId != null) return false
        val blockId = request.blockId ?: return false
        return !blockId.startsWith("habit-")
    }

    private fun accentColorFor(channelId: String): Int {
        val colorRes = if (channelId == ReminderNotificationChannels.CRITICAL_CHANNEL_ID) {
            R.color.notification_accent_critical
        } else {
            R.color.notification_accent
        }
        return ContextCompat.getColor(context, colorRes)
    }

    companion object {
        const val EXTRA_ID = "EXTRA_ID"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
    }
}

private const val HABIT_APP_ACTION_REQUEST_OFFSET = 47
private const val LOW_SUPPLY_NOTIFICATION_OFFSET = 200
