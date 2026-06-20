package com.ChronosFlow.VBCR.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.ComponentName
import androidx.core.content.ContextCompat
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var alarmRequestRepository: AlarmRequestRepository
    @Inject lateinit var focusSessionRepository: FocusSessionRepository
    @Inject lateinit var currentBlockNotificationCoordinator: CurrentBlockNotificationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (!isReminderRestoreAction(action)) {
            return
        }

        Log.i("ReminderBootReceiver", "$action received; rehydrating persisted reminders.")
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // LOCKED_BOOT_COMPLETED fires before the user unlocks the device. Room and
                // SQLCipher live in Credential Encrypted (CE) storage which is unavailable until
                // the first unlock, so skip ALL DB-backed work here — it runs again on the
                // subsequent ACTION_BOOT_COMPLETED once CE storage is unlocked.
                if (action == ACTION_LOCKED_BOOT_COMPLETED) return@launch

                try {
                    val recoverableState = focusSessionRepository.observeRecoverableSession().first()
                    if (recoverableState != null) {
                        val (sessionId, blockId) = when (recoverableState) {
                            is FocusSessionState.Running -> recoverableState.sessionId to recoverableState.blockId
                            is FocusSessionState.Paused -> recoverableState.sessionId to recoverableState.blockId
                            is FocusSessionState.ServiceKilledRecoverable -> recoverableState.sessionId to null
                            else -> null to null
                        }
                        if (sessionId != null &&
                            (action == ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED)
                        ) {
                            val serviceIntent = Intent().apply {
                                component = ComponentName(context, "com.ChronosFlow.VBCR.feature.focus.FocusService")
                                setAction("com.ChronosFlow.VBCR.feature.focus.SYNC")
                                putExtra("session_id", sessionId)
                                putExtra("block_id", blockId)
                            }
                            ContextCompat.startForegroundService(context, serviceIntent)
                            Log.i("ReminderBootReceiver", "Recoverable focus session $sessionId found; restarted FocusService.")
                        }
                    }
                } catch (ex: Exception) {
                    Log.w("ReminderBootReceiver", "Failed to restore focus session on boot: ${ex.message}", ex)
                }

                val results = restoreDatabaseBackedAlarms()
                val legacyResults = alarmScheduler.restoreScheduledAlarmsAfterReboot(skipIds = results.keys)
                ReminderReconcileScheduler.enqueue(context.applicationContext)
                try {
                    currentBlockNotificationCoordinator.refresh()
                } catch (ex: Exception) {
                    Log.w("ReminderBootReceiver", "Failed to refresh current-block notification", ex)
                }
                Log.i(
                    "ReminderBootReceiver",
                    "Reminder restore complete: ${results.size} DB result(s), ${legacyResults.size} legacy result(s)."
                )
            } catch (ex: Exception) {
                Log.w("ReminderBootReceiver", "Failed to restore reminders after boot: ${ex.message}", ex)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private suspend fun restoreDatabaseBackedAlarms(): Map<String, AlarmScheduleResult> {
        val now = Instant.now()
        val requests = alarmRequestRepository.observePendingRequests(now).first()
        val results = linkedMapOf<String, AlarmScheduleResult>()
        requests.forEach { request ->
            val result = alarmScheduler.scheduleAlarmRequest(request)
            val restoredState = when (result) {
                is AlarmScheduleResult.Scheduled -> if (result.exact) {
                    AlarmDeliveryState.SCHEDULED
                } else {
                    AlarmDeliveryState.DEGRADED
                }
                is AlarmScheduleResult.ExactDenied,
                is AlarmScheduleResult.PermissionDenied -> AlarmDeliveryState.FAILED
                is AlarmScheduleResult.Skipped -> AlarmDeliveryState.FAILED
            }
            alarmRequestRepository.saveAlarmRequest(
                request.copy(
                    reliability = if (result is AlarmScheduleResult.Scheduled && result.exact) {
                        AlarmReliability.EXACT
                    } else if (result is AlarmScheduleResult.Scheduled) {
                        AlarmReliability.DEGRADED_WINDOW
                    } else {
                        AlarmReliability.BLOCKED
                    },
                    deliveryState = restoredState,
                    updatedAt = now,
                    failureReason = when (result) {
                        is AlarmScheduleResult.Skipped -> result.reason
                        is AlarmScheduleResult.ExactDenied -> "Exact alarm permission denied during restore"
                        is AlarmScheduleResult.PermissionDenied -> "Notification permission denied during restore"
                        is AlarmScheduleResult.Scheduled -> null
                    }
                )
            )
            results[request.id] = result
        }
        return results
    }

    private companion object
}

internal const val ACTION_LOCKED_BOOT_COMPLETED = "android.intent.action.LOCKED_BOOT_COMPLETED"
internal const val ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

internal fun isReminderRestoreAction(action: String?): Boolean {
    return action in setOf(
        ACTION_BOOT_COMPLETED,
        ACTION_LOCKED_BOOT_COMPLETED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
    )
}
