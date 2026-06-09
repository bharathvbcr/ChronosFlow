package com.chronosflow.core.domain.planner

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PlannerModelsTest {
    @Test
    fun `zoom window wraps minutes within day`() {
        val window = ZoomWindow(startMinute = 1320, durationMinutes = 180)

        assertEquals(60, window.endMinute)
        assertEquals(0, window.wrapMinute(1440))
        assertEquals(59, window.wrapMinute(59))
        assertEquals(1439, window.wrapMinute(-1))
    }

    @Test
    fun `zoom window contains minute handles wrap windows`() {
        val window = ZoomWindow(startMinute = 1320, durationMinutes = 180)

        assertTrue(window.containsMinute(1320))
        assertTrue(window.containsMinute(0))
        assertFalse(window.containsMinute(1300))
    }

    @Test
    fun `time block move permission reflects flexibility and lock state`() {
        val movable = TimeBlock(
            id = "block-a",
            date = LocalDate.of(2026, 5, 31),
            title = "Focus",
            category = "Work",
            startMinuteOfDay = 540,
            durationMinutes = 30,
            timezone = "UTC",
            provenance = com.chronosflow.core.domain.model.BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = com.chronosflow.core.domain.model.EnergyIntensity.MODERATE,
            source = "user",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = Instant.parse("2026-05-31T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-31T08:00:00Z")
        )
        val fixed = movable.copy(
            flexibility = BlockFlexibility.FIXED
        )
        val locked = movable.copy(
            flexibility = BlockFlexibility.RESIZABLE,
            isLocked = true
        )

        assertTrue(movable.isMoveAllowed())
        assertFalse(fixed.isMoveAllowed())
        assertFalse(locked.isMoveAllowed())
    }

    @Test
    fun `planner feedback stores type and optional message`() {
        val feedback = PlannerFeedback(
            type = PlannerFeedbackType.SNAP_TO_INCREMENT,
            blockId = "block-a",
            message = "Snapped"
        )

        assertEquals(PlannerFeedbackType.SNAP_TO_INCREMENT, feedback.type)
        assertEquals("block-a", feedback.blockId)
        assertEquals("Snapped", feedback.message)
    }

    @Test
    fun `planner command result types expose undo states`() {
        val applied = PlannerOperationResult.Applied("ok", "block-a", 10, listOf("a", "b"))
        val conflict = PlannerOperationResult.Conflict("conflict", "block-a", listOf("x"))
        val locked = PlannerOperationResult.Locked("locked", "block-a")
        val rejected = PlannerOperationResult.Rejected("rejected", "block-a")

        assertTrue(applied.canUndo)
        assertFalse(conflict.canUndo)
        assertFalse(locked.canUndo)
        assertFalse(rejected.canUndo)
        assertEquals("x", conflict.conflicts.single())
    }
}
