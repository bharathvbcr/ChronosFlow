package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.ChronosWidgetSummary
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GetChronosWidgetSummaryUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val medicationRepository: MedicationRepository
) {
    suspend operator fun invoke(): ChronosWidgetSummary {
        val habit = habitRepository.observeHabits().first().firstOrNull { it.isActive }
        val medication = medicationRepository.observeMedicationPlans().first().firstOrNull { it.isActive }
        return ChronosWidgetSummary(
            habitId = habit?.id,
            habitTitle = habit?.title,
            medicationId = medication?.id,
            medicationName = medication?.name
        )
    }
}
