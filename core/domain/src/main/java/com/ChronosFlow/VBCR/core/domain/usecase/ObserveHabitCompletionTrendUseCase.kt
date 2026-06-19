package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.HabitDailyCompletion
import com.ChronosFlow.VBCR.core.domain.model.deriveHabitCompletionTrend
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reactive habit-completion trend over the trailing [windowDays]: re-derives whenever a habit event
 * is recorded, so completing a habit reflects on the Insights tab without a manual refresh.
 */
class ObserveHabitCompletionTrendUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    operator fun invoke(
        windowDays: Int = 14,
        today: LocalDate = LocalDate.now()
    ): Flow<List<HabitDailyCompletion>> {
        if (windowDays <= 0) return flowOf(emptyList())
        val start = today.minusDays((windowDays - 1).toLong())
        return habitRepository.observeHabitEventsBetween(start, today)
            .map { events -> deriveHabitCompletionTrend(events, windowDays, today) }
    }
}
