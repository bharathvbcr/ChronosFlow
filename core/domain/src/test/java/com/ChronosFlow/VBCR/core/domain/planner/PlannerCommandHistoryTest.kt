package com.ChronosFlow.VBCR.core.domain.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import java.time.Instant
import java.time.LocalDate

class PlannerCommandHistoryTest {

    @Test
    fun `initial history has no undo or redo`() {
        val history = PlannerCommandHistory()

        assertEquals(false, history.canUndo.value)
        assertEquals(false, history.canRedo.value)
    }

    @Test
    fun `push command enables undo and clears redo`() {
        val history = PlannerCommandHistory()
        history.push(stubCommand("one"))
        history.push(stubCommand("two"))

        assertEquals(true, history.canUndo.value)
        assertEquals(false, history.canRedo.value)

        val undo = history.popUndo()
        assertEquals("two", undo?.id)
        assertEquals(true, history.canUndo.value)
        assertEquals(true, history.canRedo.value)
    }

    @Test
    fun `undo then redo restores command state`() {
        val history = PlannerCommandHistory()
        history.push(stubCommand("first"))
        val undone = history.popUndo()

        assertEquals("first", undone?.id)
        assertEquals(false, history.canUndo.value)
        assertEquals(true, history.canRedo.value)

        val redone = history.popRedo()
        assertEquals("first", redone?.id)
        assertEquals(true, history.canUndo.value)
        assertEquals(false, history.canRedo.value)
    }

    @Test
    fun `popping empty stacks returns null and keeps flags false`() {
        val history = PlannerCommandHistory()

        assertNull(history.popUndo())
        assertNull(history.popRedo())
        assertEquals(false, history.canUndo.value)
        assertEquals(false, history.canRedo.value)
    }

    @Test
    fun `clear empties both stacks and disables undo redo`() {
        val history = PlannerCommandHistory()
        history.push(stubCommand("first"))
        history.push(stubCommand("second"))
        history.popUndo()
        assertEquals(true, history.canRedo.value)

        history.clear()

        assertEquals(false, history.canUndo.value)
        assertEquals(false, history.canRedo.value)
    }

    private fun stubCommand(id: String): PlannerCommand {
        val now = Instant.now()
        val block = TimeBlock(
            id = id,
            date = LocalDate.of(2026, 5, 30),
            title = "Test block",
            category = "Work",
            startMinuteOfDay = 60,
            durationMinutes = 30,
            timezone = "UTC",
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.FIXED,
            energyLevel = EnergyIntensity.MODERATE,
            source = "USER",
            taskId = null,
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
        return CreateTimeBlockCommand(id = id, block = block)
    }
}
