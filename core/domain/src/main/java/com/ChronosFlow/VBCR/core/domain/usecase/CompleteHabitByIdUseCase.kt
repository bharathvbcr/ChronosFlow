package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import java.time.LocalDate
import javax.inject.Inject

class CompleteHabitByIdUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val completeHabitUseCase: CompleteHabitUseCase
) {
    suspend operator fun invoke(habitId: String, date: LocalDate) {
        val habit = habitRepository.getHabitById(habitId) ?: return
        completeHabitUseCase(habit, date)
    }
}
