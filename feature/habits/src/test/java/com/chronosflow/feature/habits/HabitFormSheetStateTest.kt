package com.chronosflow.feature.habits

import com.chronosflow.core.ai.HabitAssistSuggestion
import com.chronosflow.core.ai.RoutineAssistSource
import com.chronosflow.core.domain.model.Habit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitFormSheetStateTest {

    @Test
    fun completeTodayActionLabelTargetsBlankTitleAsGenericHabit() {
        assertEquals(
            "Complete this habit today",
            habitFormCompleteTodayActionLabel("")
        )
        assertEquals(
            "Complete Morning walk today",
            habitFormCompleteTodayActionLabel("  Morning walk ")
        )
    }

    @Test
    fun shouldAutoRequestHabitAssistForCaptureUsesTemplateSignalsAndLengthFilters() {
        assertFalse(shouldAutoRequestHabitAssistForCapture("run"))
        assertFalse(shouldAutoRequestHabitAssistForCapture("  add "))
        assertTrue(shouldAutoRequestHabitAssistForCapture("Run 10k each morning with a plan"))
        assertTrue(shouldAutoRequestHabitAssistForCapture("open app"))
    }

    @Test
    fun habitEditableSummaryCardContentDescriptionBuildsReadableState() {
        assertEquals(
            "Set window for Read. Current value: Not set. Expanded",
            habitEditableSummaryCardContentDescription(
                title = "Read",
                value = "",
                actionLabel = "Set window",
                expanded = true
            )
        )
    }

    @Test
    fun applyingHabitSuggestionSupersedesSameKindAlternativesOnly() {
        val daily = HabitAssistSuggestion.Recurrence(
            id = "recurrence:daily",
            label = "Daily",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            cadence = "Daily"
        )
        val weekly = daily.copy(id = "recurrence:weekly", label = "Weekly", cadence = "Weekly")
        val window = HabitAssistSuggestion.Window(
            id = "window:morning",
            label = "Morning",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            startMinute = 8 * 60,
            endMinute = 9 * 60
        )
        val suggestions = listOf(daily, weekly, window)

        assertEquals(
            setOf("recurrence:daily", "recurrence:weekly"),
            supersededHabitAssistSuggestionIds(daily, suggestions)
        )
        assertEquals(
            setOf("window:morning"),
            supersededHabitAssistSuggestionIds(window, suggestions)
        )
    }

    @Test
    fun untouchedWindowDefaultFollowsHabitContext() {
        assertEquals(
            6 * 60 to 10 * 60,
            contextualHabitDefaultWindow("Morning run", emptyList())
        )
        assertEquals(
            18 * 60 to 22 * 60,
            contextualHabitDefaultWindow("Read 20 pages", emptyList())
        )
        assertNull(contextualHabitDefaultWindow("Tidy desk", emptyList()))
        assertEquals(
            12 * 60 to 13 * 60,
            contextualHabitDefaultWindow(
                "Tidy desk",
                listOf(
                    HabitAssistSuggestion.Window(
                        id = "window:suggested",
                        label = "Midday",
                        reason = "Test",
                        source = RoutineAssistSource.LOCAL,
                        startMinute = 12 * 60,
                        endMinute = 13 * 60
                    )
                )
            )
        )
    }

    @Test
    fun autoApplyPicksFirstSuggestionPerKindOnlyForUntouchedFields() {
        val title = HabitAssistSuggestion.Title(
            id = "title:a",
            label = "Morning run",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            title = "Morning run"
        )
        val recurrence = HabitAssistSuggestion.Recurrence(
            id = "recurrence:a",
            label = "3x / week",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            cadence = "3x / week"
        )
        val secondRecurrence = recurrence.copy(id = "recurrence:b", cadence = "Daily")
        val window = HabitAssistSuggestion.Window(
            id = "window:a",
            label = "Morning",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            startMinute = 8 * 60,
            endMinute = 9 * 60
        )

        val ids = autoApplicableHabitAssistSuggestionIds(
            suggestions = listOf(title, recurrence, secondRecurrence, window),
            titleBlank = true,
            recurrenceUntouched = true,
            windowUntouched = false,
            difficultyUntouched = true,
            dayPlanUntouched = true
        )

        assertEquals(setOf("title:a", "recurrence:a"), ids)
    }

    @Test
    fun habitSuggestionsAlreadySatisfiedAreReportedRedundant() {
        val matchingWindow = HabitAssistSuggestion.Window(
            id = "window:match",
            label = "Morning",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            startMinute = 8 * 60,
            endMinute = 9 * 60
        )
        val matchingRecurrence = HabitAssistSuggestion.Recurrence(
            id = "recurrence:match",
            label = "Daily",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            cadence = "Daily"
        )
        val freshDifficulty = HabitAssistSuggestion.Difficulty(
            id = "difficulty:new",
            label = "Hard",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            difficulty = 4
        )

        val redundant = redundantHabitAssistSuggestionIds(
            suggestions = listOf(matchingWindow, matchingRecurrence, freshDifficulty),
            currentTitle = "Morning run",
            currentRecurrencePreset = "Daily",
            currentStartMinute = 8 * 60,
            currentEndMinute = 9 * 60,
            currentDifficulty = 2,
            currentIsBundled = false
        )

        assertEquals(setOf("window:match", "recurrence:match"), redundant)
    }

    @Test
    fun contextualHabitRecurrenceOptionsPrioritizesCapturedAndSuggestedValues() {
        val options = contextualHabitRecurrenceOptions(
            selectedPreset = "Weekly",
            title = "run every week twice",
            suggestions = listOf(
                HabitAssistSuggestion.Recurrence(
                    id = "local:habit:recurrence:1",
                    label = "Three times per week",
                    reason = "local",
                    source = RoutineAssistSource.LOCAL,
                    cadence = "3x / week"
                )
            )
        )

        assertEquals("3x / week", options[0])
        assertEquals("2x / week", options[1])
        assertEquals("Weekly", options[2])
    }

    @Test
    fun contextualHabitRecurrenceOptionsFallsBackToDefaultsWithoutContext() {
        val options = contextualHabitRecurrenceOptions(
            selectedPreset = "custom",
            title = "random thought",
            suggestions = emptyList()
        )

        assertEquals("Daily", options[0])
        assertEquals("Weekdays", options[1])
        assertEquals("Weekends", options[2])
    }

    @Test
    fun contextualHabitWindowOptionsPrioritizesCapturedAndSuggestedWindows() {
        val options = contextualHabitWindowOptions(
            selectedPreset = "Evening",
            title = "morning run",
            suggestions = listOf(
                HabitAssistSuggestion.Window(
                    id = "local:habit:window:1",
                    label = "Midday",
                    reason = "local",
                    source = RoutineAssistSource.LOCAL,
                    startMinute = 12 * 60,
                    endMinute = 13 * 60
                )
            )
        )

        assertEquals("Custom", options[0])
        assertEquals("Morning", options[1])
        assertEquals("Evening", options[2])
    }

    @Test
    fun contextualHabitDifficultyOptionsRespectsSuggestionAndContextThenDefaults() {
        val options = contextualHabitDifficultyOptions(
            title = "walk and stretch",
            suggestions = listOf(
                HabitAssistSuggestion.Difficulty(
                    id = "local:habit:difficulty:1",
                    label = "Set effort",
                    reason = "local",
                    source = RoutineAssistSource.LOCAL,
                    difficulty = 5
                )
            )
        )

        assertEquals("Very hard", options[0])
        assertEquals("Easy", options[1])
        assertEquals("Very easy", options[2])
    }

    @Test
    fun contextualHabitDifficultyOptionsFallsBackToFullScaleWithoutContext() {
        val options = contextualHabitDifficultyOptions(
            title = "",
            suggestions = emptyList()
        )

        assertEquals(
            listOf("Very easy", "Easy", "Moderate", "Hard", "Very hard"),
            options
        )
    }

    @Test
    fun contextualHabitDayPlanOptionsRespectsSuggestionContextAndCurrentSelection() {
        val optionsWithSuggestion = contextualHabitDayPlanOptions(
            title = "read before bed",
            isBundled = false,
            suggestions = listOf(
                HabitAssistSuggestion.DayPlan(
                    id = "local:habit:dayplan:1",
                    label = "Show on day plan",
                    reason = "local",
                    source = RoutineAssistSource.LOCAL,
                    isBundled = true
                )
            )
        )
        assertEquals(listOf("Show on day plan"), optionsWithSuggestion)

        val optionsWithoutContext = contextualHabitDayPlanOptions(
            title = "",
            isBundled = true,
            suggestions = emptyList()
        )
        assertEquals(listOf("Show on day plan", "Keep flexible"), optionsWithoutContext)
    }

    @Test
    fun habitDayPlanOptionLabelAndValueConvertConsistently() {
        assertEquals("Show on day plan", habitDayPlanOptionLabel(true))
        assertEquals("Keep flexible", habitDayPlanOptionLabel(false))
        assertEquals(true, habitDayPlanOptionValue("Show on day plan"))
        assertEquals(false, habitDayPlanOptionValue("Keep flexible"))
        assertEquals(false, habitDayPlanOptionValue("Unknown"))
    }

    @Test
    fun habitLaunchCaptureSuggestionUsesExplicitValueAndBuildsLabel() {
        val suggestion = habitLaunchCaptureSuggestion("Track my run on spotify://playlist")

        assertEquals("spotify://playlist", suggestion?.value)
        assertEquals("Open Track my run on", suggestion?.label)
    }

    @Test
    fun habitLaunchCaptureSuggestionFallsBackToInferredAppHint() {
        val suggestion = habitLaunchCaptureSuggestion("Track calories with myfitnesspal")

        assertNotNull(suggestion)
        assertEquals("Open MyFitnessPal", suggestion?.label)
        assertEquals("com.myfitnesspal.android", suggestion?.value)
    }

    @Test
    fun prioritizeContextualHabitHistoryTemplatesRanksContextMatchesAheadOfRecency() {
        val templates = listOf(
            HabitHistoryTemplate(
                id = "walk-id",
                title = "Morning walk",
                cadence = "Daily",
                startMinute = 8 * 60,
                endMinute = 9 * 60,
                difficulty = 2,
                isBundled = false,
                isArchived = false
            ),
            HabitHistoryTemplate(
                id = "read-id",
                title = "Read",
                cadence = "Weekdays",
                startMinute = 8 * 60,
                endMinute = 9 * 60,
                difficulty = 2,
                isBundled = false,
                isArchived = false
            ),
            HabitHistoryTemplate(
                id = "workout-id",
                title = "Workout",
                cadence = "Daily",
                startMinute = 18 * 60,
                endMinute = 19 * 60,
                difficulty = 4,
                isBundled = true,
                isArchived = false
            )
        )

        val prioritized = prioritizeContextualHabitHistoryTemplates(
            templates = templates,
            recentIds = listOf("read-id"),
            title = "walk and workout",
            suggestions = emptyList()
        ).map(HabitHistoryTemplate::id)

        assertEquals(listOf("walk-id", "workout-id", "read-id"), prioritized)
    }

    @Test
    fun buildHabitHistoryTemplatesFiltersCurrentHabitAndRemovesDuplicates() {
        val templates = buildHabitHistoryTemplates(
            habits = listOf(
                habit(
                    id = "h-current",
                    title = "Morning walk",
                    lastCompletedDate = null,
                    isActive = true,
                    windowStartMinute = 8 * 60,
                    windowEndMinute = 9 * 60
                ),
                habit(
                    id = "h-active-1",
                    title = "Workout",
                    lastCompletedDate = null,
                    isActive = true,
                    windowStartMinute = 8 * 60,
                    windowEndMinute = 9 * 60
                ),
                habit(
                    id = "h-active-2",
                    title = "Workout",
                    lastCompletedDate = null,
                    isActive = true,
                    windowStartMinute = 8 * 60,
                    windowEndMinute = 9 * 60
                ),
                habit(
                    id = "h-archived",
                    title = "Read",
                    lastCompletedDate = null,
                    isActive = false,
                    windowStartMinute = 10 * 60,
                    windowEndMinute = 11 * 60
                )
            ),
            currentHabitId = "h-current"
        )

        assertEquals(2, templates.size)
        assertEquals(
            "h-archived",
            templates[0].id
        )
        assertEquals("h-active-1", templates[1].id)
        assertFalse(templates.any { it.id == "h-current" })
    }

    @Test
    fun filterHabitHistoryTemplatesSupportsMultiTermCaseInsensitiveMatches() {
        val templates = listOf(
            HabitHistoryTemplate(
                id = "h-1",
                title = "Morning walk",
                cadence = "Daily",
                startMinute = 8 * 60,
                endMinute = 9 * 60,
                difficulty = 2,
                isBundled = false,
                isArchived = false
            ),
            HabitHistoryTemplate(
                id = "h-2",
                title = "Read",
                cadence = "Weekdays",
                startMinute = 9 * 60,
                endMinute = 10 * 60,
                difficulty = 2,
                isBundled = false,
                isArchived = false
            )
        )

        assertEquals(listOf("h-1"), filterHabitHistoryTemplates(templates, "MORNING  walk").map { it.id })
    }

    @Test
    fun filterHabitHistoryTemplatesReturnsAllWhenQueryIsBlank() {
        val templates = listOf(
            HabitHistoryTemplate(
                id = "h-1",
                title = "Morning walk",
                cadence = "Daily",
                startMinute = 8 * 60,
                endMinute = 9 * 60,
                difficulty = 2,
                isBundled = false,
                isArchived = false
            )
        )

        assertEquals(templates, filterHabitHistoryTemplates(templates, "   "))
    }

    @Test
    fun prioritizeRecentHabitHistoryTemplatesRanksConfiguredRecentFirst() {
        val templates = listOf(
            HabitHistoryTemplate("h-1", "A", "Daily", 0, 0, 1, false, false),
            HabitHistoryTemplate("h-2", "B", "Daily", 0, 0, 1, false, false),
            HabitHistoryTemplate("h-3", "C", "Daily", 0, 0, 1, false, false)
        )

        val sorted = prioritizeRecentHabitHistoryTemplates(templates, recentIds = listOf("h-3", "h-1"))

        assertEquals(listOf("h-3", "h-1", "h-2"), sorted.map { it.id })
    }

    @Test
    fun parseAndEncodeRecentHabitTemplateIdsNormalizeAndBound() {
        assertEquals(listOf("one", "two", "three"), parseRecentHabitTemplateIds(" one | two || three |  one | "))
        assertEquals("one|two|three", encodeRecentHabitTemplateIds(listOf("one", "two", "three", "two", "  ")))
    }

    @Test
    fun habitStatusLabelUsesDoneTodayWindowAndDefaultBranches() {
        val today = LocalDate.of(2026, 5, 31)
        val done = habit(
            id = "done",
            title = "Read",
            lastCompletedDate = today,
            streakCount = 2,
            windowStartMinute = 8 * 60,
            windowEndMinute = 9 * 60
        )
        val dueNow = habit(
            id = "due",
            title = "Read",
            lastCompletedDate = null,
            windowStartMinute = 8 * 60,
            windowEndMinute = 20 * 60
        )
        val upcoming = habit(
            id = "upcoming",
            title = "Read",
            lastCompletedDate = null,
            windowStartMinute = 10 * 60,
            windowEndMinute = 11 * 60
        )
        val elapsed = habit(
            id = "elapsed",
            title = "Read",
            lastCompletedDate = null,
            windowStartMinute = 5 * 60,
            windowEndMinute = 6 * 60
        )

        assertEquals("Done today · 2-day streak", done.statusLabel(nowMinute = 1_000, today = today))
        assertEquals("Due now", dueNow.statusLabel(nowMinute = 12 * 60, today = today))
        assertEquals("Starts 10:00 AM", upcoming.statusLabel(nowMinute = 9 * 60, today = today))
        assertEquals("Window ended", elapsed.statusLabel(nowMinute = 12 * 60, today = today))
    }

    @Test
    fun habitStatusLabelReturnsWindowEndedForNoDate() {
        assertEquals(
            "Window ended",
            habit(
                id = "none",
                title = "Read",
                lastCompletedDate = null,
                windowStartMinute = 8 * 60,
                windowEndMinute = 9 * 60
            ).statusLabel(nowMinute = 12 * 60)
        )
    }

    private fun habit(
        id: String,
        title: String,
        lastCompletedDate: LocalDate?,
        streakCount: Int = 0,
        windowStartMinute: Int,
        windowEndMinute: Int,
        isActive: Boolean = true,
        difficulty: Int = 2
    ): Habit = Habit(
        id = id,
        title = title,
        cadence = "Daily",
        windowStartMinute = windowStartMinute,
        windowEndMinute = windowEndMinute,
        difficulty = difficulty,
        isBundled = false,
        streakCount = streakCount,
        lastCompletedDate = lastCompletedDate,
        isActive = isActive
    )
}
