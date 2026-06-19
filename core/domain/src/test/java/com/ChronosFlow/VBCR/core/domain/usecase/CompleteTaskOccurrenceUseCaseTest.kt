package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.TaskRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CompleteTaskOccurrenceUseCaseTest {

    private val repository: TaskScheduleRepository = mockk()
    private lateinit var resolver: ResolveNextTaskOccurrenceUseCase
    private lateinit var useCase: CompleteTaskOccurrenceUseCase
    private val now = Instant.parse("2026-05-25T15:00:00Z")

    @Before
    fun setup() {
        resolver = ResolveNextTaskOccurrenceUseCase()
        useCase = CompleteTaskOccurrenceUseCase(repository, resolver)
    }

    @Test
    fun `updates schedule with last completed and next occurrence`() = runTest {
        val schedule = TaskSchedule(
            id = "schedule-1",
            taskId = "task-1",
            recurrenceRule = TaskRecurrenceRule.Daily(
                intervalDays = 2,
                startsOn = LocalDate.of(2026, 5, 25)
            ),
            nextOccurrenceDate = LocalDate.of(2026, 5, 25),
            createdAt = now,
            updatedAt = now
        )
        coEvery { repository.getTaskSchedule("task-1") } returns schedule
        coEvery { repository.saveTaskSchedule(any()) } returns Unit

        val updated = useCase("task-1", completedOccurrenceDate = LocalDate.of(2026, 5, 25), updatedAt = now.plusSeconds(60))

        assertEquals(LocalDate.of(2026, 5, 25), updated?.lastCompletedOccurrenceDate)
        assertEquals(LocalDate.of(2026, 5, 27), updated?.nextOccurrenceDate)
        coVerify {
            repository.saveTaskSchedule(
                match {
                    it.taskId == "task-1" &&
                        it.lastCompletedOccurrenceDate == LocalDate.of(2026, 5, 25) &&
                        it.nextOccurrenceDate == LocalDate.of(2026, 5, 27)
                }
            )
        }
    }

    @Test
    fun `returns null when schedule is missing`() = runTest {
        coEvery { repository.getTaskSchedule("task-1") } returns null

        val updated = useCase("task-1", completedOccurrenceDate = LocalDate.of(2026, 5, 25), updatedAt = now)

        assertNull(updated)
        coVerify(exactly = 0) { repository.saveTaskSchedule(any()) }
    }
}
