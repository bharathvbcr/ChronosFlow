package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmReliability
import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskReminderRule
import com.chronosflow.core.domain.model.TaskReminderTrigger
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SyncRecurringTaskAlarmsUseCase @Inject constructor(
    private val alarmRequestRepository: AlarmRequestRepository,
    private val resolveNextTaskOccurrenceUseCase: ResolveNextTaskOccurrenceUseCase
) {
    suspend operator fun invoke(
        task: Task,
        schedule: TaskSchedule,
        now: Instant = Instant.now()
    ) {
        val desiredRequests = boundedOccurrenceDates(schedule, now)
            .flatMap { occurrenceDate ->
                schedule.reminderRules.mapNotNull { reminderRule ->
                    buildAlarmRequest(
                        task = task,
                        occurrenceDate = occurrenceDate,
                        schedule = schedule,
                        reminderRule = reminderRule,
                        now = now
                    )
                }
            }
        val desiredIds = desiredRequests.map(AlarmRequest::id).toSet()
        val existingRequests = alarmRequestRepository.observeRequestsByType(AlarmRequestType.URGENT_TASK)
            .first()
            .filter { it.id.startsWith("task:${task.id}:") }

        existingRequests
            .filterNot { it.id in desiredIds }
            .forEach { staleRequest ->
                alarmRequestRepository.saveAlarmRequest(
                    staleRequest.copy(
                        deliveryState = AlarmDeliveryState.CANCELLED,
                        updatedAt = now
                    )
                )
            }

        desiredRequests.forEach { request ->
            alarmRequestRepository.saveAlarmRequest(request)
        }
    }

    private fun boundedOccurrenceDates(schedule: TaskSchedule, now: Instant): List<LocalDate> {
        if (schedule.isPaused) return emptyList()
        val firstOccurrence = schedule.nextOccurrenceDate ?: return emptyList()
        val defaultThroughDate = now.atZone(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(DEFAULT_GENERATION_WINDOW_DAYS)
        val explicitThroughDate = schedule.generatedThroughDate ?: defaultThroughDate
        val throughDate = schedule.recurrenceRule.endsOn
            ?.let { minOf(it, explicitThroughDate) }
            ?: explicitThroughDate
        if (firstOccurrence.isAfter(throughDate)) return emptyList()
        return resolveNextTaskOccurrenceUseCase.occurrencesThrough(
            schedule = schedule,
            throughDate = throughDate,
            afterDate = firstOccurrence.minusDays(1),
            maxOccurrences = MAX_GENERATED_ALARM_OCCURRENCES
        )
    }

    private fun buildAlarmRequest(
        task: Task,
        occurrenceDate: LocalDate,
        schedule: TaskSchedule,
        reminderRule: TaskReminderRule,
        now: Instant
    ): AlarmRequest? {
        val scheduledFor = when (reminderRule.trigger) {
            TaskReminderTrigger.AT_TIME -> {
                val minuteOfDay = reminderRule.minuteOfDay ?: schedule.occurrenceMinuteOfDay ?: return null
                occurrenceDate.atTime(minuteOfDay / 60, minuteOfDay % 60)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            }
            TaskReminderTrigger.BEFORE_OCCURRENCE -> {
                val occurrenceMinute = schedule.occurrenceMinuteOfDay ?: return null
                val offsetMinutes = reminderRule.offsetMinutesBefore ?: return null
                occurrenceDate.atTime(occurrenceMinute / 60, occurrenceMinute % 60)
                    .minusMinutes(offsetMinutes.toLong())
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            }
        }
        if (!scheduledFor.isAfter(now)) {
            return null
        }
        return AlarmRequest(
            id = "task:${task.id}:${occurrenceDate}:${reminderRule.id}",
            type = AlarmRequestType.URGENT_TASK,
            scheduledFor = scheduledFor,
            title = task.title,
            message = task.description ?: task.title,
            medicationPlanId = null,
            blockId = task.id,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = now,
            updatedAt = now
        )
    }

    private companion object {
        const val DEFAULT_GENERATION_WINDOW_DAYS = 30L
        const val MAX_GENERATED_ALARM_OCCURRENCES = 32
    }
}
