package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetChronosWidgetSummaryUseCaseTest {
    private val habitRepository: HabitRepository = mockk()
    private val medicationRepository: MedicationRepository = mockk()
    private val useCase = GetChronosWidgetSummaryUseCase(habitRepository, medicationRepository)

    @Test
    fun `selects first active habit and medication for widget`() = runTest {
        every { habitRepository.observeHabits() } returns flowOf(
            listOf(
                UseCaseTestFixtures.habit(id = "inactive-habit", title = "Archived", isActive = false),
                UseCaseTestFixtures.habit(id = "habit-1", title = "Morning walk", isActive = true)
            )
        )
        every { medicationRepository.observeMedicationPlans() } returns flowOf(
            listOf(
                UseCaseTestFixtures.medicationPlan(
                    id = "inactive-medication",
                    name = "Archived med",
                    isActive = false
                ),
                UseCaseTestFixtures.medicationPlan(
                    id = "medication-1",
                    name = "Vitamin D",
                    isActive = true
                )
            )
        )

        val summary = useCase()

        assertEquals("habit-1", summary.habitId)
        assertEquals("Morning walk", summary.habitTitle)
        assertEquals("medication-1", summary.medicationId)
        assertEquals("Vitamin D", summary.medicationName)
    }
}
