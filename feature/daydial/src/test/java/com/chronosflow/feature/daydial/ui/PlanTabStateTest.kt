package com.chronosflow.feature.daydial.ui

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlanTabStateTest {
    @Test
    fun `plan date strip centers nearby days around the selected date`() {
        val selectedDate = LocalDate.of(2026, 5, 27)

        val dates = planDateScrollDates(selectedDate, radiusDays = 2)

        assertEquals(
            listOf(
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 5, 27),
                LocalDate.of(2026, 5, 28),
                LocalDate.of(2026, 5, 29)
            ),
            dates
        )
    }

    @Test
    fun `plan date strip initially scrolls near the selected day`() {
        val selectedDate = LocalDate.of(2026, 5, 27)
        val dates = planDateScrollDates(selectedDate, radiusDays = 7)

        assertEquals(5, planDateInitialFirstVisibleIndex(dates, selectedDate))
    }

    @Test
    fun `plan date strip starts at beginning when selected day is absent`() {
        val selectedDate = LocalDate.of(2026, 5, 27)
        val dates = listOf(selectedDate.minusDays(2), selectedDate.minusDays(1))

        assertEquals(0, planDateInitialFirstVisibleIndex(dates, selectedDate))
    }

    @Test
    fun `plan date strip labels name weekday date and selected state`() {
        val selectedDate = LocalDate.of(2026, 5, 27)

        assertEquals("Wed", planDateChipWeekdayLabel(selectedDate))
        assertEquals("May 27", planDateChipDateLabel(selectedDate))
        assertEquals(
            "Selected Wednesday, May 27, 2026",
            planDateChipActionLabel(selectedDate, isSelected = true)
        )
        assertEquals(
            "Select Thursday, May 28, 2026",
            planDateChipActionLabel(selectedDate.plusDays(1), isSelected = false)
        )
    }

    @Test
    fun `full plan calendar month grid includes leading and trailing week days`() {
        val selectedDate = LocalDate.of(2026, 5, 27)

        val dates = planCalendarMonthGridDates(selectedDate)

        assertEquals(LocalDate.of(2026, 4, 26), dates.first())
        assertEquals(LocalDate.of(2026, 6, 6), dates.last())
        assertEquals(42, dates.size)
    }

    @Test
    fun `full plan calendar action labels describe navigation and selected day`() {
        val selectedDate = LocalDate.of(2026, 5, 27)

        assertEquals("Show full planning calendar", planCalendarToggleActionLabel(fullCalendarVisible = false))
        assertEquals("Hide full planning calendar", planCalendarToggleActionLabel(fullCalendarVisible = true))
        assertEquals(
            "Sync device calendar events into this plan",
            planCalendarSyncActionLabel(calendarReadGranted = true)
        )
        assertEquals(
            "Connect your device calendar to sync events into this plan",
            planCalendarSyncActionLabel(calendarReadGranted = false)
        )
        assertEquals(
            "Syncing device calendar events",
            planCalendarSyncActionLabel(calendarReadGranted = true, syncInProgress = true)
        )
        assertEquals("Go to April 2026", planCalendarMonthNavigationLabel(selectedDate, monthOffset = -1))
        assertEquals("Go to June 2026", planCalendarMonthNavigationLabel(selectedDate, monthOffset = 1))
        assertEquals(
            "Selected Wednesday, May 27, 2026",
            planCalendarDayActionLabel(selectedDate, isSelected = true)
        )
        assertEquals(
            "Select Thursday, May 28, 2026",
            planCalendarDayActionLabel(selectedDate.plusDays(1), isSelected = false)
        )
    }

    @Test
    fun `full plan calendar keeps all-day imports visible as non blocking agenda markers`() {
        val allDayImport = block(
            id = "festival",
            startMinute = 0,
            durationMinutes = 24 * 60,
            category = ALL_DAY_CALENDAR_EVENT_CATEGORY,
            provenance = BlockProvenance.CALENDAR_IMPORTED.name,
            calendarEventId = 42L
        )

        assertEquals("All-day calendar", planCalendarSourceLabel(allDayImport))
        assertEquals(0, planCalendarSortMinute(allDayImport))
    }

    @Test
    fun `large gap detection ignores slack outside planned blocks`() {
        val blocks = listOf(
            block(id = "morning", startMinute = 9 * 60, durationMinutes = 60),
            block(id = "midday", startMinute = 11 * 60, durationMinutes = 60)
        )

        val gaps = findLargeGaps(blocks, thresholdMinutes = 45)

        assertEquals(listOf(10 * 60 to 11 * 60), gaps.map { it.startMinute to it.endMinute })
    }

    @Test
    fun `single planned block has no actionable large gaps`() {
        val gaps = findLargeGaps(
            blocks = listOf(block(id = "focus", startMinute = 9 * 60, durationMinutes = 60)),
            thresholdMinutes = 45
        )

        assertEquals(emptyList<Pair<Int, Int>>(), gaps.map { it.startMinute to it.endMinute })
    }

    @Test
    fun `plan primary action labels describe the planning command`() {
        assertEquals("Generate a balanced plan with AI", planGenerateActionLabel())
        assertEquals("Rebalance today's plan", planRebalanceActionLabel())
        assertEquals("Add a block manually", planCreateBlockActionLabel())
    }

    @Test
    fun `plan schedule attention action copy reflects gaps and overlaps`() {
        assertEquals("Fill gaps with tasks", planScheduleAttentionButtonText(largeGapCount = 2, overlapCount = 0))
        assertEquals("Fix schedule", planScheduleAttentionButtonText(largeGapCount = 0, overlapCount = 1))
        assertEquals("Fix schedule", planScheduleAttentionButtonText(largeGapCount = 1, overlapCount = 2))
        assertEquals(
            "Fix 2 large gaps in today's schedule",
            planScheduleAttentionActionLabel(largeGapCount = 2, overlapCount = 0)
        )
        assertEquals(
            "Fix 1 overlap in today's schedule",
            planScheduleAttentionActionLabel(largeGapCount = 0, overlapCount = 1)
        )
        assertEquals(
            "Fix 1 large gap and 2 overlaps in today's schedule",
            planScheduleAttentionActionLabel(largeGapCount = 1, overlapCount = 2)
        )
    }

    @Test
    fun `plan suggestion footer action labels name the suggestion count`() {
        assertEquals("Apply 1 AI suggestion to today's plan", planSuggestionApplyAllActionLabel(1))
        assertEquals("Apply 3 AI suggestions to today's plan", planSuggestionApplyAllActionLabel(3))
    }

    @Test
    fun `plan timeline block action label names the time range`() {
        val block = block(
            id = "focus",
            startMinute = 9 * 60 + 15,
            durationMinutes = 45
        )

        assertEquals(
            "Open focus from 9:15 AM to 10:00 AM",
            planTimelineBlockActionLabel(block)
        )
    }

    @Test
    fun `plan timeline secondary action labels name duplicate and delete target`() {
        val block = block(
            id = "focus",
            startMinute = 9 * 60,
            durationMinutes = 60
        )

        assertEquals("Duplicate focus block", planDuplicateBlockActionLabel(block))
        assertEquals("Delete focus block", planDeleteBlockActionLabel(block))
    }

    private fun block(
        id: String,
        startMinute: Int,
        durationMinutes: Int,
        category: String = "WORK",
        provenance: String = "",
        calendarEventId: Long? = null
    ): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = id,
            title = id,
            category = category,
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            color = Color(0xFF6750A4),
            provenance = provenance,
            calendarEventId = calendarEventId
        )
    }
}
