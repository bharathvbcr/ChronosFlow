package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.repository.MedicationRepository
import javax.inject.Inject

class RecordMedicationWidgetActionUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository
) {
    suspend operator fun invoke(planId: String, taken: Boolean) {
        if (taken) return
        val plan = medicationRepository.getMedicationPlanById(planId) ?: return
        medicationRepository.saveMedicationPlan(plan.copy(missedCount = plan.missedCount + 1))
    }
}
