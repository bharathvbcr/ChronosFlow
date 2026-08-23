package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MedicationActionReceiver"

@AndroidEntryPoint
class MedicationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var medicationRepository: MedicationRepository

    @Inject
    lateinit var alarmRequestRepository: AlarmRequestRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    @Inject
    lateinit var alarmDeliveryCoordinator: AlarmDeliveryCoordinator

    @Inject
    lateinit var currentBlockNotificationCoordinator: CurrentBlockNotificationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val medicationPlanId = intent.getStringExtra(EXTRA_MEDICATION_PLAN_ID) ?: return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val plan = medicationRepository.getMedicationPlanById(medicationPlanId)
                if (plan != null) {
                    when (action) {
                        ACTION_TAKE -> {
                            medicationRepository.addMedicationDoseEvent(
                                MedicationDoseEvent(
                                    id = UUID.randomUUID().toString(),
                                    medicationPlanId = plan.id,
                                    type = MedicationDoseEventType.TAKEN,
                                    eventDate = LocalDate.now(),
                                    recordedAt = Instant.now(),
                                    scheduledMinuteOfDay = plan.reminderMinuteOfDay,
                                    reason = "Recorded from notification action",
                                    doseAmount = plan.dosage
                                )
                            )
                            val remaining = plan.safetyProfile?.supplyRemaining?.let { (it - 1).coerceAtLeast(0) }
                            val updatedPlan = plan.copy(
                                safetyProfile = plan.safetyProfile?.copy(supplyRemaining = remaining)
                            )
                            medicationRepository.saveMedicationPlan(updatedPlan)

                            // Trigger low supply warning if remaining drops below/equals the refill threshold
                            val safetyProfile = plan.safetyProfile
                            val threshold = safetyProfile?.refillThreshold
                            if (remaining != null && threshold != null && remaining <= threshold) {
                                alarmDeliveryCoordinator.deliverLowSupplyWarning(updatedPlan, remaining)
                            }
                        }
                        ACTION_SNOOZE -> {
                            val now = Instant.now()
                            val snoozedFor = now.plusSeconds(MEDICATION_SNOOZE_MINUTES * 60)
                            val request = AlarmRequest(
                                id = "med-snooze-${plan.id}-${now.toEpochMilli()}",
                                type = AlarmRequestType.MEDICATION,
                                scheduledFor = snoozedFor,
                                title = plan.name,
                                message = "Snoozed dose · ${plan.dosage} ${plan.unit}",
                                medicationPlanId = plan.id,
                                blockId = null,
                                reliability = AlarmReliability.EXACT,
                                deliveryState = AlarmDeliveryState.PENDING,
                                createdAt = now,
                                updatedAt = now
                            )
                            // Persist the row BEFORE scheduling so boot restore can reconcile this
                            // reminder even if the process dies between the two steps; then update
                            // it with the schedule outcome. respectFoldSkip=false: folding dedupes
                            // imminent reminders against the live chip — a snooze the user just
                            // requested is an explicit future wake and must never be skipped.
                            alarmRequestRepository.saveAlarmRequest(request)
                            val result = alarmScheduler.scheduleAlarmRequest(request, respectFoldSkip = false)
                            val (reliability, failureReason) = when (result) {
                                is AlarmScheduleResult.Scheduled ->
                                    if (result.exact) {
                                        AlarmReliability.EXACT to null
                                    } else {
                                        AlarmReliability.DEGRADED_WINDOW to "Exact alarms unavailable"
                                    }
                                is AlarmScheduleResult.ExactDenied ->
                                    AlarmReliability.BLOCKED to "Exact alarm permission denied"
                                is AlarmScheduleResult.PermissionDenied ->
                                    AlarmReliability.BLOCKED to "Notification permission denied"
                                is AlarmScheduleResult.Skipped ->
                                    AlarmReliability.INEXACT to result.reason
                            }
                            alarmRequestRepository.saveAlarmRequest(
                                request.copy(
                                    reliability = reliability,
                                    failureReason = failureReason,
                                    updatedAt = Instant.now()
                                )
                            )
                            medicationRepository.addMedicationDoseEvent(
                                MedicationDoseEvent(
                                    id = UUID.randomUUID().toString(),
                                    medicationPlanId = plan.id,
                                    type = MedicationDoseEventType.SNOOZED,
                                    eventDate = LocalDate.now(),
                                    recordedAt = Instant.now(),
                                    scheduledMinuteOfDay = plan.reminderMinuteOfDay,
                                    reason = "Snoozed by $MEDICATION_SNOOZE_MINUTES minutes from notification",
                                    doseAmount = null
                                )
                            )
                        }
                        ACTION_SKIP -> {
                            medicationRepository.addMedicationDoseEvent(
                                MedicationDoseEvent(
                                    id = UUID.randomUUID().toString(),
                                    medicationPlanId = plan.id,
                                    type = MedicationDoseEventType.SKIPPED,
                                    eventDate = LocalDate.now(),
                                    recordedAt = Instant.now(),
                                    scheduledMinuteOfDay = plan.reminderMinuteOfDay,
                                    reason = "Skipped from notification action",
                                    doseAmount = null
                                )
                            )
                        }
                    }
                }

                if (intent.getBooleanExtra(EXTRA_REFRESH_CURRENT_BLOCK, false)) {
                    currentBlockNotificationCoordinator.refresh()
                } else if (notificationId != -1) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)
                    ReminderNotificationGroups.refreshSummary(context)
                }

                // If snooze, take, or skip was completed, we can also clear the original alarm request state
                if (requestId != null) {
                    alarmScheduler.cancelAlarm(requestId)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Action failed: ${ex.message}", ex)
            } finally {
                withContext(Dispatchers.Main) {
                    pendingResult.finish()
                }
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_TAKE = "com.ChronosFlow.VBCR.core.notifications.ACTION_TAKE_MEDICATION"
        const val ACTION_SNOOZE = "com.ChronosFlow.VBCR.core.notifications.ACTION_SNOOZE_MEDICATION"
        const val ACTION_SKIP = "com.ChronosFlow.VBCR.core.notifications.ACTION_SKIP_MEDICATION"

        const val EXTRA_MEDICATION_PLAN_ID = "EXTRA_MEDICATION_PLAN_ID"
        const val EXTRA_REQUEST_ID = "EXTRA_REQUEST_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }
}
