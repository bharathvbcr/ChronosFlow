package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.ui.graphics.Color
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.feature.daydial.model.TimeBlockUiModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsTabStateTest {

    @Test
    fun `domain-block category breakdown aggregates across a period`() {
        val rows = insightCategoryBreakdownRowsFromBlocks(
            blocks = listOf(
                domainBlock(id = "task-mon", durationMinutes = 60, taskId = "task-1"),
                domainBlock(id = "task-tue", durationMinutes = 30, taskId = "task-1"),
                domainBlock(id = "habit-wed", durationMinutes = 30, habitId = "habit-1")
            )
        )

        assertEquals("Task", rows[0].category)
        assertEquals(90, rows[0].minutes)
        assertEquals(0.75f, rows[0].share, 0.001f)
        assertEquals(1f, rows[0].progress, 0.001f)
        assertEquals("Habit", rows[1].category)
        assertEquals(30, rows[1].minutes)
    }

    @Test
    fun `category breakdown keeps per-block minutes without saturating progress`() {
        val rows = insightCategoryBreakdownRows(
            timeBlocks = listOf(
                block(id = "task", durationMinutes = 60, taskId = "task-1"),
                block(id = "habit", durationMinutes = 30, habitId = "habit-1")
            )
        )

        assertEquals("Task", rows[0].category)
        assertEquals(60, rows[0].minutes)
        assertEquals(0.667f, rows[0].share, 0.001f)
        assertEquals(1f, rows[0].progress, 0.001f)
        assertEquals("Habit", rows[1].category)
        assertEquals(30, rows[1].minutes)
        assertEquals(0.333f, rows[1].share, 0.001f)
        assertEquals(0.5f, rows[1].progress, 0.001f)
    }

    @Test
    fun `focus concentration tip only appears for risky concentration`() {
        assertEquals(
            "Only one category appears today. Add a short block in another category tomorrow to reduce concentration risk.",
            focusConcentrationTip(1f, "Task", totalCategories = 1)
        )
        assertEquals(
            "Very high focus on Task. Try splitting this category into two shorter focus blocks with a reset task in between.",
            focusConcentrationTip(0.9f, "Task", totalCategories = 2)
        )
        assertEquals(
            "You spent most of your day on Task. A short complementary block could improve context recovery.",
            focusConcentrationTip(0.7f, "Task", totalCategories = 2)
        )
        assertEquals(null, focusConcentrationTip(0.5f, "Task", totalCategories = 3))
    }

    @Test
    fun `category breakdown sorts by total minutes descending`() {
        val rows = insightCategoryBreakdownRows(
            timeBlocks = listOf(
                block(id = "task-1", durationMinutes = 30, taskId = "task-1"),
                block(id = "habit-1", durationMinutes = 45, habitId = "habit-1"),
                block(id = "task-2", durationMinutes = 45, taskId = "task-2"),
                block(id = "calendar", durationMinutes = 20, calendarEventId = 1L)
            )
        )

        assertEquals("Task", rows[0].category)
        assertEquals(75, rows[0].minutes)
        assertEquals("Habit", rows[1].category)
        assertEquals(45, rows[1].minutes)
        assertEquals("Calendar", rows[2].category)
        assertEquals(20, rows[2].minutes)
    }

    @Test
    fun `category breakdown sorts ties alphabetically`() {
        val rows = insightCategoryBreakdownRows(
            timeBlocks = listOf(
                block(id = "habit", durationMinutes = 30, habitId = "habit-1"),
                block(id = "calendar", durationMinutes = 30, calendarEventId = 1L),
                block(id = "task", durationMinutes = 10, taskId = "task-1")
            )
        )

        assertEquals("Calendar", rows[0].category)
        assertEquals(30, rows[0].minutes)
        assertEquals("Habit", rows[1].category)
        assertEquals(30, rows[1].minutes)
        assertEquals("Task", rows[2].category)
        assertEquals(10, rows[2].minutes)
    }

    @Test
    fun `concentration label maps thresholds`() {
        assertEquals("Balanced", focusConcentrationLabel(0.2f))
        assertEquals("Even", focusConcentrationLabel(0.33f))
        assertEquals("Moderate", focusConcentrationLabel(0.5f))
        assertEquals("High", focusConcentrationLabel(0.7f))
    }

    @Test
    fun `plan helper identifies when a plan is set`() {
        assertEquals(false, hasPlannedDayMinutes(0))
        assertEquals(false, hasPlannedDayMinutes(-1))
        assertEquals(true, hasPlannedDayMinutes(30))
        assertEquals(true, hasNoPlannedDayMinutes(0))
        assertEquals(true, hasNoPlannedDayMinutes(-1))
        assertEquals(false, hasNoPlannedDayMinutes(30))
    }

    @Test
    fun `no plan predicate identifies no-plan baseline`() {
        assertEquals(true, hasNoPlannedDayMinutes(0))
        assertEquals(true, hasNoPlannedDayMinutes(-1))
        assertEquals(false, hasNoPlannedDayMinutes(30))
    }

    @Test
    fun `plan checks stay internally consistent`() {
        val sampleMinutes = listOf(-10, 0, 1, 15, 120)
        sampleMinutes.forEach { minutes ->
            assertEquals(!hasPlannedDayMinutes(minutes), hasNoPlannedDayMinutes(minutes))
        }
    }

    @Test
    fun `no-plan predicate is inverse of plan presence predicate`() {
        val sampleMinutes = listOf(-120, 0, 1, 60)
        sampleMinutes.forEach { minutes ->
            assertEquals(!hasPlannedDayMinutes(minutes), hasNoPlannedDayMinutes(minutes))
        }
    }

    @Test
    fun `completion percent helper uses no-plan guard and scales when plan exists`() {
        assertEquals(0, plannedCompletionPercent(0, 80))
        assertEquals(0, plannedCompletionPercent(-10, 80))
        assertEquals(50, plannedCompletionPercent(120, 60))
        assertEquals(100, plannedCompletionPercent(50, 60))
    }

    @Test
    fun `completion percent helper rounds and clamps`() {
        assertEquals(33, plannedCompletionPercent(3, 1))
        assertEquals(0, plannedCompletionPercent(1, -10))
        assertEquals(100, plannedCompletionPercent(2, 3))
    }

    @Test
    fun `completion percent treats negative actual as zero`() {
        assertEquals(0, plannedCompletionPercent(120, -10))
    }

    @Test
    fun `execution score narrative explains missing plan`() {
        assertEquals(
            "No plan is active for today.",
            executionScoreNarrative(0, false)
        )
    }

    @Test
    fun `execution score narrative maps completion levels`() {
        assertEquals("You are ahead of plan.", executionScoreNarrative(99, true))
        assertEquals(
            "Execution is on track.",
            executionScoreNarrative(82, true)
        )
        assertEquals(
            "Execution drifted from plan.",
            executionScoreNarrative(70, true)
        )
        assertEquals(
            "Execution is behind.",
            executionScoreNarrative(40, true)
        )
    }

    @Test
    fun `execution score action maps completion levels to next action`() {
        assertEquals(
            "Create a plan to unlock completion and drift insights.",
            executionScoreAction(0, false)
        )
        assertEquals(
            "Keep your current cadence and preserve block quality.",
            executionScoreAction(99, true)
        )
        assertEquals(
            "Tighten 10-minute estimates on one block to improve forecasting.",
            executionScoreAction(82, true)
        )
        assertEquals(
            "Cap your next work block to reduce variance and recover the plan.",
            executionScoreAction(70, true)
        )
        assertEquals(
            "Prioritize your top two tasks and defer low-value blocks.",
            executionScoreAction(40, true)
        )
    }

    @Test
    fun `plan metric label hides values when plan is missing`() {
        assertEquals("N/A", planMetricLabel(false, "120m"))
        assertEquals("120m", planMetricLabel(true, "120m"))
    }

    @Test
    fun `planned completion percent handles plan and actual edge combinations`() {
        assertEquals(0, plannedCompletionPercent(-1, 10))
        assertEquals(0, plannedCompletionPercent(0, 10))
        assertEquals(100, plannedCompletionPercent(1, 1))
        assertEquals(0, plannedCompletionPercent(1, 0))
    }

    @Test
    fun `no-plan-aware actual metric label shows logged-time fallback`() {
        assertEquals("No logged time yet", noPlanAwareActualMetricLabel(0, false))
        assertEquals("No logged time yet", noPlanAwareActualMetricLabel(-10, false))
        assertEquals("15m", noPlanAwareActualMetricLabel(15, false))
    }

    @Test
    fun `no-plan-aware actual metric label keeps direct format when plan exists`() {
        assertEquals("0m", noPlanAwareActualMetricLabel(-5, true))
        assertEquals("0m", noPlanAwareActualMetricLabel(0, true))
        assertEquals("15m", noPlanAwareActualMetricLabel(15, true))
    }

    @Test
    fun `missed block summary explains no-plan state`() {
        assertEquals(
            "Create one now to unlock completion, drift, and missed-block analysis.",
            missedBlocksSummaryText(false, 7)
        )
        assertEquals("Missed blocks: none", missedBlocksSummaryText(true, 0))
        assertEquals("Missed blocks: 1", missedBlocksSummaryText(true, 1))
        assertEquals("Missed blocks: 7", missedBlocksSummaryText(true, 7))
        assertEquals("Missed blocks: none", missedBlocksSummaryText(true, -1))
    }

    private fun block(
        id: String,
        durationMinutes: Int,
        taskId: String? = null,
        habitId: String? = null,
        calendarEventId: Long? = null
    ): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = id,
            title = id,
            startMinuteOfDay = 9 * 60,
            durationMinutes = durationMinutes,
            color = Color(0xFF6750A4),
            taskId = taskId,
            habitId = habitId,
            calendarEventId = calendarEventId
        )
    }

    private fun domainBlock(
        id: String,
        durationMinutes: Int,
        taskId: String? = null,
        habitId: String? = null,
        calendarEventId: Long? = null
    ): TimeBlock = TimeBlock(
        id = id,
        date = LocalDate.of(2026, 6, 10),
        title = id,
        category = "WORK",
        startMinuteOfDay = 9 * 60,
        durationMinutes = durationMinutes,
        timezone = "UTC",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.MOVABLE,
        energyLevel = EnergyIntensity.MODERATE,
        source = "USER_CREATED",
        taskId = taskId,
        calendarEventId = calendarEventId,
        medicationPlanId = null,
        habitId = habitId,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.parse("2026-06-10T08:00:00Z"),
        updatedAt = Instant.parse("2026-06-10T08:00:00Z")
    )
}
