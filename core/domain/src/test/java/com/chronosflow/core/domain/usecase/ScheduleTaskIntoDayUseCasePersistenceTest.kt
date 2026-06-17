package com.chronosflow.core.domain.usecase

import app.cash.turbine.test
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.model.SleepTrack
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.FreeTimeCalculator
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.model.MoodEnergyCheckIn
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.domain.repository.MoodEnergyRepository
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.SleepTrackRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TaskScheduleRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTaskIntoDayUseCasePersistenceTest {
    private val date = LocalDate.of(2026, 5, 9)
    private val now = Instant.parse("2026-05-09T12:00:00Z")

    @Test
    fun `scheduling task persists linked block and notifies dial observers`() = runTest {
        val timeBlockRepository = ObservableTimeBlockRepository()
        val useCase = useCase(timeBlockRepository)

        timeBlockRepository.getTimeBlocksByDate(date).test {
            assertEquals(emptyList<TimeBlock>(), awaitItem())

            val result = useCase(
                taskId = "task-1",
                date = date,
                preferredDurationMinutes = 45,
                currentDate = date,
                currentTime = LocalTime.of(8, 0)
            )

            assertTrue(result is PlannerOperationResult.Applied)
            val blocks = awaitItem()
            assertEquals(1, blocks.size)
            val scheduled = blocks.single()
            assertEquals("task-1", scheduled.taskId)
            assertEquals("Draft proposal", scheduled.title)
            assertEquals(BlockProvenance.TASK_CONVERTED, scheduled.provenance)
            assertEquals(8 * 60, scheduled.startMinuteOfDay)
            assertEquals(45, scheduled.durationMinutes)
            assertEquals(result.blockId, scheduled.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rejected scheduling leaves repository unchanged`() = runTest {
        val existing = timeBlock(
            id = "full-day",
            startMinute = 0,
            durationMinutes = 1440
        )
        val timeBlockRepository = ObservableTimeBlockRepository(existing)
        val useCase = useCase(timeBlockRepository)

        val result = useCase(
            taskId = "task-1",
            date = date,
            preferredDurationMinutes = 45,
            currentDate = date,
            currentTime = LocalTime.of(8, 0)
        )

        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals(listOf(existing), timeBlockRepository.getTimeBlocksByDate(date).first())
    }

    @Test
    fun `scheduled task block survives recreated use case reader`() = runTest {
        val timeBlockRepository = ObservableTimeBlockRepository()
        val firstUseCase = useCase(timeBlockRepository)

        val result = firstUseCase(
            taskId = "task-1",
            date = date,
            preferredDurationMinutes = 30,
            currentDate = date,
            currentTime = LocalTime.of(10, 0)
        )

        assertTrue(result is PlannerOperationResult.Applied)
        val recreatedPlannerService = PlannerService(timeBlockRepository)
        val restored = recreatedPlannerService.getBlocksForDate(date).single()
        assertNotNull(restored)
        assertEquals(result.blockId, restored.id)
        assertEquals("task-1", restored.taskId)
        assertEquals(10 * 60, restored.startMinuteOfDay)
    }

    private fun useCase(
        timeBlockRepository: ObservableTimeBlockRepository,
        taskRepository: TaskRepository = FakeTaskRepository(
            task(
                id = "task-1",
                title = "Draft proposal"
            )
        ),
        taskScheduleRepository: TaskScheduleRepository = FakeTaskScheduleRepository()
    ): ScheduleTaskIntoDayUseCase = ScheduleTaskIntoDayUseCase(
        taskRepository = taskRepository,
        taskScheduleRepository = taskScheduleRepository,
        plannerService = PlannerService(timeBlockRepository),
        freeTimeCalculator = FreeTimeCalculator(),
        sleepScheduleRepository = object : SleepScheduleRepository {
            override fun getSleepSchedule(): SleepSchedule = SleepSchedule.default()
        },
        sleepTrackRepository = object : SleepTrackRepository {
            override fun observeForDate(date: LocalDate): Flow<SleepTrack?> = flowOf(null)
            override fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<SleepTrack>> =
                flowOf(emptyList())
            override suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<SleepTrack> =
                emptyList()
            override suspend fun upsert(track: SleepTrack) = Unit
            override suspend fun delete(id: String) = Unit
        },
        moodEnergyRepository = FakeMoodEnergyRepository()
    )

    private fun task(
        id: String,
        title: String
    ): Task = Task(
        id = id,
        title = title,
        description = null,
        isCompleted = false,
        priority = 1,
        dueDate = null,
        createdAt = now,
        updatedAt = now
    )

    private fun timeBlock(
        id: String,
        startMinute: Int,
        durationMinutes: Int,
        taskId: String? = null
    ): TimeBlock = TimeBlock(
        id = id,
        date = date,
        title = "Existing",
        category = "WORK",
        startMinuteOfDay = startMinute,
        durationMinutes = durationMinutes,
        timezone = "UTC",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.RESIZABLE,
        energyLevel = EnergyIntensity.MODERATE,
        source = "TEST",
        taskId = taskId,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = now,
        updatedAt = now
    )
}

private class ObservableTimeBlockRepository(
    vararg initialBlocks: TimeBlock
) : TimeBlockRepository {
    private val blocks = MutableStateFlow(initialBlocks.toList())

    override fun getTimeBlocksByDate(date: LocalDate): Flow<List<TimeBlock>> =
        blocks.map { entries ->
            entries.filter { it.date == date }.sortedBy { it.startMinuteOfDay }
        }

    override suspend fun getTimeBlockById(id: String): TimeBlock? =
        blocks.value.firstOrNull { it.id == id }

    override suspend fun saveTimeBlock(timeBlock: TimeBlock) {
        blocks.value = blocks.value.filterNot { it.id == timeBlock.id } + timeBlock
    }

    override suspend fun deleteTimeBlock(timeBlock: TimeBlock) {
        blocks.value = blocks.value.filterNot { it.id == timeBlock.id }
    }

    override suspend fun clearDay(date: LocalDate) {
        blocks.value = blocks.value.filterNot { it.date == date }
    }
}

private class FakeTaskRepository(
    vararg tasks: Task
) : TaskRepository {
    private val tasksById = tasks.associateBy { it.id }.toMutableMap()
    private val tasksFlow = MutableStateFlow(tasksById.values.toList())

    override fun getAllTasks(): Flow<List<Task>> = tasksFlow

    override suspend fun getTaskById(id: String): Task? = tasksById[id]

    override suspend fun saveTask(task: Task) {
        tasksById[task.id] = task
        tasksFlow.value = tasksById.values.toList()
    }

    override suspend fun deleteTask(task: Task) {
        tasksById.remove(task.id)
        tasksFlow.value = tasksById.values.toList()
    }
}

private class FakeMoodEnergyRepository : MoodEnergyRepository {
    override fun observeForDate(date: LocalDate): Flow<List<MoodEnergyCheckIn>> = flowOf(emptyList())
    override fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<MoodEnergyCheckIn>> =
        flowOf(emptyList())
    override suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<MoodEnergyCheckIn> = emptyList()
    override suspend fun getLatest(): MoodEnergyCheckIn? = null
    override suspend fun getForBlock(blockId: String): List<MoodEnergyCheckIn> = emptyList()
    override suspend fun save(checkIn: MoodEnergyCheckIn) = Unit
    override suspend fun delete(id: String) = Unit
}

private class FakeTaskScheduleRepository : TaskScheduleRepository {
    private val schedules = MutableStateFlow<Map<String, TaskSchedule>>(emptyMap())

    override fun observeTaskSchedule(taskId: String): Flow<TaskSchedule?> =
        schedules.map { it[taskId] }

    override suspend fun getTaskSchedule(taskId: String): TaskSchedule? =
        schedules.value[taskId]

    override suspend fun saveTaskSchedule(schedule: TaskSchedule) {
        schedules.value = schedules.value + (schedule.taskId to schedule)
    }

    override suspend fun deleteTaskSchedule(taskId: String) {
        schedules.value = schedules.value - taskId
    }
}
