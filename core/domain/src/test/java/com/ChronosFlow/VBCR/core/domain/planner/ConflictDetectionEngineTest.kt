package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.ScheduleConflictSeverity
import com.ChronosFlow.VBCR.core.domain.planner.PlannerTestFixtures.timeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictDetectionEngineTest {
    private val engine = ConflictDetectionEngine()
    private val date = PlannerTestFixtures.date

    @Test
    fun `no blocks means no conflicts`() {
        val conflicts = engine.detect(emptyList())
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `non-overlapping blocks have no conflicts`() {
        val blocks = listOf(
            timeBlock(id = "1", startMinute = 100, durationMinutes = 50),
            timeBlock(id = "2", startMinute = 200, durationMinutes = 50)
        )
        val conflicts = engine.detect(blocks)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `overlapping blocks create a warning conflict`() {
        val blocks = listOf(
            timeBlock(id = "1", startMinute = 100, durationMinutes = 100),
            timeBlock(id = "2", startMinute = 150, durationMinutes = 100)
        )
        val conflicts = engine.detect(blocks)
        assertEquals(1, conflicts.size)
        val conflict = conflicts[0]
        assertEquals("1", conflict.primaryBlockId)
        assertEquals("2", conflict.conflictingBlockId)
        assertEquals(150, conflict.overlapStartMinute)
        assertEquals(200, conflict.overlapEndMinute)
        assertEquals(ScheduleConflictSeverity.WARNING, conflict.severity)
    }

    @Test
    fun `overlapping with locked block creates a blocking conflict`() {
        val blocks = listOf(
            timeBlock(id = "1", startMinute = 100, durationMinutes = 100, isLocked = true),
            timeBlock(id = "2", startMinute = 150, durationMinutes = 100)
        )
        val conflicts = engine.detect(blocks)
        assertEquals(1, conflicts.size)
        assertEquals(ScheduleConflictSeverity.BLOCKING, conflicts[0].severity)
    }

    @Test
    fun `overlapping with protected block creates a blocking conflict`() {
        val blocks = listOf(
            timeBlock(id = "1", startMinute = 100, durationMinutes = 100, isProtected = true),
            timeBlock(id = "2", startMinute = 150, durationMinutes = 100)
        )
        val conflicts = engine.detect(blocks)
        assertEquals(1, conflicts.size)
        assertEquals(ScheduleConflictSeverity.BLOCKING, conflicts[0].severity)
    }

    @Test
    fun `blocks on different days do not conflict`() {
        val blocks = listOf(
            timeBlock(id = "1", startMinute = 100, durationMinutes = 100),
            timeBlock(id = "2", startMinute = 150, durationMinutes = 100, date = date.plusDays(1))
        )
        val conflicts = engine.detect(blocks)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `wrapped blocks can conflict at both ends`() {
        // Block 1: 23:30 to 00:30 (1410 to 60)
        // Block 2: 00:10 to 00:20 (10 to 20)
        val blocks = listOf(
            timeBlock(id = "1", startMinute = 1410, durationMinutes = 60),
            timeBlock(id = "2", startMinute = 10, durationMinutes = 10)
        )
        val conflicts = engine.detect(blocks)
        assertEquals(1, conflicts.size)
        assertEquals(10, conflicts[0].overlapStartMinute)
        assertEquals(20, conflicts[0].overlapEndMinute)
    }

    @Test
    fun `previewMove identifies conflicts for the moved block`() {
        val blocks = listOf(
            timeBlock(id = "1", startMinute = 100, durationMinutes = 100),
            timeBlock(id = "2", startMinute = 300, durationMinutes = 100)
        )
        // Move block 1 to overlap with block 2
        val conflicts = engine.previewMove(blocks, "1", startMinute = 250, durationMinutes = 100)
        assertEquals(1, conflicts.size)
        assertEquals("1", conflicts[0].primaryBlockId)
        assertEquals("2", conflicts[0].conflictingBlockId)
    }

    @Test
    fun `previewMove returns empty if block not found`() {
        val conflicts = engine.previewMove(emptyList(), "invalid", 0, 60)
        assertTrue(conflicts.isEmpty())
    }
}
