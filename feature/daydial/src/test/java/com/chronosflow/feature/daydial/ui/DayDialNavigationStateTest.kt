package com.chronosflow.feature.daydial.ui

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.model.AppLaunchTarget
import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import com.chronosflow.feature.daydial.model.DayQuickContextActionUiModel
import com.chronosflow.feature.daydial.model.DayQuickItemKind
import com.chronosflow.feature.daydial.model.DayQuickItemUiModel
import com.chronosflow.feature.daydial.model.DayQuickItemsUiState
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import com.chronosflow.feature.daydial.model.TemplateBlueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayDialNavigationStateTest {
    @Test
    fun `date header label includes weekday and date`() {
        assertEquals(
            "Wed, May 27",
            dateNavHeaderLabel(LocalDate.of(2026, 5, 27))
        )
    }

    @Test
    fun `date navigation action label names target day`() {
        val selectedDate = LocalDate.of(2026, 5, 27)

        assertEquals("Go to May 26", dayNavigationActionLabel(selectedDate, dayOffset = -1))
        assertEquals("Go to May 28", dayNavigationActionLabel(selectedDate, dayOffset = 1))
    }

    @Test
    fun `calendar month navigation label names target month`() {
        val selectedDate = LocalDate.of(2026, 5, 27)

        assertEquals("Go to April 2026", calendarMonthNavigationLabel(selectedDate, monthOffset = -1))
        assertEquals("Go to June 2026", calendarMonthNavigationLabel(selectedDate, monthOffset = 1))
    }

    @Test
    fun `calendar day label includes selected state and full date`() {
        assertEquals(
            "Selected Wednesday, May 27, 2026",
            calendarDaySelectionLabel(LocalDate.of(2026, 5, 27), isSelected = true)
        )
        assertEquals(
            "Select Thursday, May 28, 2026",
            calendarDaySelectionLabel(LocalDate.of(2026, 5, 28), isSelected = false)
        )
    }

    @Test
    fun `template action labels name the affected template`() {
        val template = TemplateBlueprint(
            id = "morning-deep-work",
            name = "Morning deep work",
            blocks = emptyList()
        )

        assertEquals("Apply Morning deep work template", templateApplyActionLabel(template))
        assertEquals("Edit Morning deep work template", templateEditActionLabel(template))
        assertEquals("Copy Morning deep work template", templateCopyActionLabel(template))
    }

    @Test
    fun `plan template chip label explains the apply action`() {
        val template = TemplateBlueprint(
            id = "meeting-light-day",
            name = "Meeting light day",
            blocks = emptyList()
        )

        assertEquals("Apply Meeting light day to current plan", planTemplateChipActionLabel(template))
    }

    @Test
    fun `plan suggestion action labels name the suggested block and scheduled time`() {
        val suggestion = TimeBlockUiModel(
            id = "suggestion-1",
            title = "Focus writing",
            startMinuteOfDay = 9 * 60,
            durationMinutes = 90,
            color = Color.Blue
        )

        assertEquals(
            "Accept Focus writing suggestion from 9:00 AM for 90 minutes",
            planSuggestionAcceptActionLabel(suggestion)
        )
        assertEquals(
            "Reject Focus writing suggestion from 9:00 AM for 90 minutes",
            planSuggestionRejectActionLabel(suggestion)
        )
    }

    @Test
    fun `plan suggestion time text names start and end time`() {
        val suggestion = TimeBlockUiModel(
            id = "suggestion-2",
            title = "Review metrics",
            startMinuteOfDay = 14 * 60 + 15,
            durationMinutes = 45,
            color = Color.Blue
        )

        assertEquals(
            "2:15 PM - 3:00 PM · 45m",
            planSuggestionTimeText(suggestion)
        )
    }

    @Test
    fun `review metric action labels name the detail destination`() {
        assertEquals("Open Planned breakdown", reviewMetricActionLabel("Planned"))
        assertEquals("Open Actual log", reviewMetricActionLabel("Actual"))
        assertEquals("Open Missed recovery", reviewMetricActionLabel("Missed"))
    }

    @Test
    fun `today block action labels name the affected block`() {
        val block = TimeBlockUiModel(
            id = "block-1",
            title = "Draft proposal",
            startMinuteOfDay = 10 * 60,
            durationMinutes = 45,
            color = Color.Blue
        )

        assertEquals("Start focus for Draft proposal", todayStartFocusActionLabel(block))
        assertEquals("Complete Draft proposal", todayCompleteBlockActionLabel(block))
    }

    @Test
    fun `today missed block action label names the affected block`() {
        val block = TimeBlockUiModel(
            id = "block-1",
            title = "Draft proposal",
            startMinuteOfDay = 10 * 60,
            durationMinutes = 45,
            color = Color.Blue
        )

        assertEquals("Undo missed mark for Draft proposal", todayUndoMissedActionLabel(block))
    }

    @Test
    fun `today next block action label names the upcoming block`() {
        val block = TimeBlockUiModel(
            id = "block-2",
            title = "Review launch plan",
            startMinuteOfDay = 11 * 60,
            durationMinutes = 30,
            color = Color.Blue
        )

        assertEquals("Open next block Review launch plan", todayNextBlockActionLabel(block))
    }

    @Test
    fun `today context action for block resolves task and habit quick actions`() {
        val taskAction = DayQuickContextActionUiModel(
            label = "Call",
            contentDescription = "Call for Draft proposal",
            appLaunchTarget = AppLaunchTarget(label = "Phone", value = "tel:5550100")
        )
        val habitAction = DayQuickContextActionUiModel(
            label = "Open app",
            contentDescription = "Open app for Evening walk",
            appLaunchTarget = AppLaunchTarget(label = "Fitness", value = "com.example.fitness")
        )
        val quickItems = DayQuickItemsUiState(
            tasks = listOf(
                DayQuickItemUiModel(
                    id = "task-1",
                    kind = DayQuickItemKind.TASK,
                    title = "Draft proposal",
                    detail = "Task",
                    status = "Due today",
                    isDone = false,
                    contextAction = taskAction
                )
            ),
            habits = listOf(
                DayQuickItemUiModel(
                    id = "habit-1",
                    kind = DayQuickItemKind.HABIT,
                    title = "Evening walk",
                    detail = "Habit",
                    status = "Due today",
                    isDone = false,
                    contextAction = habitAction
                )
            )
        )

        assertEquals(
            taskAction,
            todayContextActionForBlock(
                block = TimeBlockUiModel(
                    id = "block-task",
                    title = "Draft proposal",
                    startMinuteOfDay = 10 * 60,
                    durationMinutes = 45,
                    color = Color.Blue,
                    taskId = "task-1"
                ),
                quickItems = quickItems
            )
        )
        assertEquals(
            habitAction,
            todayContextActionForBlock(
                block = TimeBlockUiModel(
                    id = "block-habit",
                    title = "Evening walk",
                    startMinuteOfDay = 18 * 60,
                    durationMinutes = 30,
                    color = Color.Green,
                    habitId = "habit-1"
                ),
                quickItems = quickItems
            )
        )
        assertNull(
            todayContextActionForBlock(
                block = TimeBlockUiModel(
                    id = "block-calendar",
                    title = "Planning sync",
                    startMinuteOfDay = 13 * 60,
                    durationMinutes = 30,
                    color = Color.Gray
                ),
                quickItems = quickItems
            )
        )
    }

    @Test
    fun `today open time action labels name the time window`() {
        assertEquals("Add block at 10:15 AM", todayOpenTimeAddBlockActionLabel(10 * 60 + 15))
        assertEquals(
            "Fill 45 minute gap until 11:00 AM",
            todayOpenTimeFillGapActionLabel(nextStartMinute = 11 * 60, minutesUntilNext = 45)
        )
    }

    @Test
    fun `today open time summary names next block window`() {
        assertEquals(
            "Free until 11:00 AM · 45m",
            todayOpenTimeSummaryLabel(
                nextStartMinute = 11 * 60,
                currentMinute = 10 * 60 + 15,
                openWindowEndMinute = null
            )
        )
    }

    @Test
    fun `dial zoom window label spans twelve hours and wraps midnight`() {
        assertEquals("6:00 AM – 6:00 PM", dialZoomWindowLabel(6 * 60))
        assertEquals("8:00 PM – 8:00 AM", dialZoomWindowLabel(20 * 60))
    }

    @Test
    fun `dial zoom now shortcut appears only when now leaves the window`() {
        assertTrue(dialZoomMinuteInWindow(minute = 9 * 60, windowStart = 6 * 60))
        assertFalse(dialZoomMinuteInWindow(minute = 19 * 60, windowStart = 6 * 60))
        assertTrue(dialZoomMinuteInWindow(minute = 2 * 60, windowStart = 20 * 60))
    }

    @Test
    fun `today open time summary names rest of day without a next block`() {
        assertEquals(
            "Free for the rest of the day · 3h 45m",
            todayOpenTimeSummaryLabel(
                nextStartMinute = null,
                currentMinute = 20 * 60 + 15,
                openWindowEndMinute = DAY_END_MINUTE
            )
        )
    }

    @Test
    fun `today open time summary avoids zero minute fallback when no window is available`() {
        assertEquals(
            "Free for the rest of the day",
            todayOpenTimeSummaryLabel(
                nextStartMinute = null,
                currentMinute = 20 * 60 + 15,
                openWindowEndMinute = null
            )
        )
    }

    @Test
    fun `developer feature flag rows expose all module toggles`() {
        val rows = developerFeatureFlagRows(
            ChronosFeatureFlags(
                habitsEnabled = true,
                medicationEnabled = false,
                reviewEnabled = true,
                aiAdvisorEnabled = true
            )
        )

        assertEquals(
            listOf(
                DeveloperFeatureFlagRow(DeveloperFeatureFlag.HABITS, "Habits page", true),
                DeveloperFeatureFlagRow(DeveloperFeatureFlag.GOALS, "Goals page", true),
                DeveloperFeatureFlagRow(DeveloperFeatureFlag.MEDICATION, "Meds page", false),
                DeveloperFeatureFlagRow(DeveloperFeatureFlag.REVIEW, "Review page", true),
                DeveloperFeatureFlagRow(DeveloperFeatureFlag.JOURNAL, "Journal entry", true),
                DeveloperFeatureFlagRow(DeveloperFeatureFlag.SLEEP, "Sleep log", true),
                DeveloperFeatureFlagRow(DeveloperFeatureFlag.AI_ADVISOR, "AI advisor", true)
            ),
            rows
        )
    }

    @Test
    fun `ai settings primary action label explains disabled advisor`() {
        assertEquals("Generate plan", aiSettingsGeneratePlanActionLabel(aiAdvisorEnabled = true))
        assertEquals("AI advisor disabled", aiSettingsGeneratePlanActionLabel(aiAdvisorEnabled = false))
    }
}
