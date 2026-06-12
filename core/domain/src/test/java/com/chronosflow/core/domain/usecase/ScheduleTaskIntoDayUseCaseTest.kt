package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.domain.model.MoodEnergyCheckIn
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.FreeTimeCalculator
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.domain.repository.MoodEnergyRepository
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TaskScheduleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleTaskIntoDayUseCaseTest {
    private val taskRepository: TaskRepository = mockk()
    private val taskScheduleRepository: TaskScheduleRepository = mockk()
    private val plannerService: PlannerService = mockk()
    private val sleepScheduleRepository: SleepScheduleRepository = mockk()
    private val moodEnergyRepository: MoodEnergyRepository = mockk()
    private lateinit var useCase: ScheduleTaskIntoDayUseCase

    private val date = LocalDate.of(2026, 5, 9)
    private val now = Instant.parse("2026-05-08T12:00:00Z")
    private val currentDate = LocalDate.of(2026, 5, 8)
    private val currentTime = LocalTime.of(12, 0)

    @Before
    fun setup() {
        useCase = ScheduleTaskIntoDayUseCase(
            taskRepository = taskRepository,
            taskScheduleRepository = taskScheduleRepository,
            plannerService = plannerService,
            freeTimeCalculator = FreeTimeCalculator(),
            sleepScheduleRepository = sleepScheduleRepository,
            moodEnergyRepository = moodEnergyRepository
        )
        coEvery { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        coEvery { taskScheduleRepository.getTaskSchedule(any()) } returns null
        coEvery { moodEnergyRepository.getForDateRange(any(), any()) } returns emptyList()
    }

    @Test
    fun `schedules urgent task into first available DayDial gap`() = runTest {
        val captured = slot<TimeBlock>()
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1", priority = 2)
        coEvery { plannerService.getBlocksForDate(date) } returns listOf(block(start = 8 * 60, duration = 60, isProtected = true))
        coEvery { plannerService.createBlock(capture(captured)) } returns PlannerOperationResult.Applied(
            message = "Placement valid",
            blockId = "new-block",
            snappedToMinute = 9 * 60
        )

        val result = scheduleTask("task-1", date = date)

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals("Scheduled Write proposal into DayDial", result.message)
        assertEquals(9 * 60, captured.captured.startMinuteOfDay)
        assertEquals(60, captured.captured.durationMinutes)
        assertEquals(BlockProvenance.TASK_CONVERTED, captured.captured.provenance)
        assertEquals("task-1", captured.captured.taskId)
        assertTrue(captured.captured.isProtected)
    }

    @Test
    fun `urgent task is biased toward the measured energy peak`() = runTest {
        val captured = slot<TimeBlock>()
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1", priority = 2)
        coEvery { plannerService.getBlocksForDate(date) } returns emptyList()
        coEvery { moodEnergyRepository.getForDateRange(any(), any()) } returns peakCheckIns(peakHour = 15)
        coEvery { plannerService.createBlock(capture(captured)) } returns PlannerOperationResult.Applied(
            message = "Placement valid",
            blockId = "new-block",
            snappedToMinute = 15 * 60
        )

        val result = scheduleTask("task-1", date = date)

        assertTrue(result is PlannerOperationResult.Applied)
        // Free all day, peak energy at 15:00 -> high-priority task lands on the peak instead of 08:00.
        assertEquals(15 * 60, captured.captured.startMinuteOfDay)
        assertEquals(EnergyIntensity.HIGH, captured.captured.energyLevel)
    }

    @Test
    fun `low priority task ignores energy peak and keeps first-fit`() = runTest {
        val captured = slot<TimeBlock>()
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1", priority = 0)
        coEvery { plannerService.getBlocksForDate(date) } returns emptyList()
        coEvery { moodEnergyRepository.getForDateRange(any(), any()) } returns peakCheckIns(peakHour = 15)
        coEvery { plannerService.createBlock(capture(captured)) } returns PlannerOperationResult.Applied(
            message = "Placement valid",
            blockId = "new-block",
            snappedToMinute = 8 * 60
        )

        scheduleTask("task-1", date = date)

        assertEquals(8 * 60, captured.captured.startMinuteOfDay)
        coVerify(exactly = 0) { moodEnergyRepository.getForDateRange(any(), any()) }
    }

    @Test
    fun `rejects completed task before touching planner`() = runTest {
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1", completed = true)

        val result = scheduleTask("task-1", date = date)

        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals("Completed tasks cannot be scheduled", result.message)
        coVerify(exactly = 0) { plannerService.getBlocksForDate(any()) }
        coVerify(exactly = 0) { plannerService.createBlock(any()) }
    }

    @Test
    fun `rejects when no gap can fit requested duration`() = runTest {
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1")
        coEvery { plannerService.getBlocksForDate(date) } returns listOf(block(start = 0, duration = 1440))

        val result = scheduleTask(
            "task-1",
            date = date,
            preferredDurationMinutes = 30,
            currentDate = date
        )

        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals("No open 30 minute gap today", result.message)
        coVerify(exactly = 0) { plannerService.createBlock(any()) }
    }

    @Test
    fun `no gap message names future target date`() = runTest {
        val targetDate = LocalDate.of(2026, 5, 12)
        coEvery {
            taskRepository.getTaskById("task-1")
        } returns task(id = "task-1", targetDate = targetDate)
        coEvery {
            plannerService.getBlocksForDate(targetDate)
        } returns listOf(block(start = 0, duration = 1440, date = targetDate))

        val result = scheduleTask("task-1", preferredDurationMinutes = 30)

        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals("No open 30 minute gap on 2026-05-12", result.message)
        coVerify(exactly = 0) { plannerService.createBlock(any()) }
    }

    @Test
    fun `same-day scheduling refuses a task that would cross midnight`() = runTest {
        val today = LocalDate.of(2026, 5, 10)
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1")
        coEvery { plannerService.getBlocksForDate(today) } returns listOf(block(start = 0, duration = 23 * 60 + 30, date = today))

        val result = scheduleTask(
            "task-1",
            date = today,
            preferredDurationMinutes = 30,
            nowMinuteOfDay = 23 * 60 + 50,
            currentDate = today,
            currentTime = LocalTime.of(23, 50)
        )

        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals("No open 30 minute gap today", result.message)
        coVerify(exactly = 0) { plannerService.createBlock(any()) }
    }

    @Test
    fun `uses requested duration and normal priority metadata`() = runTest {
        val captured = slot<TimeBlock>()
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1", priority = 0)
        coEvery { plannerService.getBlocksForDate(date) } returns emptyList()
        coEvery { plannerService.createBlock(capture(captured)) } returns PlannerOperationResult.Applied(
            message = "Placement valid",
            blockId = "new-block",
            snappedToMinute = 8 * 60
        )

        scheduleTask("task-1", date = date, preferredDurationMinutes = 75)

        assertEquals(8 * 60, captured.captured.startMinuteOfDay)
        assertEquals(75, captured.captured.durationMinutes)
        assertEquals(EnergyIntensity.LOW, captured.captured.energyLevel)
        assertEquals(BlockFlexibility.RESIZABLE, captured.captured.flexibility)
    }

    @Test
    fun `uses task scheduling preferences when explicit values are absent`() = runTest {
        val captured = slot<TimeBlock>()
        val preferredDate = LocalDate.of(2026, 5, 12)
        coEvery {
            taskRepository.getTaskById("task-1")
        } returns task(
            id = "task-1",
            priority = 1,
            preferredDurationMinutes = 90,
            preferredStartMinuteOfDay = 13 * 60 + 30,
            targetDate = preferredDate
        )
        coEvery { plannerService.getBlocksForDate(preferredDate) } returns emptyList()
        coEvery { plannerService.createBlock(capture(captured)) } returns PlannerOperationResult.Applied(
            message = "Placement valid",
            blockId = "preferred-block",
            snappedToMinute = 13 * 60 + 30
        )

        val result = scheduleTask("task-1")

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals("Scheduled Write proposal into DayDial", result.message)
        assertEquals(preferredDate, captured.captured.date)
        assertEquals(13 * 60 + 30, captured.captured.startMinuteOfDay)
        assertEquals(90, captured.captured.durationMinutes)
        assertEquals(EnergyIntensity.MODERATE, captured.captured.energyLevel)
    }

    @Test
    fun `sleep schedule blocks overnight gaps from schedule today`() = runTest {
        coEvery { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule(
            enabled = true,
            startMinute = 21 * 60,
            endMinute = 7 * 60
        )
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1")
        coEvery {
            plannerService.getBlocksForDate(date)
        } returns listOf(block(start = 8 * 60, duration = 13 * 60, date = date))

        val result = scheduleTask(
            "task-1",
            date = date,
            preferredDurationMinutes = 30,
            currentDate = date
        )

        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals("No open 30 minute gap today", result.message)
        coVerify(exactly = 0) { plannerService.createBlock(any()) }
    }

    @Test
    fun `recurring task schedules next occurrence date and tags block`() = runTest {
        val captured = slot<TimeBlock>()
        val nextOccurrence = LocalDate.of(2026, 6, 2)
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1", targetDate = date)
        coEvery { taskScheduleRepository.getTaskSchedule("task-1") } returns schedule(taskId = "task-1", nextOccurrenceDate = nextOccurrence)
        coEvery { plannerService.getBlocksForDate(nextOccurrence) } returns emptyList()
        coEvery { plannerService.createBlock(capture(captured)) } returns PlannerOperationResult.Applied(
            message = "Placement valid",
            blockId = "recurring-block",
            snappedToMinute = 8 * 60
        )

        val result = scheduleTask("task-1")

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals(nextOccurrence, captured.captured.date)
        assertEquals(nextOccurrence, captured.captured.taskOccurrenceDate)
    }

    @Test
    fun `returns existing block when recurring occurrence already scheduled`() = runTest {
        val occurrenceDate = LocalDate.of(2026, 6, 2)
        val existing = block(
            start = 10 * 60,
            duration = 45,
            date = occurrenceDate,
            taskId = "task-1",
            taskOccurrenceDate = occurrenceDate
        )
        coEvery { taskRepository.getTaskById("task-1") } returns task(id = "task-1")
        coEvery { taskScheduleRepository.getTaskSchedule("task-1") } returns schedule(taskId = "task-1", nextOccurrenceDate = occurrenceDate)
        coEvery { plannerService.getBlocksForDate(occurrenceDate) } returns listOf(existing)

        val result = scheduleTask("task-1")

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals("Occurrence already scheduled for Write proposal", result.message)
        assertEquals("block-600", result.blockId)
        coVerify(exactly = 0) { plannerService.createBlock(any()) }
    }

    private fun task(
        id: String,
        priority: Int = 0,
        completed: Boolean = false,
        preferredDurationMinutes: Int? = null,
        preferredStartMinuteOfDay: Int? = null,
        targetDate: LocalDate? = null
    ): Task = Task(
        id = id,
        title = "Write proposal",
        description = null,
        isCompleted = completed,
        priority = priority,
        dueDate = null,
        createdAt = now,
        updatedAt = now,
        preferredDurationMinutes = preferredDurationMinutes,
        preferredStartMinuteOfDay = preferredStartMinuteOfDay,
        targetDate = targetDate
    )

    private suspend fun scheduleTask(
        taskId: String,
        date: LocalDate? = null,
        preferredDurationMinutes: Int? = null,
        nowMinuteOfDay: Int? = null,
        currentDate: LocalDate = this.currentDate,
        currentTime: LocalTime = this.currentTime
    ): PlannerOperationResult = useCase(
        taskId = taskId,
        date = date,
        preferredDurationMinutes = preferredDurationMinutes,
        nowMinuteOfDay = nowMinuteOfDay,
        currentDate = currentDate,
        currentTime = currentTime
    )

    private fun block(
        start: Int,
        duration: Int,
        date: LocalDate = this.date,
        isProtected: Boolean = false,
        taskId: String? = null,
        taskOccurrenceDate: LocalDate? = null
    ): TimeBlock = TimeBlock(
        id = "block-$start",
        date = date,
        title = "Existing",
        category = "WORK",
        startMinuteOfDay = start,
        durationMinutes = duration,
        timezone = "UTC",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.RESIZABLE,
        energyLevel = EnergyIntensity.MODERATE,
        source = "USER",
        taskId = taskId,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = isProtected,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = now,
        updatedAt = now,
        taskOccurrenceDate = taskOccurrenceDate
    )

    private fun peakCheckIns(peakHour: Int): List<MoodEnergyCheckIn> {
        val day = LocalDate.of(2026, 5, 7)
        fun checkIn(idx: Int, hour: Int, energy: Int) = MoodEnergyCheckIn(
            id = "checkin-$idx",
            blockId = null,
            moodScore = 3,
            stressScore = 2,
            energyScore = energy,
            focusScore = 3,
            notes = null,
            recordedAt = LocalDateTime.of(day, LocalTime.of(hour, 0)),
            checkInDate = day
        )
        // High energy clustered at the peak hour, low energy in the early morning.
        return List(4) { checkIn(it, peakHour, energy = 5) } +
            List(3) { checkIn(it + 4, 9, energy = 1) }
    }

    private fun schedule(taskId: String, nextOccurrenceDate: LocalDate): TaskSchedule = TaskSchedule(
        id = "schedule-$taskId",
        taskId = taskId,
        recurrenceRule = TaskRecurrenceRule.Daily(intervalDays = 1, startsOn = nextOccurrenceDate),
        nextOccurrenceDate = nextOccurrenceDate,
        createdAt = now,
        updatedAt = now
    )
}
