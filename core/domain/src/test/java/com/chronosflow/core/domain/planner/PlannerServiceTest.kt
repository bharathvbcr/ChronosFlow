package com.chronosflow.core.domain.planner

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.ScheduleConflictSeverity
import com.chronosflow.core.domain.planner.PlannerTestFixtures.timeBlock
import com.chronosflow.core.domain.repository.TimeBlockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class PlannerServiceTest {
    private val repository: TimeBlockRepository = mockk()
    private val conflictDetectionEngine: ConflictDetectionEngine = mockk()
    private lateinit var service: PlannerService
    private val date = PlannerTestFixtures.date

    @Before
    fun setup() {
        service = PlannerService(repository, conflictDetectionEngine)
    }

    @Test
    fun `createBlock rejects locked block`() = runTest {
        val block = timeBlock(isLocked = true)
        val result = service.createBlock(block)
        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals("Cannot create locked block", result.message)
    }

    @Test
    fun `moveBlock snaps to grid and saves if valid`() = runTest {
        val block = timeBlock(id = "1", startMinute = 600)
        coEvery { repository.getTimeBlockById("1") } returns block
        every { repository.getTimeBlocksByDate(date) } returns flowOf(listOf(block))
        every { conflictDetectionEngine.detect(any()) } returns emptyList()
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        val result = service.moveBlock("1", 607) // Should snap to 600 or 615? 7 is closer to 0 than 15. 15/2 = 7.5. So 7 snaps to 0.

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals(600, (result as PlannerOperationResult.Applied).snappedToMinute)
        coVerify { repository.saveTimeBlock(match { it.id == "1" && it.startMinuteOfDay == 600 }) }
    }

    @Test
    fun `moveBlock rejects locked or fixed blocks`() = runTest {
        val lockedBlock = timeBlock(id = "locked", isLocked = true)
        val fixedBlock = timeBlock(id = "fixed").copy(flexibility = BlockFlexibility.FIXED)
        
        coEvery { repository.getTimeBlockById("locked") } returns lockedBlock
        coEvery { repository.getTimeBlockById("fixed") } returns fixedBlock

        assertTrue(service.moveBlock("locked", 700) is PlannerOperationResult.Locked)
        assertTrue(service.moveBlock("fixed", 700) is PlannerOperationResult.Locked)
    }

    @Test
    fun `resizeBlockWindow updates duration and saves if valid`() = runTest {
        val block = timeBlock(id = "1", durationMinutes = 60)
        coEvery { repository.getTimeBlockById("1") } returns block
        every { repository.getTimeBlocksByDate(date) } returns flowOf(listOf(block))
        every { conflictDetectionEngine.detect(any()) } returns emptyList()
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        val result = service.resizeBlockWindow("1", block.startMinuteOfDay, 90)

        assertTrue(result is PlannerOperationResult.Applied)
        coVerify { repository.saveTimeBlock(match { it.id == "1" && it.durationMinutes == 90 }) }
    }

    @Test
    fun `validatePlacement detects blocking conflicts`() = runTest {
        val block = timeBlock(id = "1", startMinute = 600, durationMinutes = 60)
        val other = timeBlock(id = "2", startMinute = 630, durationMinutes = 60, isLocked = true)
        
        coEvery { repository.getTimeBlockById("1") } returns block
        every { repository.getTimeBlocksByDate(date) } returns flowOf(listOf(block, other))
        
        // Mock conflict detection to return a blocking conflict
        every { conflictDetectionEngine.detect(any()) } returns listOf(
            com.chronosflow.core.domain.model.ScheduleConflict(
                primaryBlockId = "1",
                conflictingBlockId = "2",
                overlapStartMinute = 630,
                overlapEndMinute = 660,
                severity = ScheduleConflictSeverity.BLOCKING,
                reason = "Locked block collision"
            )
        )

        val result = service.moveBlock("1", 615)
        assertTrue(result is PlannerOperationResult.Locked)
        assertEquals("Locked block collision detected", result.message)
    }

    @Test
    fun `rebalanceDay moves flexible blocks after immutable ones`() = runTest {
        val fixed = timeBlock(id = "fixed", startMinute = 0, durationMinutes = 120).copy(flexibility = BlockFlexibility.FIXED)
        val flexible = timeBlock(id = "flex", startMinute = 0, durationMinutes = 60).copy(flexibility = BlockFlexibility.MOVABLE)
        
        every { repository.getTimeBlocksByDate(date) } returns flowOf(listOf(fixed, flexible))
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        val result = service.rebalanceDay(date)

        assertTrue(result is PlannerOperationResult.Applied)
        // flex should be moved after fixed (at 120)
        coVerify { repository.saveTimeBlock(match { it.id == "flex" && it.startMinuteOfDay == 120 }) }
        assertEquals(listOf("flex"), (result as PlannerOperationResult.Applied).affectedBlockIds)
        assertTrue(result.message.startsWith("Moved 1 block:"))
    }

    @Test
    fun `rebalanceDay reports nothing to do when every block already fits`() = runTest {
        val first = timeBlock(id = "a", startMinute = 0, durationMinutes = 60).copy(flexibility = BlockFlexibility.MOVABLE)
        val second = timeBlock(id = "b", startMinute = 65, durationMinutes = 60).copy(flexibility = BlockFlexibility.MOVABLE)

        every { repository.getTimeBlocksByDate(date) } returns flowOf(listOf(first, second))
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        val result = service.rebalanceDay(date)

        assertTrue(result is PlannerOperationResult.Rejected)
        assertEquals("Everything already fits — nothing to rebalance", result.message)
    }

    @Test
    fun `rebalance summary names moved blocks and caps the list`() {
        val moved = (1..5).map { index ->
            timeBlock(id = "b$index", startMinute = index * 60, durationMinutes = 30)
                .copy(title = "Block $index")
        }

        assertEquals(
            "Moved 5 blocks: Block 1 → 1:00, Block 2 → 2:00, Block 3 → 3:00 and 2 more",
            rebalanceSummaryMessage(moved)
        )
    }

    @Test
    fun `logActualWindow updates actual times`() = runTest {
        val block = timeBlock(id = "1")
        coEvery { repository.getTimeBlockById("1") } returns block
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        val result = service.logActualWindow("1", 500, 600)

        assertTrue(result is PlannerOperationResult.Applied)
        coVerify { repository.saveTimeBlock(match { it.id == "1" && it.actualStartMinuteOfDay == 500 && it.actualEndMinuteOfDay == 600 }) }
    }

    @Test
    fun `previewMove returns Applied for valid placement`() = runTest {
        val block = timeBlock(id = "1", startMinute = 600)
        coEvery { repository.getTimeBlockById("1") } returns block
        every { repository.getTimeBlocksByDate(date) } returns flowOf(listOf(block))
        every { conflictDetectionEngine.detect(any()) } returns emptyList()

        val result = service.previewMove("1", 700)

        assertTrue(result is PlannerOperationResult.Applied)
        // 700 snaps to 705 on a 15m grid
        assertEquals(705, (result as PlannerOperationResult.Applied).snappedToMinute)
    }

    @Test
    fun `previewResizeWindow returns Applied for valid resize`() = runTest {
        val block = timeBlock(id = "1", durationMinutes = 60)
        coEvery { repository.getTimeBlockById("1") } returns block
        every { repository.getTimeBlocksByDate(date) } returns flowOf(listOf(block))
        every { conflictDetectionEngine.detect(any()) } returns emptyList()

        val result = service.previewResizeWindow("1", block.startMinuteOfDay, 120)

        assertTrue(result is PlannerOperationResult.Applied)
        assertEquals("Resize preview valid", (result as PlannerOperationResult.Applied).message)
    }

    @Test
    fun `deleteBlockById deletes and returns Applied`() = runTest {
        val block = timeBlock(id = "1")
        coEvery { repository.getTimeBlockById("1") } returns block
        coEvery { repository.deleteTimeBlock(block) } returns Unit

        val result = service.deleteBlockById("1")

        assertTrue(result is PlannerOperationResult.Applied)
        coVerify { repository.deleteTimeBlock(block) }
    }

    @Test
    fun `makeAiSuggestedBlock creates block with correct fields`() = runTest {
        val result = service.makeAiSuggestedBlock(
            date = date,
            title = "AI Block",
            startMinute = 600,
            durationMinutes = 60,
            category = "WORK",
            timezone = "UTC"
        )

        assertEquals("AI Block", result.title)
        assertEquals(600, result.startMinuteOfDay)
        assertEquals(60, result.durationMinutes)
        assertEquals(com.chronosflow.core.domain.model.BlockProvenance.AI_SUGGESTED, result.provenance)
    }
}
