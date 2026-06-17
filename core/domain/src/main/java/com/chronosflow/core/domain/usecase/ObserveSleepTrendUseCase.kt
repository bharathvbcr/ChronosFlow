package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.SleepTrends
import com.chronosflow.core.domain.model.deriveSleepTrends
import com.chronosflow.core.domain.repository.SleepTrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reactive sleep trend over the trailing [windowDays]: re-derives whenever the underlying tracks
 * change, so a background Health Connect import surfaces on the Insights tab without a manual refresh.
 */
class ObserveSleepTrendUseCase @Inject constructor(
    private val sleepTrackRepository: SleepTrackRepository
) {
    operator fun invoke(
        windowDays: Int = 14,
        today: LocalDate = LocalDate.now()
    ): Flow<SleepTrends> {
        if (windowDays <= 0) return flowOf(SleepTrends())
        val start = today.minusDays((windowDays - 1).toLong())
        return sleepTrackRepository.observeForDateRange(start, today)
            .map { tracks -> deriveSleepTrends(tracks, windowDays, today) }
    }
}
