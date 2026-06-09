package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.repository.CalendarEventRepository
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.MoveBlockUseCase
import com.chronosflow.core.domain.usecase.ResizeBlockUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialBlockDelegateTest {
    private val sleepScheduleRepository: SleepScheduleRepository = mockk()
    private val moveBlockUseCase: MoveBlockUseCase = mockk(relaxed = true)
    private val resizeBlockUseCase: ResizeBlockUseCase = mockk(relaxed = true)
    private val calendarEventRepository: CalendarEventRepository = mockk(relaxed = true)
    private val date = LocalDate.of(2026, 5, 8)

    @Test
    fun `createQuickBlock rejects blocks inside sleep window`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository()
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule(
            enabled = true,
            startMinute = 21 * 60,
            endMinute = 7 * 60
        )
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = moveBlockUseCase,
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )
        var result: PlannerOperationResult? = null

        delegate.createQuickBlock(
            scope = this,
            date = date,
            title = "Late work",
            startMinute = 22 * 60,
            durationMinutes = 45,
            category = "WORK"
        ) { operation, _ ->
            result = operation
        }

        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals("This time overlaps your sleep schedule", result?.message)
        assertTrue(repository.blocks.isEmpty())
    }

    @Test
    fun `updateBlockDetails rejects edits that overlap sleep window`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository(
            listOf(timeBlock(id = "block-1", date = date, startMinute = 9 * 60, durationMinutes = 60))
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule(
            enabled = true,
            startMinute = 21 * 60,
            endMinute = 7 * 60
        )
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = moveBlockUseCase,
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )
        var result: PlannerOperationResult? = null

        delegate.updateBlockDetails(
            scope = this,
            blockId = "block-1",
            title = "Late work",
            startMinute = 22 * 60,
            durationMinutes = 45,
            category = "WORK",
            isLocked = false,
            isProtected = false
        ) { operation, _ ->
            result = operation
        }

        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals("This time overlaps your sleep schedule", result?.message)
        assertEquals(9 * 60, repository.blocks.getValue("block-1").startMinuteOfDay)
    }

    @Test
    fun `onBlockResize previews resize without persisting`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository(
            listOf(timeBlock(id = "block-1", date = date, startMinute = 9 * 60, durationMinutes = 60))
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = moveBlockUseCase,
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )
        var result: PlannerOperationResult? = null

        delegate.onBlockResize(
            scope = this,
            blockId = "block-1",
            startMinute = 9 * 60,
            durationMinutes = 75
        ) { operation, _ ->
            result = operation
        }

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals(60, repository.blocks.getValue("block-1").durationMinutes)
        assertEquals(75, delegate.dragPreview.value?.durationMinutes)
    }

    @Test
    fun `onBlockResizeCommitted persists a valid resize preview`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository(
            listOf(timeBlock(id = "block-1", date = date, startMinute = 9 * 60, durationMinutes = 60))
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        coEvery { resizeBlockUseCase("block-1", 9 * 60, 75) } returns PlannerOperationResult.Applied(
            message = "Block resized",
            blockId = "block-1",
            snappedToMinute = null
        )
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = moveBlockUseCase,
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )
        var result: PlannerOperationResult? = null

        delegate.onBlockResize(
            scope = this,
            blockId = "block-1",
            startMinute = 9 * 60,
            durationMinutes = 75
        ) { _, _ -> }
        delegate.onBlockResizeCommitted(
            scope = this,
            blockId = "block-1",
            finalStartMinute = 9 * 60,
            finalDurationMinutes = 75
        ) { operation, _ ->
            result = operation
        }

        assertTrue(result is PlannerOperationResult.Applied)
        coVerify { resizeBlockUseCase("block-1", 9 * 60, 75) }
        assertEquals(null, delegate.dragPreview.value)
    }

    @Test
    fun `onBlockDragMoved previews move without persisting`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository(
            listOf(timeBlock(id = "block-1", date = date, startMinute = 9 * 60, durationMinutes = 60))
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = MoveBlockUseCase(PlannerService(repository)),
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )
        var result: PlannerOperationResult? = null

        delegate.onBlockDragStarted("block-1", 9 * 60)
        delegate.onBlockDragMoved(
            scope = this,
            blockId = "block-1",
            newStartMinute = 10 * 60
        ) { operation, _ ->
            result = operation
        }

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals(9 * 60, repository.blocks.getValue("block-1").startMinuteOfDay)
        assertEquals(10 * 60, delegate.dragPreview.value?.startMinute)
    }

    @Test
    fun `onBlockMoveCommitted persists valid drag through move use case`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository(
            listOf(timeBlock(id = "block-1", date = date, startMinute = 9 * 60, durationMinutes = 60))
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = MoveBlockUseCase(PlannerService(repository)),
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )
        var result: PlannerOperationResult? = null

        delegate.onBlockDragStarted("block-1", 9 * 60)
        delegate.onBlockDragMoved(
            scope = this,
            blockId = "block-1",
            newStartMinute = 10 * 60
        ) { _, _ -> }
        delegate.onBlockMoveCommitted(
            scope = this,
            blockId = "block-1",
            finalStartMinute = 10 * 60
        ) { operation, _ ->
            result = operation
        }

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals(10 * 60, repository.blocks.getValue("block-1").startMinuteOfDay)
        assertNull(delegate.dragPreview.value)
    }

    @Test
    fun `onBlockMoveCommitted rejects invalid drag preview without persisting`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository(
            listOf(
                timeBlock(id = "block-1", date = date, startMinute = 9 * 60, durationMinutes = 60),
                timeBlock(id = "block-2", date = date, startMinute = 10 * 60, durationMinutes = 60)
            )
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = MoveBlockUseCase(PlannerService(repository)),
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )
        var previewResult: PlannerOperationResult? = null
        var commitResult: PlannerOperationResult? = null

        delegate.onBlockDragStarted("block-1", 9 * 60)
        delegate.onBlockDragMoved(
            scope = this,
            blockId = "block-1",
            newStartMinute = 10 * 60
        ) { operation, _ ->
            previewResult = operation
        }
        delegate.onBlockMoveCommitted(
            scope = this,
            blockId = "block-1",
            finalStartMinute = 10 * 60
        ) { operation, _ ->
            commitResult = operation
        }

        assertTrue(previewResult is PlannerOperationResult.Conflict || previewResult is PlannerOperationResult.Locked)
        assertNull(commitResult)
        assertEquals(9 * 60, repository.blocks.getValue("block-1").startMinuteOfDay)
        assertNull(delegate.dragPreview.value)
    }

    @Test
    fun `repeated drag move gestures persist validated commits without crashing`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository(
            listOf(timeBlock(id = "block-1", date = date, startMinute = 9 * 60, durationMinutes = 45))
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = MoveBlockUseCase(PlannerService(repository)),
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )

        repeat(8) { index ->
            val currentStart = repository.blocks.getValue("block-1").startMinuteOfDay
            val nextStart = 9 * 60 + (index + 1) * 15
            var previewResult: PlannerOperationResult? = null
            var commitResult: PlannerOperationResult? = null

            delegate.onBlockDragStarted("block-1", currentStart)
            delegate.onBlockDragMoved(
                scope = this,
                blockId = "block-1",
                newStartMinute = nextStart
            ) { operation, _ ->
                previewResult = operation
            }
            assertTrue(previewResult is PlannerOperationResult.Applied)
            assertEquals(currentStart, repository.blocks.getValue("block-1").startMinuteOfDay)

            delegate.onBlockMoveCommitted(
                scope = this,
                blockId = "block-1",
                finalStartMinute = nextStart
            ) { operation, _ ->
                commitResult = operation
            }

            assertTrue(commitResult is PlannerOperationResult.Applied)
            assertEquals(nextStart, repository.blocks.getValue("block-1").startMinuteOfDay)
            assertNull(delegate.dragPreview.value)
        }
    }

    @Test
    fun `deleteBlock removes exported calendar event for user blocks`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository(
            listOf(
                timeBlock(
                    id = "block-1",
                    date = date,
                    startMinute = 9 * 60,
                    durationMinutes = 60,
                    calendarEventId = 42L
                )
            )
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        coEvery { calendarEventRepository.deleteExportedTimeBlock(42L) } returns true
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = moveBlockUseCase,
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )

        delegate.deleteBlock(
            scope = this,
            blockId = "block-1",
            onResult = { _, _ -> },
            onDeleted = {}
        )

        coVerify { calendarEventRepository.deleteExportedTimeBlock(42L) }
        assertTrue(repository.blocks.isEmpty())
    }

    @Test
    fun `deleteBlock keeps imported device calendar event untouched`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryTimeBlockRepository(
            listOf(
                timeBlock(
                    id = "calendar-import-99",
                    date = date,
                    startMinute = 9 * 60,
                    durationMinutes = 60,
                    provenance = BlockProvenance.CALENDAR_IMPORTED,
                    calendarEventId = 99L
                )
            )
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = moveBlockUseCase,
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )

        delegate.deleteBlock(
            scope = this,
            blockId = "calendar-import-99",
            onResult = { _, _ -> },
            onDeleted = {}
        )

        coVerify(exactly = 0) { calendarEventRepository.deleteExportedTimeBlock(99L) }
        assertTrue(repository.blocks.isEmpty())
    }

    @Test
    fun `clearCurrentDay removes exported calendar links before clearing local blocks`() = runTest(UnconfinedTestDispatcher()) {
        val otherDate = date.plusDays(1)
        val repository = InMemoryTimeBlockRepository(
            listOf(
                timeBlock(
                    id = "exported",
                    date = date,
                    startMinute = 9 * 60,
                    durationMinutes = 60,
                    calendarEventId = 42L
                ),
                timeBlock(
                    id = "imported",
                    date = date,
                    startMinute = 11 * 60,
                    durationMinutes = 60,
                    provenance = BlockProvenance.CALENDAR_IMPORTED,
                    calendarEventId = 99L
                ),
                timeBlock(
                    id = "other-day",
                    date = otherDate,
                    startMinute = 10 * 60,
                    durationMinutes = 45,
                    calendarEventId = 77L
                )
            )
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        coEvery { calendarEventRepository.deleteExportedTimeBlock(42L) } returns true
        val delegate = DayDialBlockDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            moveBlockUseCase = moveBlockUseCase,
            resizeBlockUseCase = resizeBlockUseCase,
            calendarEventRepository = calendarEventRepository,
        )

        delegate.clearCurrentDay(this, date)

        coVerify { calendarEventRepository.deleteExportedTimeBlock(42L) }
        coVerify(exactly = 0) { calendarEventRepository.deleteExportedTimeBlock(99L) }
        coVerify(exactly = 0) { calendarEventRepository.deleteExportedTimeBlock(77L) }
        assertEquals(listOf("other-day"), repository.blocks.keys.toList())
    }

    private fun timeBlock(
        id: String,
        date: LocalDate,
        startMinute: Int,
        durationMinutes: Int,
        provenance: BlockProvenance = BlockProvenance.USER_CREATED,
        calendarEventId: Long? = null
    ): TimeBlock {
        val now = Instant.parse("2026-05-08T12:00:00Z")
        return TimeBlock(
            id = id,
            date = date,
            title = "Focus",
            category = "WORK",
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            timezone = "UTC",
            provenance = provenance,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "TEST",
            taskId = null,
            calendarEventId = calendarEventId,
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

    private class InMemoryTimeBlockRepository(
        initialBlocks: List<TimeBlock> = emptyList()
    ) : TimeBlockRepository {
        val blocks = initialBlocks.associateBy { it.id }.toMutableMap()

        override fun getTimeBlocksByDate(date: LocalDate): Flow<List<TimeBlock>> = flow {
            emit(blocks.values.filter { it.date == date })
        }

        override suspend fun getTimeBlockById(id: String): TimeBlock? = blocks[id]

        override suspend fun saveTimeBlock(timeBlock: TimeBlock) {
            blocks[timeBlock.id] = timeBlock
        }

        override suspend fun deleteTimeBlock(timeBlock: TimeBlock) {
            blocks.remove(timeBlock.id)
        }

        override suspend fun clearDay(date: LocalDate) {
            blocks.entries.removeIf { it.value.date == date }
        }
    }
}
