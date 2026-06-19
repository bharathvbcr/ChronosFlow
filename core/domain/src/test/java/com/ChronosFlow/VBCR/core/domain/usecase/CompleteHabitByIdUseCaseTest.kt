package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CompleteHabitByIdUseCaseTest {
    private val habitRepository: HabitRepository = mockk()
    private val completeHabitUseCase: CompleteHabitUseCase = mockk()
    private val useCase = CompleteHabitByIdUseCase(habitRepository, completeHabitUseCase)

    @Test
    fun `loads habit before completing by id`() = runTest {
        val habit = UseCaseTestFixtures.habit(id = "habit-1", title = "Morning walk")
        coEvery { habitRepository.getHabitById("habit-1") } returns habit
        coEvery { completeHabitUseCase(habit, UseCaseTestFixtures.date) } returns Unit

        useCase("habit-1", UseCaseTestFixtures.date)

        coVerify(exactly = 1) { completeHabitUseCase(habit, UseCaseTestFixtures.date) }
    }

    @Test
    fun `missing habit is a no-op`() = runTest {
        coEvery { habitRepository.getHabitById("missing") } returns null

        useCase("missing", UseCaseTestFixtures.date)

        coVerify(exactly = 0) { completeHabitUseCase(any(), any()) }
    }
}
