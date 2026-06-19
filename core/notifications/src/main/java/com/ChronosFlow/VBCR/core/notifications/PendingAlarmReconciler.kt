package com.ChronosFlow.VBCR.core.notifications

import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class AlarmReconcileResult(
    val prunedLegacyCount: Int,
    val scheduledCount: Int,
    val skippedCount: Int,
    val failedCount: Int
)

@Singleton
class PendingAlarmReconciler @Inject constructor(
    private val alarmScheduler: AlarmScheduler,
    private val alarmRequestRepository: AlarmRequestRepository,
    private val habitRepository: HabitRepository,
    private val habitReminderScheduler: HabitReminderScheduler
) {
    suspend fun reconcile(now: Instant = Instant.now()): AlarmReconcileResult {
        val prunedLegacyCount = alarmScheduler.pruneExpiredPersistedReminders()

        // Keep database lean by deleting historical alarm requests older than 7 days
        val threshold = now.minus(java.time.Duration.ofDays(7))
        alarmRequestRepository.pruneExpiredAlarmRequests(threshold)

        val pendingRequests = alarmRequestRepository.observePendingRequests(now).first()
        var scheduledCount = 0
        var skippedCount = 0
        var failedCount = 0

        pendingRequests.forEach { request ->
            val result = alarmScheduler.scheduleAlarmRequest(request)
            alarmRequestRepository.saveAlarmRequest(request.withScheduleResult(result, now))
            when (result) {
                is AlarmScheduleResult.Scheduled -> scheduledCount++
                is AlarmScheduleResult.Skipped -> skippedCount++
                is AlarmScheduleResult.ExactDenied,
                is AlarmScheduleResult.PermissionDenied -> failedCount++
            }
        }
        habitReminderScheduler.syncUpcomingHabitReminders(habitRepository.observeHabits().first(), now)

        return AlarmReconcileResult(
            prunedLegacyCount = prunedLegacyCount,
            scheduledCount = scheduledCount,
            skippedCount = skippedCount,
            failedCount = failedCount
        )
    }

    private fun AlarmRequest.withScheduleResult(
        result: AlarmScheduleResult,
        now: Instant
    ): AlarmRequest {
        val (reliability, deliveryState, failureReason) = when (result) {
            is AlarmScheduleResult.Scheduled -> if (result.exact) {
                Triple(AlarmReliability.EXACT, AlarmDeliveryState.SCHEDULED, null)
            } else {
                Triple(
                    AlarmReliability.DEGRADED_WINDOW,
                    AlarmDeliveryState.DEGRADED,
                    "Exact alarm permission unavailable; scheduled with fallback window"
                )
            }
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
}
