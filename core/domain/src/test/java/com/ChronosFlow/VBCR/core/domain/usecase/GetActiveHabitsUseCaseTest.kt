package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetActiveHabitsUseCaseTest {
    private val habitRepository: HabitRepository = mockk()
    private val useCase = GetActiveHabitsUseCase(habitRepository)

    @Test
    fun `filters inactive habits from repository stream`() = runTest {
        every { habitRepository.observeHabits() } returns flowOf(
            listOf(
                UseCaseTestFixtures.habit(id = "active", title = "Walk", isActive = true),
                UseCaseTestFixtures.habit(id = "inactive", title = "Archive", isActive = false)
            )
        )

        val active = useCase().first()

        assertEquals(listOf("active"), active.map { it.id })
    }
}
