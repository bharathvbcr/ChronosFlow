package com.chronosflow.core.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.AppLaunchTarget
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
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
    private val alarmScheduler: AlarmScheduler
) {
    suspend fun deliverFromAlarmIntent(intent: Intent, receiverClass: Class<*>) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "ChronosFlow Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "It's time for your scheduled task."
        val requestId = intent.getStringExtra(EXTRA_ID)

        val alarmRequest = requestId?.let { alarmRequestRepository.getAlarmRequest(it) }
        val channelId = ReminderNotificationChannels.channelIdFor(
            receiverClass = receiverClass,
            requestType = alarmRequest?.type
        )
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val task = loadTaskForRequest(requestId)
        val habitLaunchTarget = if (task == null) loadHabitLaunchTargetForRequest(requestId) else null
        val contentIntent = habitLaunchTarget
            ?.let { target ->
                buildAppLaunchPendingIntent(
                    context = context,
                    target = target,
                    requestCode = requestId?.hashCode() ?: target.value.hashCode()
                )
            }
            ?: buildTaskNotificationContentIntent(
                context = context,
                task = task,
                requestId = requestId,
                receiverClass = receiverClass
            )

        val notificationId = requestId?.hashCode() ?: System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_chronos_alarm)
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

        val blockId = alarmRequest?.blockId
            ?: requestId?.takeIf { it.startsWith("daydial:") }
                ?.split(":")
                ?.getOrNull(2)
                ?.takeUnless { it == "day" }
        val habitId = blockId?.takeIf { it.startsWith("habit-") }?.removePrefix("habit-")
            ?: blockId?.takeIf { habitRepository.getHabitById(it) != null }

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

        val isMedication = alarmRequest?.type == AlarmRequestType.MEDICATION || receiverClass == MedicationAlarmReceiver::class.java
        val medicationPlanId = alarmRequest?.medicationPlanId
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

        val notificationId = plan.id.hashCode() + 200
        val message = "Only $remaining ${plan.unit}(s) left of ${plan.name}. Please request a refill soon."
        val builtNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_chronos_alarm)
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
                requestCode = requestId?.hashCode() ?: task.id.hashCode()
            )?.let { return it }
        }
        return buildNotificationContentIntent(
            context = context,
            launch = NotificationLaunch(
                section = SECTION_TASKS,
                taskId = task.id,
                target = TASK_LAUNCH_TARGET_CONTEXT
            ),
            requestCode = requestId?.hashCode() ?: task.id.hashCode()
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
            requestCode = (requestId?.hashCode() ?: target.value.hashCode()) + HABIT_APP_ACTION_REQUEST_OFFSET
        )
    }

    private fun buildTaskNotificationActions(
        context: Context,
        task: Task?
    ): List<TaskContextCommand> {
        if (task == null) return emptyList()
        return safeExternalTaskContextCommands(context, task, limit = 3)
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
