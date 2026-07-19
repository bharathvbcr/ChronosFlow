package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.domain.planner.DialGeometry
import com.ChronosFlow.VBCR.core.domain.planner.DialRing
import com.ChronosFlow.VBCR.feature.daydial.DialUtils.minuteToAngle
import com.ChronosFlow.VBCR.feature.daydial.ui.dailyDialHubMaxWidth
import com.ChronosFlow.VBCR.feature.daydial.ui.dailyDialHubShowsCategoryPill
import com.ChronosFlow.VBCR.feature.daydial.ui.dailyDialHubShowsSecondaryLines
import com.ChronosFlow.VBCR.feature.daydial.ui.isEmptyDayReview
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
    fun dialRadiusScaleExpandsVisualAndHitGeometryTogether() {
        assertEquals(400f, scaledDialOuterDiameter(canvasSize = 600f, dialRadiusScale = 1f), 0.001f)
        assertEquals(464f, scaledDialOuterDiameter(canvasSize = 600f, dialRadiusScale = 1.16f), 0.001f)
        assertEquals(232f, scaledDialHitRadius(canvasSize = 600f, dialRadiusScale = 1.16f), 0.001f)
        assertEquals(268f, scaledOuterRingTapRadius(canvasSize = 600f, dialRadiusScale = 1.16f), 0.001f)
    }

    @Test
    fun wakeArcsComplementNightWindowOn24HourDial() {
        val wake = buildDialWakeArcs(
            nightStartMinute = 21 * 60,
            nightEndMinute = 7 * 60,
            compactMode = false,
            compactWindowStart = 0
        )
        assertEquals(1, wake.size)
        assertEquals(minuteToAngle(7 * 60), wake.single().startAngle, 0.001f)
        // Night is 10h (150°); wake is the remaining 14h (210°).
        assertEquals(210f, wake.single().sweepAngle, 0.001f)
    }

    @Test
    fun selectedBlockCustomActionsExposeCompleteMoveAndResize() {
        val block = block().copy(startMinuteOfDay = 9 * 60, durationMinutes = 60)
        var completedId: String? = null
        var movedTo: Int? = null
        var resizedDuration: Int? = null
        val actions = buildDialSelectedBlockCustomActions(
            selectedBlock = block,
            geometry = DialGeometry(),
            onComplete = { completedId = it },
            onMoved = { _, minute -> movedTo = minute },
            onMoveCommitted = { _, minute -> movedTo = minute },
            onResize = { _, _, duration -> resizedDuration = duration },
            onResizeCommitted = { _, _, duration -> resizedDuration = duration }
        )
        assertEquals(
            listOf(
                DialA11yActionLabels.COMPLETE,
                DialA11yActionLabels.MOVE_EARLIER,
                DialA11yActionLabels.MOVE_LATER,
                DialA11yActionLabels.RESIZE_SHORTER,
                DialA11yActionLabels.RESIZE_LONGER
            ),
            actions.map { it.label }
        )
        assertTrue(actions[0].action())
        assertEquals("block", completedId)
        assertTrue(actions[1].action())
        assertEquals(9 * 60 - 15, movedTo)
        assertTrue(actions[4].action())
        assertEquals(75, resizedDuration)
    }

    @Test
    fun hubWidthCapsToInnerClearZone() {
        assertEquals(165.dp, dailyDialHubMaxWidth(300.dp))
        assertTrue(dailyDialHubShowsSecondaryLines(fontScale = 1f, availableHeight = 100.dp))
        assertFalse(dailyDialHubShowsSecondaryLines(fontScale = 1.4f, availableHeight = 100.dp))
        assertFalse(dailyDialHubShowsCategoryPill(fontScale = 1.4f, availableHeight = 80.dp))
    }

    @Test
    fun emptyDayReviewCollapsesZeroMetrics() {
        assertTrue(
            isEmptyDayReview(
                DailyReview(plannedMinutes = 0, actualMinutes = 0, missedMinutes = 0, completedBlocks = 0)
            )
        )
        assertFalse(
            isEmptyDayReview(
                DailyReview(plannedMinutes = 30, actualMinutes = 0, missedMinutes = 0, completedBlocks = 0)
            )
        )
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
