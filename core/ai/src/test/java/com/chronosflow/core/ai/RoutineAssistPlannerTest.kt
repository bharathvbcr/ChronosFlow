package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.AssistTextGeneration
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineAssistPlannerTest {
    @Test
    fun `habit prompt includes concrete capture examples`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        val prompt = slot<String>()
        coEvery { coordinator.generateAssistText(capture(prompt)) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = HabitAssistPlanner(coordinator)

        planner.suggest(
            HabitAssistRequest(
                title = "gym 3x week evening",
                cadence = "Daily",
                startMinute = 8 * 60,
                endMinute = 20 * 60,
                difficulty = 2,
                isBundled = false
            )
        )

        assertTrue(prompt.captured.contains("Input: gym 3x week evening"))
        assertTrue(prompt.captured.contains("Return complementary suggestions together"))
        assertTrue(prompt.captured.contains("For dictated fragments, keep the habit name separate"))
        assertTrue(prompt.captured.contains("recurrence|3x / week|3x / week"))
        assertTrue(prompt.captured.contains("window|Evening window|1080,1320"))
        assertTrue(prompt.captured.contains("Input: hydrate all day"))
    }

    @Test
    fun `habit assist local fallback understands spoken per week cadence`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = HabitAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            HabitAssistRequest(
                title = "strength training three times per week at 6pm",
                cadence = "Daily",
                startMinute = 8 * 60,
                endMinute = 20 * 60,
                difficulty = 2,
                isBundled = false
            )
        )

        val title = suggestions
            .filterIsInstance<HabitAssistSuggestion.Title>()
            .single()
        val recurrence = suggestions
            .filterIsInstance<HabitAssistSuggestion.Recurrence>()
            .single()
        val window = suggestions
            .filterIsInstance<HabitAssistSuggestion.Window>()
            .single()
        assertEquals("Strength training", title.title)
        assertEquals("3x / week", recurrence.cadence)
        assertEquals(18 * 60, window.startMinute)
    }

    @Test
    fun `medication prompt includes concrete capture examples without clinical advice`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        val prompt = slot<String>()
        coEvery { coordinator.generateAssistText(capture(prompt)) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = MedicationAssistPlanner(coordinator)

        planner.suggest(
            MedicationAssistRequest(
                name = "vitamin d 1000 iu morning",
                dosage = "",
                unit = "dose",
                frequency = "Once daily",
                primaryReminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )

        assertTrue(prompt.captured.contains("Input: vitamin d 1000 iu morning"))
        assertTrue(prompt.captured.contains("Return complementary details, reminder, meal_timing, form_route, refill, and notes suggestions together"))
        assertTrue(prompt.captured.contains("For dictated fragments, preserve the medication or supplement name separately"))
        assertTrue(prompt.captured.contains("details|Vitamin D 1000 iu|Vitamin D,1000,iu,Once daily"))
        assertTrue(prompt.captured.contains("Input: rescue inhaler as needed 23 left"))
        assertTrue(prompt.captured.contains("Do not recommend medications, dosage changes, interactions, or clinical advice."))
    }

    @Test
    fun `medication assist normalizes generated topical form route aliases`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "form_route|Topical gel|gel,topical|The capture says topical gel.",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = MedicationAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            MedicationAssistRequest(
                name = "topical gel nightly",
                dosage = "",
                unit = "dose",
                frequency = "Once daily",
                primaryReminderMinute = 21 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )

        val formRoute = suggestions.single() as MedicationAssistSuggestion.FormRoute
        assertEquals("cream", formRoute.form)
        assertEquals("topical", formRoute.route)
    }

    @Test
    fun `habit assist parses Gemini Nano window suggestion`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "window|Morning window|360,540|Matches the habit wording",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = HabitAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            HabitAssistRequest(
                title = "Morning walk",
                cadence = "Daily",
                startMinute = 8 * 60,
                endMinute = 20 * 60,
                difficulty = 2,
                isBundled = false
            )
        )

        val window = suggestions.single() as HabitAssistSuggestion.Window
        assertEquals(6 * 60, window.startMinute)
        assertEquals(9 * 60, window.endMinute)
        assertEquals(RoutineAssistSource.GEMINI_NANO, window.source)
    }

    @Test
    fun `habit assist falls back locally when generation is empty`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = HabitAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            HabitAssistRequest(
                title = "Evening journal",
                cadence = "Daily",
                startMinute = 8 * 60,
                endMinute = 20 * 60,
                difficulty = 2,
                isBundled = false
            )
        )

        assertTrue(suggestions.any { it is HabitAssistSuggestion.Window && it.startMinute == 18 * 60 })
        assertEquals(RoutineAssistSource.LOCAL, suggestions.first().source)
    }

    @Test
    fun `habit assist local fallback adapts gym cadence effort and evening window`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = HabitAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            HabitAssistRequest(
                title = "gym 3x week evening",
                cadence = "Daily",
                startMinute = 8 * 60,
                endMinute = 20 * 60,
                difficulty = 2,
                isBundled = false
            )
        )

        val recurrence = suggestions.filterIsInstance<HabitAssistSuggestion.Recurrence>().single()
        val window = suggestions.filterIsInstance<HabitAssistSuggestion.Window>().single()
        val difficulty = suggestions.filterIsInstance<HabitAssistSuggestion.Difficulty>().single()
        val dayPlan = suggestions.filterIsInstance<HabitAssistSuggestion.DayPlan>().single()
        assertEquals("3x / week", recurrence.cadence)
        assertEquals(18 * 60, window.startMinute)
        assertEquals(22 * 60, window.endMinute)
        assertEquals(4, difficulty.difficulty)
        assertEquals(true, dayPlan.isBundled)
    }

    @Test
    fun `medication assist parses reminder without dosage advice`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "reminder|Evening reminder|1260|Use the stated bedtime routine",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = MedicationAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            MedicationAssistRequest(
                name = "Evening dose",
                dosage = "1",
                unit = "tablet",
                frequency = "Once daily",
                primaryReminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "bedtime routine",
                form = "tablet",
                route = "oral"
            )
        )

        val reminder = suggestions.single() as MedicationAssistSuggestion.Reminder
        assertEquals(21 * 60, reminder.primaryMinute)
        assertEquals(RoutineAssistSource.GEMINI_NANO, reminder.source)
    }

    @Test
    fun `medication assist local fallback does not invent refill or notes`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = MedicationAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            MedicationAssistRequest(
                name = "Inhaler",
                dosage = "",
                unit = "dose",
                frequency = "Once daily",
                primaryReminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )

        assertTrue(suggestions.any { it is MedicationAssistSuggestion.FormRoute })
        assertTrue(suggestions.none { it is MedicationAssistSuggestion.RefillTracking })
        assertTrue(suggestions.none { it is MedicationAssistSuggestion.Notes })
    }

    @Test
    fun `medication assist extracts vitamin dose and timing from capture`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = MedicationAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            MedicationAssistRequest(
                name = "vitamin d 1000 iu morning",
                dosage = "",
                unit = "dose",
                frequency = "Once daily",
                primaryReminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )

        val details = suggestions.first { it is MedicationAssistSuggestion.Details } as MedicationAssistSuggestion.Details
        assertEquals("Vitamin D", details.name)
        assertEquals("1000", details.dosage)
        assertEquals("iu", details.unit)
        assertTrue(suggestions.any { it is MedicationAssistSuggestion.Reminder })
    }

    @Test
    fun `medication assist local fallback adapts dinner capture into food timing and evening reminder`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = MedicationAssistPlanner(coordinator)

        val suggestions = planner.suggest(
            MedicationAssistRequest(
                name = "metformin 500 mg with dinner",
                dosage = "",
                unit = "dose",
                frequency = "Once daily",
                primaryReminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )

        val details = suggestions.first { it is MedicationAssistSuggestion.Details } as MedicationAssistSuggestion.Details
        val reminder = suggestions
            .filterIsInstance<MedicationAssistSuggestion.Reminder>()
            .single { it.primaryMinute == 18 * 60 }
        val mealTiming = suggestions.filterIsInstance<MedicationAssistSuggestion.MealTiming>().single()
        assertEquals("Metformin", details.name)
        assertEquals("500", details.dosage)
        assertEquals("mg", details.unit)
        assertEquals(18 * 60, reminder.primaryMinute)
        assertEquals("With food", mealTiming.mealTiming)
    }

    @Test
    fun `medication assist local fallback spaces three and four daily reminders`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = MedicationAssistPlanner(coordinator)

        val threeTimesSuggestions = planner.suggest(
            MedicationAssistRequest(
                name = "antibiotic 3 times daily",
                dosage = "",
                unit = "dose",
                frequency = "Once daily",
                primaryReminderMinute = 6 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )
        val fourTimesSuggestions = planner.suggest(
            MedicationAssistRequest(
                name = "eye drops 4 times daily",
                dosage = "",
                unit = "drop",
                frequency = "Once daily",
                primaryReminderMinute = 6 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "drop",
                route = "topical"
            )
        )

        val threeTimesReminder = threeTimesSuggestions
            .filterIsInstance<MedicationAssistSuggestion.Reminder>()
            .single { it.frequency == "3 times daily" }
        val fourTimesReminder = fourTimesSuggestions
            .filterIsInstance<MedicationAssistSuggestion.Reminder>()
            .single { it.frequency == "4 times daily" }
        assertEquals(6 * 60, threeTimesReminder.primaryMinute)
        assertEquals(14 * 60, threeTimesReminder.secondaryMinute)
        assertEquals(6 * 60, fourTimesReminder.primaryMinute)
        assertEquals(12 * 60, fourTimesReminder.secondaryMinute)
    }

    @Test
    fun `medication assist local fallback understands spoken daily frequency variants`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = MedicationAssistPlanner(coordinator)

        val onceDailySuggestions = planner.suggest(
            MedicationAssistRequest(
                name = "vitamin d every morning",
                dosage = "",
                unit = "dose",
                frequency = "",
                primaryReminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )
        val twiceDailySuggestions = planner.suggest(
            MedicationAssistRequest(
                name = "antibiotic two times per day",
                dosage = "",
                unit = "dose",
                frequency = "",
                primaryReminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )

        val onceDailyDetails = onceDailySuggestions
            .filterIsInstance<MedicationAssistSuggestion.Details>()
            .single()
        val twiceDailyDetails = twiceDailySuggestions
            .filterIsInstance<MedicationAssistSuggestion.Details>()
            .single()
        assertEquals("Once daily", onceDailyDetails.frequency)
        assertEquals("Twice daily", twiceDailyDetails.frequency)
    }
}
