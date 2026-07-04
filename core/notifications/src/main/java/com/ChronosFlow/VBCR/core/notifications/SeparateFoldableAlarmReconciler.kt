package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Drops separate med/task/habit OS alarms when fold-into-live mode turns on. Skip-at-schedule
 * prevents new foldable alarms; this clears ones already registered before the toggle.
 */
@Singleton
class SeparateFoldableAlarmReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmScheduler: AlarmScheduler,
    private val alarmRequestRepository: AlarmRequestRepository,
) {
    suspend fun cancelWhenFoldModeActive(): Int {
        if (!ReminderFoldScheduling.skipsSeparateRemindersWhenFolded(context)) return 0
        var cancelled = cancelPersistedFoldableAlarms()
        cancelled += cancelRepositoryFoldableAlarms()
        return cancelled
    }

    private fun cancelPersistedFoldableAlarms(): Int {
        var cancelled = 0
        alarmScheduler.getPersistedAlarmIds().forEach { id ->
            if (ReminderFoldScheduling.isSeparateFoldableReminderAlarmId(id)) {
                alarmScheduler.cancelAlarm(id)
                cancelled++
            }
        }
        return cancelled
    }

    private suspend fun cancelRepositoryFoldableAlarms(): Int {
        val now = Instant.now()
        var cancelled = 0
        listOf(
            AlarmRequestType.MEDICATION,
            AlarmRequestType.URGENT_TASK,
            AlarmRequestType.BLOCK_START,
        ).forEach { type ->
            alarmRequestRepository.observeRequestsByType(type).first()
                .filter { request ->
                    request.deliveryState != AlarmDeliveryState.CANCELLED &&
                        request.deliveryState != AlarmDeliveryState.DELIVERED &&
                        ReminderFoldScheduling.isSeparateFoldableReminder(request)
                }
                .forEach { request ->
                    alarmScheduler.cancelAlarm(request.id)
                    alarmRequestRepository.saveAlarmRequest(
                        request.copy(
                            deliveryState = AlarmDeliveryState.CANCELLED,
                            updatedAt = now,
                            failureReason = FOLDED_REMINDER_SKIP_REASON,
                        )
                    )
                    cancelled++
                }
        }
        return cancelled
    }
}
