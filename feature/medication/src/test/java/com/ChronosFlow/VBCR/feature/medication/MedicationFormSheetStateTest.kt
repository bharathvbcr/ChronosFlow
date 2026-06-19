package com.ChronosFlow.VBCR.feature.medication

import com.ChronosFlow.VBCR.core.ai.MedicationAssistSuggestion
import com.ChronosFlow.VBCR.core.ai.RoutineAssistSource
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationFormSheetStateTest {
    @Test
    fun `editable summary card label names section value action and state`() {
        assertEquals(
            "Adjust dose for Dose. Current value: 1 tablet. Collapsed",
            editableSummaryCardContentDescription(
                title = "Dose",
                value = "1 tablet",
                actionLabel = "Adjust dose",
                expanded = false
            )
        )

        assertEquals(
            "Hide controls for Dose. Current value: 1 tablet. Expanded",
            editableSummaryCardContentDescription(
                title = "Dose",
                value = "1 tablet",
                actionLabel = "Hide controls",
                expanded = true
            )
        )
    }

    @Test
    fun `quick dose action labels name the edited medication`() {
        val plan = plan(name = "Vitamin D")

        assertEquals("Mark Vitamin D taken from medication form", medicationFormTakenActionLabel(plan))
        assertEquals("Mark Vitamin D missed from medication form", medicationFormMissedActionLabel(plan))
        assertEquals("Snooze Vitamin D for 15 minutes from medication form", medicationFormSnoozeActionLabel(plan, minutes = 15))
        assertEquals("Snooze Vitamin D for 60 minutes from medication form", medicationFormSnoozeActionLabel(plan, minutes = 60))
    }

    @Test
    fun `applying a medication suggestion supersedes same kind alternatives only`() {
        val morning = MedicationAssistSuggestion.Reminder(
            id = "reminder:morning",
            label = "Morning",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            primaryMinute = 8 * 60
        )
        val evening = morning.copy(id = "reminder:evening", label = "Evening", primaryMinute = 20 * 60)
        val meal = MedicationAssistSuggestion.MealTiming(
            id = "meal:food",
            label = "With food",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            mealTiming = "With food"
        )
        val suggestions = listOf(morning, evening, meal)

        assertEquals(
            setOf("reminder:morning", "reminder:evening"),
            supersededMedicationAssistSuggestionIds(morning, suggestions)
        )
        assertEquals(
            setOf("meal:food"),
            supersededMedicationAssistSuggestionIds(meal, suggestions)
        )
    }

    @Test
    fun `untouched reminder default follows the medication context`() {
        assertEquals(
            21 * 60,
            contextualMedicationDefaultReminderMinute("Melatonin before bed", "", emptyList())
        )
        assertEquals(
            8 * 60,
            contextualMedicationDefaultReminderMinute("Vitamin D with breakfast", "", emptyList())
        )
        assertNull(contextualMedicationDefaultReminderMinute("Ibuprofen", "", emptyList()))
        assertEquals(
            20 * 60,
            contextualMedicationDefaultReminderMinute(
                "Ibuprofen",
                "",
                listOf(
                    MedicationAssistSuggestion.Reminder(
                        id = "reminder:suggested",
                        label = "Evening",
                        reason = "Test",
                        source = RoutineAssistSource.LOCAL,
                        primaryMinute = 20 * 60
                    )
                )
            )
        )
    }

    @Test
    fun `auto apply targets details reminder and meal timing only while untouched`() {
        val details = MedicationAssistSuggestion.Details(
            id = "details:a",
            label = "Vitamin D",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            name = "Vitamin D",
            dosage = "1000",
            unit = "iu"
        )
        val reminder = MedicationAssistSuggestion.Reminder(
            id = "reminder:a",
            label = "Morning",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            primaryMinute = 8 * 60
        )
        val mealTiming = MedicationAssistSuggestion.MealTiming(
            id = "meal:a",
            label = "With food",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            mealTiming = "With food"
        )
        val refill = MedicationAssistSuggestion.RefillTracking(
            id = "refill:a",
            label = "Track refills",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            dosesLeft = 30
        )

        val ids = autoApplicableMedicationAssistSuggestionIds(
            suggestions = listOf(details, reminder, mealTiming, refill),
            doseUnset = true,
            reminderUntouched = false,
            mealTimingUntouched = true
        )

        assertEquals(setOf("details:a", "meal:a"), ids)
    }

    @Test
    fun `suggestions already satisfied by the medication form are reported redundant`() {
        val matchingReminder = MedicationAssistSuggestion.Reminder(
            id = "reminder:match",
            label = "Morning",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            primaryMinute = 8 * 60
        )
        val freshMealTiming = MedicationAssistSuggestion.MealTiming(
            id = "meal:new",
            label = "With food",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            mealTiming = "With food"
        )
        val matchingDetails = MedicationAssistSuggestion.Details(
            id = "details:match",
            label = "Vitamin D",
            reason = "Test",
            source = RoutineAssistSource.LOCAL,
            name = "vitamin d",
            dosage = "1000",
            unit = "iu"
        )

        val redundant = redundantMedicationAssistSuggestionIds(
            suggestions = listOf(matchingReminder, freshMealTiming, matchingDetails),
            currentName = "Vitamin D",
            currentDosage = "1000",
            currentUnit = "iu",
            currentFrequency = "Once daily",
            currentPrimaryReminderMinute = 8 * 60,
            currentSecondaryReminderMinute = null,
            currentMealTiming = "Anytime",
            currentHasRefillTracking = false,
            currentRefillCount = null,
            currentNotes = "",
            currentForm = "tablet",
            currentRoute = "oral"
        )

        assertEquals(setOf("reminder:match", "details:match"), redundant)
    }

    @Test
    fun `contextual medication reminder options prioritize capture and suggestions`() {
        assertEquals(
            "Night",
            contextualMedicationReminderOptions(
                name = "Inhaler before bed",
                notes = "",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            "Morning",
            contextualMedicationReminderOptions(
                name = "Vitamin D",
                notes = "",
                suggestions = listOf(
                    MedicationAssistSuggestion.Reminder(
                        id = "test:reminder",
                        label = "Morning",
                        reason = "Test",
                        source = RoutineAssistSource.LOCAL,
                        primaryMinute = 8 * 60
                    )
                )
            ).first()
        )
    }

    @Test
    fun `contextual medication meal timing options prioritize capture and suggestions`() {
        assertEquals(
            "With food",
            contextualMedicationMealTimingOptions(
                name = "Metformin with dinner",
                notes = "",
                selectedMealTiming = "Anytime",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            "Before bed",
            contextualMedicationMealTimingOptions(
                name = "Evening dose",
                notes = "",
                selectedMealTiming = "Anytime",
                suggestions = listOf(
                    MedicationAssistSuggestion.MealTiming(
                        id = "test:meal",
                        label = "Before bed",
                        reason = "Test",
                        source = RoutineAssistSource.LOCAL,
                        mealTiming = "Before bed"
                    )
                )
            ).first()
        )
    }

    @Test
    fun `contextual medication dosage presets prioritize typed capture and suggestions`() {
        assertEquals(
            "1000",
            contextualMedicationDosagePresets(
                name = "Vitamin D 1000 iu",
                dosage = "",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            "½",
            contextualMedicationDosagePresets(
                name = "Melatonin 1/2 tablet before bed",
                dosage = "",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            "500",
            contextualMedicationDosagePresets(
                name = "Metformin",
                dosage = "",
                suggestions = listOf(
                    MedicationAssistSuggestion.Details(
                        id = "test:details",
                        label = "Metformin 500 mg",
                        reason = "Test",
                        source = RoutineAssistSource.LOCAL,
                        dosage = "500"
                    )
                )
            ).first()
        )
    }

    @Test
    fun `contextual medication frequency options prioritize typed capture and suggestions`() {
        assertEquals(
            "Twice daily",
            contextualMedicationFrequencyOptions(
                name = "Metformin twice daily",
                notes = "",
                frequency = "Once daily",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            "As needed",
            contextualMedicationFrequencyOptions(
                name = "Rescue inhaler as needed",
                notes = "",
                frequency = "Once daily",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            "Weekly",
            contextualMedicationFrequencyOptions(
                name = "Vitamin B12",
                notes = "",
                frequency = "Once daily",
                suggestions = listOf(
                    MedicationAssistSuggestion.Details(
                        id = "test:frequency",
                        label = "Weekly",
                        reason = "Test",
                        source = RoutineAssistSource.LOCAL,
                        frequency = "Weekly"
                    )
                )
            ).first()
        )
    }

    @Test
    fun `contextual medication unit options prioritize capture and suggestions`() {
        assertEquals(
            "iu",
            contextualMedicationUnitOptions(
                name = "Vitamin D 1000 iu",
                selectedUnit = "dose",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            "mg",
            contextualMedicationUnitOptions(
                name = "Metformin",
                selectedUnit = "dose",
                suggestions = listOf(
                    MedicationAssistSuggestion.Details(
                        id = "test:details",
                        label = "Metformin 500 mg",
                        reason = "Test",
                        source = RoutineAssistSource.LOCAL,
                        unit = "mg"
                    )
                )
            ).first()
        )
    }

    @Test
    fun `contextual medication refill count options prioritize suggestions and selected value`() {
        assertEquals(
            "23",
            contextualMedicationRefillCountOptions(
                selectedRefillDoses = "",
                suggestions = listOf(
                    MedicationAssistSuggestion.RefillTracking(
                        id = "test:refill",
                        label = "Track 23 doses",
                        reason = "Test",
                        source = RoutineAssistSource.LOCAL,
                        dosesLeft = 23
                    )
                )
            ).first()
        )

        assertEquals(
            "12",
            contextualMedicationRefillCountOptions(
                selectedRefillDoses = "12",
                suggestions = emptyList()
            ).first()
        )
    }

    @Test
    fun `contextual medication form and route options prioritize capture`() {
        assertEquals(
            "inhaler",
            contextualMedicationFormOptions(
                name = "Rescue inhaler",
                selectedForm = "tablet",
                suggestions = emptyList()
            ).first()
        )
        assertEquals(
            "inhaled",
            contextualMedicationRouteOptions(
                name = "Rescue inhaler",
                selectedRoute = "oral",
                suggestions = emptyList()
            ).first()
        )
        assertEquals(
            "cream",
            contextualMedicationFormOptions(
                name = "Topical gel nightly",
                selectedForm = "tablet",
                suggestions = emptyList()
            ).first()
        )
        assertEquals(
            "topical",
            contextualMedicationRouteOptions(
                name = "Topical gel nightly",
                selectedRoute = "oral",
                suggestions = emptyList()
            ).first()
        )
    }

    @Test
    fun `medication auto assist capture ignores default fields but keeps explicit context`() {
        assertEquals(
            "Vitamin D",
            medicationAutoAssistCapture(
                name = "Vitamin D",
                dosage = "",
                unit = "dose",
                frequency = "Once daily",
                mealTiming = "Anytime",
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )
        assertEquals(
            "Vitamin D 1000 iu Before bed",
            medicationAutoAssistCapture(
                name = "Vitamin D",
                dosage = "1000",
                unit = "iu",
                frequency = "Once daily",
                mealTiming = "Before bed",
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )
    }

    @Test
    fun `contextual medication context kind classifies all recognized context groups`() {
        assertEquals(
            MedicationContextKind.SUPPLEMENT,
            contextualMedicationContextKind("vitamin d supplement")
        )
        assertEquals(
            MedicationContextKind.AS_NEEDED,
            contextualMedicationContextKind("use prn when headache starts")
        )
        assertEquals(
            MedicationContextKind.INHALER,
            contextualMedicationContextKind("inhaler puffs with chest tightness")
        )
        assertEquals(
            MedicationContextKind.INJECTION,
            contextualMedicationContextKind("insulin injection before bed")
        )
        assertEquals(
            MedicationContextKind.REFILL,
            contextualMedicationContextKind("need prescription refill soon")
        )
        assertEquals(
            MedicationContextKind.DROPS,
            contextualMedicationContextKind("eye drops for redness")
        )
        assertEquals(
            MedicationContextKind.TOPICAL,
            contextualMedicationContextKind("hydrocortisone cream for dry patch")
        )
        assertEquals(
            MedicationContextKind.LIQUID,
            contextualMedicationContextKind("take cough syrup after meals")
        )
        assertEquals(
            null,
            contextualMedicationContextKind("regular daily maintenance plan")
        )
    }

    @Test
    fun `contextual medication reminder options include contextual timing and explicit suggestion`() {
        assertEquals(
            listOf("Morning", "Noon", "Afternoon", "Evening", "Night"),
            contextualMedicationReminderOptions(
                name = "Morning breakfast noon lunch afternoon dinner night",
                notes = "",
                suggestions = emptyList()
            ).take(5)
        )

        assertEquals(
            "Night",
            contextualMedicationReminderOptions(
                name = "Noon check in",
                notes = "",
                suggestions = listOf(
                    MedicationAssistSuggestion.Reminder(
                        id = "test:reminder",
                        label = "Night",
                        reason = "Test",
                        source = RoutineAssistSource.LOCAL,
                        primaryMinute = 21 * 60
                    )
                )
            ).first()
        )
    }

    @Test
    fun `auto medication assist capture requires dose timing or safety context`() {
        assertEquals(false, shouldAutoRequestMedicationAssistForCapture("Vitamin D"))
        assertEquals(true, shouldAutoRequestMedicationAssistForCapture("Vitamin D 1000 iu morning"))
        assertEquals(true, shouldAutoRequestMedicationAssistForCapture("Rescue inhaler"))
    }

    private fun plan(name: String): MedicationPlan {
        return MedicationPlan(
            id = "med-1",
            name = name,
            dosage = "1000",
            unit = "IU",
            notes = null,
            startAt = null,
            endAt = null,
            reminderMinuteOfDay = 8 * 60,
            takeWithFood = true,
            missedCount = 0,
            refillNeededAfterDoses = null,
            isActive = true
        )
    }
}
