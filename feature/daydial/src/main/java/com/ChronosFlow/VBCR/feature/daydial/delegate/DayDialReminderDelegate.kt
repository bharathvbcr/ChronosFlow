package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.model.HabitRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.model.buildLegacyHabitSchedule
import com.ChronosFlow.VBCR.core.domain.model.occupiesScheduleTime
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrence
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrenceType
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduleResult
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DayDialReminderDelegate @Inject constructor(
    private val repository: TimeBlockRepository,
    private val alarmScheduler: AlarmScheduler,
    private val alarmRequestRepository: AlarmRequestRepository,
    private val habitRepository: HabitRepository
) {
    private val scheduledReminderIds = mutableSetOf<String>()
    private val _reminderScheduleStatus = MutableStateFlow("Reminders not scheduled")
    val reminderScheduleStatus = _reminderScheduleStatus.asStateFlow()

    fun medicationReliabilityStatus(scope: CoroutineScope) =
        alarmRequestRepository.observeRequestsByType(AlarmRequestType.MEDICATION)
            .map { requests -> medicationReliabilityMessage(requests) }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(5000),
                "Medication reminders: no scheduled medication alarms yet"
            )

    fun refreshReminderSchedule(
        scope: CoroutineScope,
        date: LocalDate,
        blockStartReminders: Boolean,
        breakReminders: Boolean,
        missedAlerts: Boolean,
        endDayReviewReminder: Boolean,
        sleepScheduleEnabled: Boolean,
        sleepScheduleStartMinute: Int,
        sleepScheduleEndMinute: Int,
        journalRemindersEnabled: Boolean = true,
        sleepJournalLogReminder: Boolean = false,
        sleepJournalRemindersEnabled: Boolean = true
    ) {
        scope.launch {
            val blocks = repository.getTimeBlocksByDate(date).first()
            val habits = habitRepository.observeHabits().first()
            // Completed blocks (actual time logged) shouldn't keep firing start/break/missed
            // reminders. Excluding them here lets the stale-id sweep below cancel any alarms
            // that were scheduled before the block was marked complete.
            val reminderBlocks = blocks.filter { it.occupiesScheduleTime() && it.actualEndMinuteOfDay == null }
            val nextReminderIds = mutableSetOf<String>()
            var scheduled = 0
            var skipped = 0
            var denied = 0
            val reminderTypesEnabled = blockStartReminders ||
                breakReminders ||
                missedAlerts ||
                endDayReviewReminder ||
                sleepJournalLogReminder
            val sleepSchedule = SleepSchedule(
                enabled = sleepScheduleEnabled,
                startMinute = sleepScheduleStartMinute,
                endMinute = sleepScheduleEndMinute
            )

            suspend fun schedule(
                id: String,
                minuteOfDay: Int,
                title: String,
                message: String,
                type: AlarmRequestType,
                blockId: String? = null,
                medicationPlanId: String? = null
            ) {
                if (type != AlarmRequestType.MEDICATION && sleepSchedule.contains(minuteOfDay)) {
                    alarmScheduler.cancelAlarm(id)
                    persistAlarmRequest(
                        id = id,
                        type = type,
                        scheduledFor = date.atStartOfDay(ZoneId.systemDefault())
                            .plusMinutes(minuteOfDay.coerceIn(0, 1439).toLong())
                            .toInstant(),
                        title = title,
                        message = message,
                        result = AlarmScheduleResult.Skipped(id, "Reminder falls inside the sleep schedule"),
                        blockId = blockId,
                        medicationPlanId = medicationPlanId
                    )
                    skipped++
                    return
                }

                val instant = date.atStartOfDay(ZoneId.systemDefault())
                    .plusMinutes(minuteOfDay.coerceIn(0, 1439).toLong())
                    .toInstant()
                nextReminderIds.add(id)
                // Block-start and medication reminders are time-critical, so they use
                // exact alarms (degrading to a windowed alarm when the user hasn't
                // granted exact-alarm access). Break/missed/review nudges stay inexact
                // per platform guidance to use inexact alarms whenever possible.
                val result = if (type == AlarmRequestType.MEDICATION || type == AlarmRequestType.BLOCK_START) {
                    alarmScheduler.scheduleExactAlarm(id, instant, title, message)
                } else {
                    alarmScheduler.scheduleInexactAlarm(id, instant, title, message)
                }
                persistAlarmRequest(id, type, instant, title, message, result, blockId, medicationPlanId)
                when (result) {
                    is AlarmScheduleResult.Scheduled -> scheduled++
                    is AlarmScheduleResult.Skipped -> skipped++
                    is AlarmScheduleResult.ExactDenied,
                    is AlarmScheduleResult.PermissionDenied -> denied++
                }
            }

            if (blockStartReminders) {
                reminderBlocks.forEach { block ->
                    schedule(
                        id = reminderId(date, block.id, "start"),
                        minuteOfDay = block.startMinuteOfDay,
                        title = block.title,
                        message = "Your planned block starts now.",
                        type = AlarmRequestType.BLOCK_START,
                        blockId = block.id
                    )
                }

                habits
                    .filter { it.isActive }
                    .filter { isHabitReminderDueOnDate(it, date) }
                    .forEach { habit ->
                        val habitSchedule = habit.schedule ?: buildLegacyHabitSchedule(
                            habitId = habit.id,
                            cadence = habit.cadence,
                            windowStartMinute = habit.windowStartMinute,
                            windowEndMinute = habit.windowEndMinute,
                            plannerVisible = habit.isBundled
                        )
                        val reminderMinute = habitSchedule.deferUntilMinuteOfDay ?: habitSchedule.targetStartMinute
                        schedule(
                            id = reminderId(date, "habit-${habit.id}", "start"),
                            minuteOfDay = reminderMinute,
                            title = habit.title.ifBlank { "Habit reminder" },
                            message = "${habit.title.ifBlank { "Habit" }} starts now",
                            type = AlarmRequestType.BLOCK_START,
                            blockId = "habit-${habit.id}"
                        )
                    }
            }

            if (breakReminders) {
                reminderBlocks.forEach { block ->
                    schedule(
                        id = reminderId(date, block.id, "break"),
                        minuteOfDay = block.plannedEndMinuteOfDay,
                        title = "Break check",
                        message = "Wrap ${block.title} and take a short reset.",
                        type = AlarmRequestType.FOCUS_BLOCK,
                        blockId = block.id
                    )
                }
            }

            if (missedAlerts) {
                reminderBlocks.forEach { block ->
                    schedule(
                        id = reminderId(date, block.id, "missed"),
                        minuteOfDay = (block.startMinuteOfDay + 10).coerceAtMost(1439),
                        title = "Progress check",
                        message = "Check whether ${block.title} started as planned.",
                        type = AlarmRequestType.FOCUS_BLOCK,
                        blockId = block.id
                    )
                }
            }

            // The daily review reminder deep-links to the journal sheet, so suppress it
            // when the Journal feature is turned off (the tap would otherwise dead-end).
            if (endDayReviewReminder && journalRemindersEnabled) {
                schedule(
                    id = reminderId(date, "day", "review"),
                    minuteOfDay = 21 * 60,
                    title = "Daily review",
                    message = "Review your day and capture tonight's journal.",
                    type = AlarmRequestType.DAILY_REVIEW
                )
            }

            // Evening nudge to log the night and journal. Fires a little earlier than the review
            // so it lands before a typical sleep window (later times get skipped by `schedule`),
            // and taps deep-link to the sleep log sheet (see NotificationLaunchIntent ":logsleep").
            // Suppressed when both capture surfaces are off, so the tap can't dead-end.
            if (sleepJournalLogReminder && sleepJournalRemindersEnabled) {
                schedule(
                    id = reminderId(date, "day", "logsleep"),
                    minuteOfDay = SLEEP_JOURNAL_LOG_REMINDER_MINUTE,
                    title = "Log sleep & journal",
                    message = "Log last night's sleep and capture today's journal.",
                    type = AlarmRequestType.LOG_REMINDER
                )
            }

            scheduledReminderIds
                .filterNot { it in nextReminderIds }
                .forEach { id ->
                    alarmScheduler.cancelAlarm(id)
                    alarmRequestRepository.getAlarmRequest(id)?.let { previous ->
                        alarmRequestRepository.saveAlarmRequest(
                            previous.copy(
                                reliability = AlarmReliability.BLOCKED,
                                deliveryState = AlarmDeliveryState.CANCELLED,
                                updatedAt = Instant.now(),
                                failureReason = "Cancelled by DayDial reminder settings"
                            )
                        )
                    }
                }
            scheduledReminderIds.clear()
            scheduledReminderIds.addAll(nextReminderIds)

            _reminderScheduleStatus.value = when {
                denied > 0 -> "Scheduled $scheduled; $denied need notification or exact-alarm permission"
                scheduled > 0 && skipped > 0 -> "Scheduled $scheduled upcoming; skipped $skipped during sleep hours"
                scheduled > 0 -> "Scheduled $scheduled upcoming reminders"
                skipped > 0 -> "Skipped $skipped reminders during sleep hours"
                !reminderTypesEnabled -> "All reminders off"
                nextReminderIds.isEmpty() -> "No upcoming reminders for this day"
                else -> "No reminder schedule changes for this day"
            }
        }
    }

    fun openExactAlarmSettings() {
        alarmScheduler.routeToExactAlarmSetting()
    }

    private fun medicationReliabilityMessage(requests: List<AlarmRequest>): String {
        if (requests.isEmpty()) return "Medication reminders: no scheduled medication alarms yet"
        val active = requests.filter { it.deliveryState != AlarmDeliveryState.CANCELLED }
        val blocked = active.count {
            it.reliability == AlarmReliability.BLOCKED ||
                it.deliveryState == AlarmDeliveryState.FAILED
        }
        val degraded = active.count {
            it.reliability == AlarmReliability.DEGRADED_WINDOW ||
                it.reliability == AlarmReliability.INEXACT ||
                it.deliveryState == AlarmDeliveryState.DEGRADED
        }
        val exact = active.count {
            it.reliability == AlarmReliability.EXACT &&
                it.deliveryState in setOf(AlarmDeliveryState.SCHEDULED, AlarmDeliveryState.DELIVERED)
        }
        return when {
            blocked > 0 -> "Medication reminders blocked: $blocked alarm(s) need notification or exact-alarm permission."
            degraded > 0 -> "Medication reminders degraded: $degraded alarm(s) are using fallback delivery windows."
            exact > 0 -> "Medication reminders exact: $exact alarm(s) scheduled or delivered with exact reliability."
            else -> "Medication reminders pending: ${active.size} alarm(s) awaiting scheduling status."
        }
    }

    private fun reminderId(date: LocalDate, blockId: String, type: String): String {
        return "daydial:${date}:$blockId:$type"
    }

    private suspend fun persistAlarmRequest(
        id: String,
        type: AlarmRequestType,
        scheduledFor: Instant,
        title: String,
        message: String,
        result: AlarmScheduleResult,
        blockId: String?,
        medicationPlanId: String?
    ) {
        val now = Instant.now()
        val (reliability, deliveryState, failureReason) = when (result) {
            is AlarmScheduleResult.Scheduled -> if (result.exact) {
                Triple(AlarmReliability.EXACT, AlarmDeliveryState.SCHEDULED, null)
            } else if (type != AlarmRequestType.MEDICATION) {
                Triple(AlarmReliability.INEXACT, AlarmDeliveryState.SCHEDULED, null)
            } else {
                Triple(
                    AlarmReliability.DEGRADED_WINDOW,
                    AlarmDeliveryState.DEGRADED,
                    "Exact alarm permission unavailable; scheduled with fallback window"
                )
            }
            is AlarmScheduleResult.Skipped -> Triple(AlarmReliability.BLOCKED, AlarmDeliveryState.FAILED, result.reason)
            is AlarmScheduleResult.ExactDenied -> Triple(AlarmReliability.BLOCKED, AlarmDeliveryState.FAILED, "Exact alarm permission denied")
            is AlarmScheduleResult.PermissionDenied -> Triple(AlarmReliability.BLOCKED, AlarmDeliveryState.FAILED, "Notification permission denied")
        }
        val existing = alarmRequestRepository.getAlarmRequest(id)
        alarmRequestRepository.saveAlarmRequest(
            AlarmRequest(
                id = id,
                type = type,
                scheduledFor = scheduledFor,
                title = title,
                message = message,
                medicationPlanId = medicationPlanId,
                blockId = blockId,
                reliability = reliability,
                deliveryState = deliveryState,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                failureReason = failureReason
            )
        )
    }

    private companion object {
        // 8 PM — before a typical sleep window so the nudge isn't skipped, and late enough that
        // the day's sleep and journal are worth logging.
        const val SLEEP_JOURNAL_LOG_REMINDER_MINUTE = 20 * 60
    }
}

