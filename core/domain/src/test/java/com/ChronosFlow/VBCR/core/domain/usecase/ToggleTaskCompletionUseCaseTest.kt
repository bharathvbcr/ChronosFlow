package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToggleTaskCompletionUseCaseTest {

    private val taskRepository: TaskRepository = mockk()
    private val taskScheduleRepository: TaskScheduleRepository = mockk()
    private val completeTaskOccurrenceUseCase: CompleteTaskOccurrenceUseCase = mockk()

    private val useCase = ToggleTaskCompletionUseCase(
        repository = taskRepository,
        taskScheduleRepository = taskScheduleRepository,
        completeTaskOccurrenceUseCase = completeTaskOccurrenceUseCase
    )

    @Test
    fun `toggles one-off task completion`() = runTest {
        val task = task(isCompleted = false)
        coEvery { taskRepository.getTaskById("task-1") } returns task
        coEvery { taskScheduleRepository.getTaskSchedule("task-1") } returns null
        coEvery { taskRepository.saveTask(any()) } returns Unit

        useCase("task-1")

        coVerify(exactly = 0) { completeTaskOccurrenceUseCase(any(), any(), any()) }
        coVerify {
            taskRepository.saveTask(
                match {
                    it.id == "task-1" && it.isCompleted
                }
            )
        }
    }

    @Test
    fun `completes recurring occurrence without marking task done when more remain`() = runTest {
        val task = task(isCompleted = false)
        val initialSchedule = schedule(nextOccurrenceDate = LocalDate.of(2026, 5, 26))
        val advancedSchedule = initialSchedule.copy(nextOccurrenceDate = LocalDate.of(2026, 5, 29))
        coEvery { taskRepository.getTaskById("task-1") } returns task
        coEvery { taskScheduleRepository.getTaskSchedule("task-1") } returns initialSchedule
        coEvery { completeTaskOccurrenceUseCase("task-1", LocalDate.of(2026, 5, 26), any()) } returns advancedSchedule
        coEvery { taskRepository.saveTask(any()) } returns Unit

        useCase("task-1")

        coVerify { completeTaskOccurrenceUseCase("task-1", LocalDate.of(2026, 5, 26), any()) }
        coVerify {
            taskRepository.saveTask(
                match {
                    it.id == "task-1" && !it.isCompleted
                }
            )
        }
    }

    @Test
    fun `marks recurring task completed when final occurrence is consumed`() = runTest {
        val task = task(isCompleted = false)
        val initialSchedule = schedule(nextOccurrenceDate = LocalDate.of(2026, 5, 26))
        coEvery { taskRepository.getTaskById("task-1") } returns task
        coEvery { taskScheduleRepository.getTaskSchedule("task-1") } returns initialSchedule
        coEvery { completeTaskOccurrenceUseCase("task-1", LocalDate.of(2026, 5, 26), any()) } returns initialSchedule.copy(nextOccurrenceDate = null)
        coEvery { taskRepository.saveTask(any()) } returns Unit

        useCase("task-1")

        coVerify {
            taskRepository.saveTask(
                match {
                    it.id == "task-1" && it.isCompleted
                }
            )
        }
    }

    private fun task(isCompleted: Boolean): Task = Task(
        id = "task-1",
        title = "Write proposal",
        description = null,
        isCompleted = isCompleted,
        priority = 1,
        dueDate = null,
        createdAt = Instant.parse("2026-05-25T12:00:00Z"),
        updatedAt = Instant.parse("2026-05-25T12:00:00Z")
    )

    private fun schedule(nextOccurrenceDate: LocalDate?): TaskSchedule = TaskSchedule(
        id = "schedule-1",
        taskId = "task-1",
        recurrenceRule = TaskRecurrenceRule.Daily(
            intervalDays = 1,
            startsOn = LocalDate.of(2026, 5, 25)
        ),
        nextOccurrenceDate = nextOccurrenceDate,
        createdAt = Instant.parse("2026-05-25T12:00:00Z"),
        updatedAt = Instant.parse("2026-05-25T12:00:00Z")
    )
}
