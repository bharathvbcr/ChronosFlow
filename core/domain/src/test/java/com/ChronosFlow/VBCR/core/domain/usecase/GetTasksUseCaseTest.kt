package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetTasksUseCaseTest {
    private val taskRepository: TaskRepository = mockk()
    private val useCase = GetTasksUseCase(taskRepository)

    @Test
    fun `returns repository task stream unchanged`() = runTest {
        val tasks = listOf(UseCaseTestFixtures.task(id = "task-1"))
        every { taskRepository.getAllTasks() } returns flowOf(tasks)

        assertEquals(tasks, useCase().first())
    }
}
