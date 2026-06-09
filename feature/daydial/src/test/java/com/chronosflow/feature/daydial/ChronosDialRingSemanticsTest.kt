package com.chronosflow.feature.daydial

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.planner.DialRing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosDialRingSemanticsTest {
    @Test
    fun ringForBlockAssignsCalendarBlocksToOuterRing() {
        val block = block(calendarEventId = 42L)

        assertEquals(DialRing.OUTER, ringForBlock(block))
        assertFalse(block.isInnerRingActionBlock)
    }

    @Test
    fun ringForBlockAssignsTaskHabitAndMedicationBlocksToInnerRing() {
        val taskBlock = block(taskId = "task-1")
        val habitBlock = block(habitId = "habit-1")
        val medicationBlock = block(medicationPlanId = "med-1")

        assertEquals(DialRing.INNER, ringForBlock(taskBlock))
        assertEquals(DialRing.INNER, ringForBlock(habitBlock))
        assertEquals(DialRing.INNER, ringForBlock(medicationBlock))
        assertTrue(taskBlock.isInnerRingActionBlock)
        assertTrue(habitBlock.isInnerRingActionBlock)
        assertTrue(medicationBlock.isInnerRingActionBlock)
    }

    @Test
    fun ringForBlockAssignsStandardPlannerBlocksToMiddleRing() {
        assertEquals(DialRing.MIDDLE, ringForBlock(block()))
    }

    @Test
    fun ringForBlockHonorsQuickCreateCategoriesForCalendarAndRoutineLanes() {
        val calendarHold = block(category = "CALENDAR")
        val routineCheckpoint = block(category = "ROUTINE")

        assertEquals(DialRing.OUTER, ringForBlock(calendarHold))
        assertEquals(DialRing.INNER, ringForBlock(routineCheckpoint))
        assertTrue(routineCheckpoint.isInnerRingActionBlock)
    }

    @Test
    fun quickCreateDefaultsMatchRingIntent() {
        assertEquals("Calendar hold" to "CALENDAR", quickCreateDefaultsForRing(DialRing.OUTER))
        assertEquals("Focus Block" to "WORK", quickCreateDefaultsForRing(DialRing.MIDDLE))
        assertEquals("Routine checkpoint" to "ROUTINE", quickCreateDefaultsForRing(DialRing.INNER))
    }

    @Test
    fun ringGuideOverlayRequiresVisibleGuideAndThreeRingMode() {
        assertTrue(shouldDrawRingGuides(enableThreeRingMode = true, showRingGuide = true))
        assertFalse(shouldDrawRingGuides(enableThreeRingMode = true, showRingGuide = false))
        assertFalse(shouldDrawRingGuides(enableThreeRingMode = false, showRingGuide = true))
    }

    @Test
    fun dialRadiusScaleExpandsVisualAndHitGeometryTogether() {
        assertEquals(400f, scaledDialOuterDiameter(canvasSize = 600f, dialRadiusScale = 1f), 0.001f)
        assertEquals(464f, scaledDialOuterDiameter(canvasSize = 600f, dialRadiusScale = 1.16f), 0.001f)
        assertEquals(232f, scaledDialHitRadius(canvasSize = 600f, dialRadiusScale = 1.16f), 0.001f)
        assertEquals(268f, scaledOuterRingTapRadius(canvasSize = 600f, dialRadiusScale = 1.16f), 0.001f)
    }

    private fun block(
        calendarEventId: Long? = null,
        taskId: String? = null,
        habitId: String? = null,
        medicationPlanId: String? = null,
        category: String = "WORK"
    ): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = "block",
            title = "Block",
            startMinuteOfDay = 9 * 60,
            durationMinutes = 30,
            color = Color.Blue,
            calendarEventId = calendarEventId,
            taskId = taskId,
            habitId = habitId,
            medicationPlanId = medicationPlanId,
            category = category
        )
    }
}