// Shared with DayDialAiDelegate's gap filler so "due today" means the same
// thing for reminders and for gap-fill habit placement.
internal fun isHabitReminderDueOnDate(
    habit: Habit,
    date: LocalDate
): Boolean {
    val schedule = habit.schedule ?: buildLegacyHabitSchedule(
        habitId = habit.id,
        cadence = habit.cadence,
        windowStartMinute = habit.windowStartMinute,
        windowEndMinute = habit.windowEndMinute,
        plannerVisible = habit.isBundled
    )
    val recurrenceDue = when (val rule = schedule.resolvedRecurrenceRule) {
        is HabitRecurrenceRule.Scheduled -> recurrenceOccursOn(
            date = date,
            recurrence = rule.recurrence,
            startDate = habit.recentEvents.minOfOrNull { it.eventDate } ?: habit.lastCompletedDate ?: date
        )
        is HabitRecurrenceRule.Quota -> true
    }
    val completed = habit.lastCompletedDate == date ||
        habit.recentEvents.any { it.eventDate == date && it.type == HabitEventType.COMPLETED }
    val skipped = schedule.skipDate == date ||
        habit.recentEvents.any { it.eventDate == date && it.type == HabitEventType.SKIPPED }
    val paused = schedule.pausedUntil?.let { !it.isBefore(date) } == true

    return recurrenceDue && !completed && !skipped && !paused
}

