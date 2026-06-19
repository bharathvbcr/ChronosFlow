package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyTrends
import com.ChronosFlow.VBCR.core.domain.model.deriveMoodEnergyTrends
import com.ChronosFlow.VBCR.core.domain.repository.MoodEnergyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class ObserveMoodEnergyTrendsUseCase @Inject constructor(
    private val moodEnergyRepository: MoodEnergyRepository
) {
    operator fun invoke(windowDays: Int = 14, today: LocalDate = LocalDate.now()): Flow<MoodEnergyTrends> {
        if (windowDays <= 0) return flowOf(MoodEnergyTrends())
        val start = today.minusDays((windowDays - 1).toLong())
        return moodEnergyRepository.observeForDateRange(start, today)
            .map { checkIns -> deriveMoodEnergyTrends(checkIns) }
    }
}
