package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Records a dose acknowledgement coming from a home-screen widget, matching the semantics of
 * the in-app markDoseTaken / markDoseMissed actions: both outcomes write a dose event so the
 * adherence history stays complete, and a miss also bumps the plan's missed counter.
 */
class RecordMedicationWidgetActionUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository
) {
    suspend operator fun invoke(
        planId: String,
        taken: Boolean,
        today: LocalDate = LocalDate.now(),
        now: Instant = Instant.now()
    ) {
        val plan = medicationRepository.getMedicationPlanById(planId) ?: return
        medicationRepository.addMedicationDoseEvent(
            MedicationDoseEvent(
                id = UUID.randomUUID().toString(),
                medicationPlanId = plan.id,
                type = if (taken) MedicationDoseEventType.TAKEN else MedicationDoseEventType.MISSED,
                eventDate = today,
                recordedAt = now,
                scheduledMinuteOfDay = plan.reminderMinuteOfDay,
                reason = if (taken) null else "Marked missed from widget",
                doseAmount = if (taken) plan.dosage else null
            )
        )
        if (!taken) {
            medicationRepository.saveMedicationPlan(plan.copy(missedCount = plan.missedCount + 1))
        }
    }
}
