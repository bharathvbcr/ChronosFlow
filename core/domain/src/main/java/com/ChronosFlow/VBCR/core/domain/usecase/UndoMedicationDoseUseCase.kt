package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reverses a just-taken dose — the wrist/widget "undo" for an accidental acknowledgement — by
 * DELETING the most recent TAKEN event recorded for the plan on [today]. This is the correct undo:
 * recording a MISSED event instead ([RecordMedicationWidgetActionUseCase] with `taken = false`)
 * would wrongly leave the TAKEN event in the adherence history AND bump the missed counter, so the
 * event is removed outright and no counter is touched.
 *
 * A no-op when no TAKEN event exists for the plan today, so a stale mirror can't corrupt history.
 */
class UndoMedicationDoseUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository
) {
    suspend operator fun invoke(planId: String, today: LocalDate = LocalDate.now()) {
        val latestTaken = medicationRepository.getDoseEventsForPlan(planId)
            .filter { it.type == MedicationDoseEventType.TAKEN && it.eventDate == today }
            .maxByOrNull { it.recordedAt }
            ?: return
        medicationRepository.deleteMedicationDoseEvent(latestTaken.id)
    }
}
