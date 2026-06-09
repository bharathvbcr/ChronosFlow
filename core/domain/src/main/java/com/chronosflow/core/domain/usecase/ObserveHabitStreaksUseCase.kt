package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.repository.HabitRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HabitStreak(
    val habitId: String,
    val title: String,
    val streakCount: Int,
    val lastCompletedDate: LocalDate?
)

class ObserveHabitStreaksUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    operator fun invoke(): Flow<List<HabitStreak>> {
        return habitRepository.observeHabits().map { habits ->
            habits
                .filter { it.isActive }
                .map {
                    HabitStreak(
                        habitId = it.id,
                        title = it.title,
                        streakCount = it.analytics.currentStreak.takeIf { streak -> streak > 0 } ?: it.streakCount,
                        lastCompletedDate = it.lastCompletedDate
                    )
                }
                .sortedByDescending { it.streakCount }
        }
    }
}
