package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.HabitAnalytics
import com.chronosflow.core.domain.repository.HabitRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveHabitStreaksUseCaseTest {
    private val habitRepository: HabitRepository = mockk()
    private val useCase = ObserveHabitStreaksUseCase(habitRepository)

    @Test
    fun `emits active habit streaks sorted by resolved streak count`() = runTest {
        every { habitRepository.observeHabits() } returns flowOf(
            listOf(
                UseCaseTestFixtures.habit(
                    id = "fallback",
                    title = "Read",
                    streakCount = 3,
                    analytics = HabitAnalytics(currentStreak = 0)
                ),
                UseCaseTestFixtures.habit(
                    id = "inactive",
                    title = "Archived",
                    isActive = false,
                    streakCount = 99
                ),
                UseCaseTestFixtures.habit(
                    id = "analytics",
                    title = "Walk",
                    streakCount = 1,
                    analytics = HabitAnalytics(currentStreak = 5)
                )
            )
        )

        val streaks = useCase().first()

        assertEquals(listOf("analytics", "fallback"), streaks.map { it.habitId })
        assertEquals(listOf(5, 3), streaks.map { it.streakCount })
        assertEquals(listOf("Walk", "Read"), streaks.map { it.title })
    }
}
