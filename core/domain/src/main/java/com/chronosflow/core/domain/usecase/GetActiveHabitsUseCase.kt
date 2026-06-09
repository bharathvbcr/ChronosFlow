package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.repository.HabitRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetActiveHabitsUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    operator fun invoke(): Flow<List<Habit>> {
        return habitRepository.observeHabits().map { habits ->
            habits.filter { it.isActive }
        }
    }
}
