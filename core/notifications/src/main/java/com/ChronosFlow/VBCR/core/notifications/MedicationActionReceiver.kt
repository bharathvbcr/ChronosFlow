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
    lateinit var alarmScheduler: AlarmScheduler

    @Inject
    lateinit var alarmDeliveryCoordinator: AlarmDeliveryCoordinator

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
                            val snoozedFor = now.plusSeconds(15 * 60L) // Snooze for 15 minutes
                            val request = AlarmRequest(
                                id = UUID.randomUUID().toString(),
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
                            alarmScheduler.scheduleAlarmRequest(request)
                            medicationRepository.addMedicationDoseEvent(
                                MedicationDoseEvent(
                                    id = UUID.randomUUID().toString(),
                                    medicationPlanId = plan.id,
                                    type = MedicationDoseEventType.SNOOZED,
                                    eventDate = LocalDate.now(),
                                    recordedAt = Instant.now(),
                                    scheduledMinuteOfDay = plan.reminderMinuteOfDay,
                                    reason = "Snoozed by 15 minutes from notification",
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

                // Cancel the notification
                if (notificationId != -1) {
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
