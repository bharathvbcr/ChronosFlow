package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.HabitDailyCompletion
import com.chronosflow.core.domain.model.deriveHabitCompletionTrend
import com.chronosflow.core.domain.repository.HabitRepository
import java.time.LocalDate
import javax.inject.Inject

class GetHabitCompletionTrendUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    suspend operator fun invoke(
        windowDays: Int = 14,
        today: LocalDate = LocalDate.now()
    ): List<HabitDailyCompletion> {
        val start = today.minusDays((windowDays - 1).coerceAtLeast(0).toLong())
        val events = habitRepository.getHabitEventsBetween(start, today)
        return deriveHabitCompletionTrend(events, windowDays, today)
    }
}
