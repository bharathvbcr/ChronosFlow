package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.MedicationDailyAdherence
import com.chronosflow.core.domain.model.deriveMedicationAdherenceTrend
import com.chronosflow.core.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reactive medication-adherence trend over the trailing [windowDays]: re-derives whenever a dose
 * event is recorded, so logging a dose reflects on the Insights tab without a manual refresh.
 */
class ObserveMedicationAdherenceTrendUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository
) {
    operator fun invoke(
        windowDays: Int = 14,
        today: LocalDate = LocalDate.now()
    ): Flow<List<MedicationDailyAdherence>> {
        if (windowDays <= 0) return flowOf(emptyList())
        val start = today.minusDays((windowDays - 1).toLong())
        return medicationRepository.observeDoseEventsBetween(start, today)
            .map { events -> deriveMedicationAdherenceTrend(events, windowDays, today) }
    }
}
