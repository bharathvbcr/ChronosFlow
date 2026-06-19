package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.AppUsageDay
import com.ChronosFlow.VBCR.core.domain.repository.AppUsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reactive screen-time trend over the trailing [windowDays], one [AppUsageDay] per calendar day
 * (days with no stored usage fill in as zero so the Insights chart has a continuous series).
 * Re-emits whenever the stored aggregates change, so a sync surfaces without a manual refresh.
 */
class ObserveAppUsageTrendUseCase @Inject constructor(
    private val appUsageRepository: AppUsageRepository
) {
    operator fun invoke(
        windowDays: Int = 14,
        today: LocalDate = LocalDate.now()
    ): Flow<List<AppUsageDay>> {
        if (windowDays <= 0) return flowOf(emptyList())
        val start = today.minusDays((windowDays - 1).toLong())
        return appUsageRepository.observeForDateRange(start, today).map { rows ->
            val byDate = rows.associateBy { it.date }
            (0 until windowDays).map { offset ->
                val date = start.plusDays(offset.toLong())
                byDate[date] ?: AppUsageDay(date, productiveMinutes = 0, distractingMinutes = 0, neutralMinutes = 0)
            }
        }
    }
}
