package com.chronosflow.core.notifications

import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmReliability
import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.HabitEventType
import com.chronosflow.core.domain.model.HabitRecurrenceRule
import com.chronosflow.core.domain.model.PlannerRecurrence
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import com.chronosflow.core.domain.model.buildLegacyHabitSchedule
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class HabitReminderScheduler @Inject constructor(
    private val alarmScheduler: AlarmScheduler,
    private val alarmRequestRepository: AlarmRequestRepository
) {
    suspend fun syncUpcomingHabitReminders(
        habits: List<Habit>,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        var scheduled = 0
        habits.forEach { habit ->
            if (syncUpcomingHabitReminder(habit, now, zoneId) is AlarmScheduleResult.Scheduled) {
                scheduled++
            }
        }
        return scheduled
    }

    suspend fun syncUpcomingHabitReminder(
        habit: Habit,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AlarmScheduleResult? {
        cancelUpcomingHabitReminders(habit.id, now)
        if (!habit.isActive) return null
        val occurrence = nextHabitOccurrence(habit, now, zoneId) ?: return null
        val request = habitReminderRequest(habit, occurrence, now, zoneId)
        val result = alarmScheduler.scheduleInexactAlarm(
            id = request.id,
            time = request.scheduledFor,
            title = request.title,
            message = request.message
        )
        alarmRequestRepository.saveAlarmRequest(request.withScheduleResult(result, now))
        return result
    }

    suspend fun cancelUpcomingHabitReminders(
        habitId: String,
        now: Instant = Instant.now()
    ) {
        val blockId = habitBlockId(habitId)
        alarmRequestRepository.observeRequestsByType(AlarmRequestType.BLOCK_START)
            .first()
            .filter { request ->
                request.deliveryState != AlarmDeliveryState.CANCELLED &&
                    request.deliveryState != AlarmDeliveryState.DELIVERED &&
                    (request.blockId == blockId || request.id.contains(":$blockId:"))
            }
            .forEach { request ->
                alarmScheduler.cancelAlarm(request.id)
                alarmRequestRepository.saveAlarmRequest(
                    request.copy(
                        deliveryState = AlarmDeliveryState.CANCELLED,
                        updatedAt = now,
                        failureReason = "Cancelled by habit reminder resync"
                    )
                )
            }
    }

    private fun nextHabitOccurrence(
        habit: Habit,
        now: Instant,
        zoneId: ZoneId
    ): Instant? {
        val localNow = now.atZone(zoneId)
        val today = localNow.toLocalDate()
        val referenceDate = habit.recentEvents.minOfOrNull { it.eventDate } ?: habit.lastCompletedDate ?: today
        repeat(MAX_HABIT_LOOKAHEAD_DAYS) { offset ->
            val date = today.plusDays(offset.toLong())
            if (habit.isReminderDueOnDate(date, referenceDate)) {
                val schedule = habit.schedule ?: buildLegacyHabitSchedule(
                    habitId = habit.id,
                    cadence = habit.cadence,
                    windowStartMinute = habit.windowStartMinute,
                    windowEndMinute = habit.windowEndMinute,
                    plannerVisible = habit.isBundled
                )
                val minute = (schedule.deferUntilMinuteOfDay ?: schedule.targetStartMinute).coerceIn(0, 1439)
                val candidate = date.atStartOfDay(zoneId).plusMinutes(minute.toLong()).toInstant()
                if (candidate.isAfter(now)) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun habitReminderRequest(
        habit: Habit,
        scheduledFor: Instant,
        now: Instant,
        zoneId: ZoneId
    ): AlarmRequest {
        val date = scheduledFor.atZone(zoneId).toLocalDate()
        val title = habit.title.ifBlank { "Habit reminder" }
        return AlarmRequest(
            id = "daydial:$date:${habitBlockId(habit.id)}:start",
            type = AlarmRequestType.BLOCK_START,
            scheduledFor = scheduledFor,
            title = title,
            message = "${habit.title.ifBlank { "Habit" }} starts now",
            medicationPlanId = null,
            blockId = habitBlockId(habit.id),
            reliability = AlarmReliability.INEXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun Habit.isReminderDueOnDate(
        date: LocalDate,
        referenceDate: LocalDate
    ): Boolean {
        val schedule = this.schedule ?: buildLegacyHabitSchedule(
            habitId = id,
            cadence = cadence,
            windowStartMinute = windowStartMinute,
            windowEndMinute = windowEndMinute,
            plannerVisible = isBundled
        )
        val recurrenceDue = when (val rule = schedule.resolvedRecurrenceRule) {
            is HabitRecurrenceRule.Scheduled -> recurrenceOccursOn(
                date = date,
                recurrence = rule.recurrence,
                startDate = referenceDate
            )
            is HabitRecurrenceRule.Quota -> true
        }
        val completed = lastCompletedDate == date ||
            recentEvents.any { it.eventDate == date && it.type == HabitEventType.COMPLETED }
        val skipped = schedule.skipDate == date ||
            recentEvents.any { it.eventDate == date && it.type == HabitEventType.SKIPPED }
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

    private fun AlarmRequest.withScheduleResult(
        result: AlarmScheduleResult,
        now: Instant
    ): AlarmRequest {
        val (reliability, deliveryState, failureReason) = when (result) {
            is AlarmScheduleResult.Scheduled -> Triple(AlarmReliability.INEXACT, AlarmDeliveryState.SCHEDULED, null)
            is AlarmScheduleResult.ExactDenied -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                "Exact alarm permission denied"
            )
            is AlarmScheduleResult.PermissionDenied -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                "Notification permission denied"
            )
            is AlarmScheduleResult.Skipped -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                result.reason
            )
        }
        return copy(
            reliability = reliability,
            deliveryState = deliveryState,
            updatedAt = now,
            failureReason = failureReason
        )
    }

    private fun LocalDate.weekStart(): LocalDate =
        minusDays((dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    private fun habitBlockId(habitId: String): String = "habit-$habitId"
}

private const val MAX_HABIT_LOOKAHEAD_DAYS = 370

private val weekdaySet = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)

private val weekendSet = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
