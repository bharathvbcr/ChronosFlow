package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.MedicationDailyAdherence
import com.chronosflow.core.domain.model.deriveMedicationAdherenceTrend
import com.chronosflow.core.domain.repository.MedicationRepository
import java.time.LocalDate
import javax.inject.Inject

class GetMedicationAdherenceTrendUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository
) {
    suspend operator fun invoke(
        windowDays: Int = 14,
        today: LocalDate = LocalDate.now()
    ): List<MedicationDailyAdherence> {
        val start = today.minusDays((windowDays - 1).coerceAtLeast(0).toLong())
        val events = medicationRepository.getDoseEventsBetween(start, today)
        return deriveMedicationAdherenceTrend(events, windowDays, today)
    }
}
