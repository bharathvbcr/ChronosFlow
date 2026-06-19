package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.PlannerTestFixtures.timeBlock
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises [PlannerService.resolveConflicts] against a live in-memory store and the real
 * [ConflictDetectionEngine], so the relocation/anchoring behaviour is verified end to end rather
 * than through a mocked detector.
 */
class PlannerServiceConflictResolutionTest {
    private val repository: TimeBlockRepository = mockk()
    private val store = mutableListOf<TimeBlock>()
    private lateinit var service: PlannerService
    private val date = PlannerTestFixtures.date

    @Before
    fun setup() {
        store.clear()
        every { repository.getTimeBlocksByDate(date) } answers { flowOf(store.toList()) }
        coEvery { repository.getTimeBlockById(any()) } answers {
            val id = firstArg<String>()
            store.firstOrNull { it.id == id }
        }
        coEvery { repository.saveTimeBlock(any()) } answers {
            val block = firstArg<TimeBlock>()
            store.removeAll { it.id == block.id }
            store.add(block)
        }
        service = PlannerService(repository, ConflictDetectionEngine())
    }

    private fun start(id: String): Int = store.first { it.id == id }.startMinuteOfDay

    @Test
    fun `no overlap reports nothing to fix`() = runTest {
        store += timeBlock(id = "a", startMinute = 9 * 60, durationMinutes = 60)
        store += timeBlock(id = "b", startMinute = 11 * 60, durationMinutes = 60)

        val result = service.resolveConflicts(date)

        assertFalse(result.hadConflicts)
        assertTrue(result.moves.isEmpty())
        assertEquals(0, result.unresolvedConflictCount)
    }

    @Test
    fun `two movable blocks overlapping slides the later one clear`() = runTest {
        store += timeBlock(id = "a", startMinute = 9 * 60, durationMinutes = 60)
        store += timeBlock(id = "b", startMinute = 9 * 60 + 30, durationMinutes = 60)

        val result = service.resolveConflicts(date)

        assertTrue(result.hadConflicts)
        assertEquals(0, result.unresolvedConflictCount)
        assertEquals(1, result.moves.size)
        assertEquals("b", result.moves.single().blockId)
        // a stays; b is pushed to start at/after a ends (9:00 + 60 = 10:00).
        assertEquals(9 * 60, start("a"))
        assertTrue(start("b") >= 10 * 60)
    }

    @Test
    fun `movable block colliding with a fixed block moves the movable one only`() = runTest {
        store += timeBlock(id = "fixed", startMinute = 9 * 60, durationMinutes = 120)
            .copy(flexibility = BlockFlexibility.FIXED)
        store += timeBlock(id = "flex", startMinute = 9 * 60 + 30, durationMinutes = 60)
            .copy(flexibility = BlockFlexibility.MOVABLE)

        val result = service.resolveConflicts(date)

        assertEquals(listOf("flex"), result.moves.map { it.blockId })
        assertEquals(9 * 60, start("fixed")) // anchor untouched
        assertTrue(start("flex") >= 11 * 60) // moved clear of the fixed block (ends 11:00)
        assertEquals(0, result.unresolvedConflictCount)
    }

    @Test
    fun `locked block is treated as immovable`() = runTest {
        store += timeBlock(id = "locked", startMinute = 9 * 60, durationMinutes = 120, isLocked = true)
        store += timeBlock(id = "flex", startMinute = 9 * 60 + 30, durationMinutes = 60)
            .copy(flexibility = BlockFlexibility.MOVABLE)

        val result = service.resolveConflicts(date)

        assertEquals(listOf("flex"), result.moves.map { it.blockId })
        assertEquals(9 * 60, start("locked"))
    }

    @Test
    fun `two immovable blocks overlapping cannot be auto-resolved`() = runTest {
        store += timeBlock(id = "a", startMinute = 9 * 60, durationMinutes = 60, isLocked = true)
        store += timeBlock(id = "b", startMinute = 9 * 60 + 30, durationMinutes = 60, isProtected = true)

        val result = service.resolveConflicts(date)

        assertTrue(result.hadConflicts)
        assertTrue(result.moves.isEmpty())
        assertTrue(result.unresolvedConflictCount > 0)
        // Neither immovable block moved.
        assertEquals(9 * 60, start("a"))
        assertEquals(9 * 60 + 30, start("b"))
    }

    @Test
    fun `non-conflicting blocks are left in place`() = runTest {
        store += timeBlock(id = "morning", startMinute = 7 * 60, durationMinutes = 60)
            .copy(flexibility = BlockFlexibility.MOVABLE)
        store += timeBlock(id = "a", startMinute = 9 * 60, durationMinutes = 60)
            .copy(flexibility = BlockFlexibility.MOVABLE)
        store += timeBlock(id = "b", startMinute = 9 * 60 + 30, durationMinutes = 60)
            .copy(flexibility = BlockFlexibility.MOVABLE)

        val result = service.resolveConflicts(date)

        // The unrelated early block is never touched — only the colliding pair is repaired.
        assertEquals(7 * 60, start("morning"))
        assertFalse(result.moves.any { it.blockId == "morning" })
    }

    @Test
    fun `fromMinute floor keeps relocations in the remaining day`() = runTest {
        store += timeBlock(id = "a", startMinute = 9 * 60, durationMinutes = 60)
            .copy(flexibility = BlockFlexibility.MOVABLE)
        store += timeBlock(id = "b", startMinute = 9 * 60 + 30, durationMinutes = 60)
            .copy(flexibility = BlockFlexibility.MOVABLE)

        // Repairing from 2pm: any moved block must land at or after 14:00.
        val result = service.resolveConflicts(date, fromMinute = 14 * 60)

        result.moves.forEach { assertTrue(it.newStartMinute >= 14 * 60) }
    }

    @Test
    fun `relocation avoids the active sleep window`() = runTest {
        // Day is full from 9:00 to 22:00 except a slot; force b to land outside sleep (22:00-06:00).
        store += timeBlock(id = "a", startMinute = 9 * 60, durationMinutes = 60)
            .copy(flexibility = BlockFlexibility.MOVABLE)
        store += timeBlock(id = "b", startMinute = 9 * 60 + 30, durationMinutes = 60)
            .copy(flexibility = BlockFlexibility.MOVABLE)
        val sleep = SleepSchedule(enabled = true, startMinute = 22 * 60, endMinute = 6 * 60)

        val result = service.resolveConflicts(date, sleepSchedule = sleep)

        result.moves.forEach { move ->
            assertFalse(sleep.intersects(move.newStartMinute, 60))
        }
    }

    @Test
    fun `setBlockStart forces a reposition for undo`() = runTest {
        store += timeBlock(id = "a", startMinute = 9 * 60, durationMinutes = 60)

        val result = service.setBlockStart("a", 15 * 60)

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals(15 * 60, start("a"))
    }

    @Test
    fun `summary message notes blocks still needing a manual change`() {
        val resolution = ConflictResolutionResult(
            moves = listOf(
                ResolvedBlockMove("a", "A", originalStartMinute = 0, newStartMinute = 60)
            ),
            unresolvedConflictCount = 2,
            hadConflicts = true
        )

        val message = conflictRepairSummaryMessage(resolution)

        assertTrue(message.startsWith("Moved 1 block to clear conflicts"))
        assertTrue(message.contains("2 still need a manual change"))
    }
}
