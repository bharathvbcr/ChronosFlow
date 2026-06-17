package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TaskScheduleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AdvanceRecurringTaskOccurrenceUseCaseTest {
    private val occurrenceDate = LocalDate.of(2026, 5, 26)
    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val taskScheduleRepository: TaskScheduleRepository = mockk(relaxed = true)
    private val completeTaskOccurrenceUseCase: CompleteTaskOccurrenceUseCase = mockk(relaxed = true)
    private val useCase = AdvanceRecurringTaskOccurrenceUseCase(
        taskRepository,
        taskScheduleRepository,
        completeTaskOccurrenceUseCase
    )

    @Test
    fun `returns null when the block has no linked task`() = runTest {
        val result = useCase(block(taskId = null, occurrence = occurrenceDate))

        assertNull(result)
        coVerify(exactly = 0) { completeTaskOccurrenceUseCase(any(), any(), any()) }
    }

    @Test
    fun `returns null when the schedule is not due for this occurrence`() = runTest {
        val task = task()
        coEvery { taskRepository.getTaskById("task-1") } returns task
        coEvery { taskScheduleRepository.getTaskSchedule("task-1") } returns
            schedule(nextOccurrence = occurrenceDate.plusDays(1))

        val result = useCase(block(taskId = "task-1", occurrence = occurrenceDate))

        assertNull(result)
        coVerify(exactly = 0) { completeTaskOccurrenceUseCase(any(), any(), any()) }
    }

    @Test
    fun `advances and returns the task and updated schedule when due`() = runTest {
        val task = task()
        val updated = schedule(nextOccurrence = occurrenceDate.plusDays(1))
        coEvery { taskRepository.getTaskById("task-1") } returns task
        coEvery { taskScheduleRepository.getTaskSchedule("task-1") } returns
            schedule(nextOccurrence = occurrenceDate)
        coEvery { completeTaskOccurrenceUseCase("task-1", occurrenceDate, any()) } returns updated

        val result = useCase(block(taskId = "task-1", occurrence = occurrenceDate))

        assertEquals(task to updated, result)
    }

    private fun task(): Task = Task(
        id = "task-1",
        title = "Standup",
        description = null,
        isCompleted = false,
        priority = 1,
        dueDate = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun schedule(nextOccurrence: LocalDate): TaskSchedule = TaskSchedule(
        id = "schedule-1",
        taskId = "task-1",
        recurrenceRule = TaskRecurrenceRule.Daily(
            intervalDays = 1,
            startsOn = LocalDate.of(2026, 5, 25)
        ),
        nextOccurrenceDate = nextOccurrence,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun block(taskId: String?, occurrence: LocalDate?): TimeBlock = TimeBlock(
        id = "block-1",
        date = occurrenceDate,
        title = "Standup",
        category = "work",
        startMinuteOfDay = 540,
        durationMinutes = 15,
        timezone = "UTC",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.MOVABLE,
        energyLevel = EnergyIntensity.fromLevel(2),
        source = "test",
        taskId = taskId,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        taskOccurrenceDate = occurrence,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
