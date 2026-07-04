package com.ChronosFlow.VBCR.core.notifications

import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.buildLegacyHabitSchedule
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import java.time.Instant
import java.time.ZoneId
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
        val referenceDate = habit.recurrenceReferenceDate(today)
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

    private fun habitBlockId(habitId: String): String = "habit-$habitId"
}
