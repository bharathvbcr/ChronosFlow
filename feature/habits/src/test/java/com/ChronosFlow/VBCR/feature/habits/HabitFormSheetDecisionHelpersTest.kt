package com.ChronosFlow.VBCR.feature.habits

import com.ChronosFlow.VBCR.core.ai.HabitAssistSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitFormSheetDecisionHelpersTest {

    @Test
    fun `assist capture ignores short input and ignores punctuation-only words`() {
        assertFalse(shouldAutoRequestHabitAssistForCapture("run"))
        assertFalse(shouldAutoRequestHabitAssistForCapture("  add  "))
        assertFalse(shouldAutoRequestHabitAssistForCapture("hey"))
    }

    @Test
    fun `assist capture flags hydration and launch intent cues`() {
        assertTrue(shouldAutoRequestHabitAssistForCapture("Drink 2L water before work"))
        assertTrue(shouldAutoRequestHabitAssistForCapture("Track my workout in the evening"))
        assertTrue(shouldAutoRequestHabitAssistForCapture("Open app strava at gym"))
    }

    @Test
    fun `recurrence options prioritize captured cadence over selected preset`() {
        val options = contextualHabitRecurrenceOptions(
            selectedPreset = "Daily",
            title = "run three times a week",
            suggestions = emptyList()
        )

        assertEquals("3x / week", options[0])
        assertEquals("Daily", options[1])
    }

    @Test
    fun `recurrence options honors selected preset when context is empty`() {
        val options = contextualHabitRecurrenceOptions(
            selectedPreset = "Every 2 days",
            title = "random thought",
            suggestions = emptyList()
        )

        assertEquals("Every 2 days", options[0])
        assertEquals("Daily", options[1])
    }

    @Test
    fun `window options detect hydration and evening context`() {
        val options = contextualHabitWindowOptions(
            selectedPreset = "Morning",
            title = "drink 500ml water before bed",
            suggestions = emptyList()
        )

        assertEquals("All day", options[0])
        assertEquals("Morning", options[1])
    }

    @Test
    fun `difficulty options prioritize context before defaults`() {
        val options = contextualHabitDifficultyOptions(
            title = "very hard workout",
            suggestions = emptyList()
        )

        assertEquals("Hard", options[0])
        assertEquals("Very hard", options[1])
    }

    @Test
    fun `day plan options can still force bundled mode from suggestions`() {
        val options = contextualHabitDayPlanOptions(
            title = "random",
            isBundled = false,
            suggestions = listOf(
                HabitAssistSuggestion.DayPlan(
                    id = "b1",
                    label = "Show on day plan",
                    reason = "local",
                    source = com.ChronosFlow.VBCR.core.ai.RoutineAssistSource.LOCAL,
                    isBundled = true
                )
            )
        )

        assertEquals(listOf("Show on day plan"), options)
    }

    @Test
    fun `day plan options falls back to flexible when context indicates flexible habit`() {
        val options = contextualHabitDayPlanOptions(
            title = "read a book anytime",
            isBundled = false,
            suggestions = emptyList()
        )

        assertEquals(listOf("Keep flexible"), options)
    }

    @Test
    fun `launch suggestion supports explicit package syntax`() {
        val suggestion = habitLaunchCaptureSuggestion("package:com.example.journal")

        assertEquals("Open habit app", suggestion?.label)
        assertEquals("package:com.example.journal", suggestion?.value)
    }

    @Test
    fun `launch suggestion infers known app by context`() {
        val suggestion = habitLaunchCaptureSuggestion("Log nutrition in MyFitnessPal after dinner")

        assertEquals("Open MyFitnessPal", suggestion?.label)
        assertEquals("com.myfitnesspal.android", suggestion?.value)
    }

    @Test
    fun `launch suggestion returns null for unsupported capture text`() {
        assertNull(habitLaunchCaptureSuggestion("buy groceries after work"))
    }
}