private fun recurrenceOccursOn(
    date: LocalDate,
    recurrence: PlannerRecurrence,
    startDate: LocalDate
): Boolean = when (recurrence.type) {
    PlannerRecurrenceType.DAILY,
    PlannerRecurrenceType.MULTIPLE_TIMES_DAILY -> true
    PlannerRecurrenceType.WEEKDAYS -> date.dayOfWeek in weekdaySet
    PlannerRecurrenceType.WEEKENDS -> date.dayOfWeek in weekendSet
    PlannerRecurrenceType.SELECTED_WEEKDAYS ->
        recurrence.weekdays.isEmpty() || date.dayOfWeek in recurrence.weekdays
    PlannerRecurrenceType.EVERY_N_DAYS -> {
        val days = ChronoUnit.DAYS.between(startDate, date)
        days >= 0 && days % recurrence.interval.coerceAtLeast(1) == 0L
    }
    PlannerRecurrenceType.WEEKLY_INTERVAL -> {
        val allowedDays = recurrence.weekdays.ifEmpty { setOf(startDate.dayOfWeek) }
        val weeks = ChronoUnit.WEEKS.between(startDate.weekStart(), date.weekStart())
        weeks >= 0 && weeks % recurrence.interval.coerceAtLeast(1) == 0L &&
            date.dayOfWeek in allowedDays
    }
    PlannerRecurrenceType.PRN -> false
}

private fun LocalDate.weekStart(): LocalDate =
    minusDays((dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

private val weekdaySet = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)

private val weekendSet = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
