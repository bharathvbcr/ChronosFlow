package com.chronosflow.feature.medication

import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.MedicationSafetyProfile
import com.chronosflow.core.domain.model.MedicationSchedule
import com.chronosflow.core.domain.model.PlannerRecurrence
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationHistoryTemplateTest {
    @Test
    fun buildMedicationHistoryTemplatesPrioritizesArchivedPlansAndExcludesCurrentPlan() {
        val templates = buildMedicationHistoryTemplates(
            plans = listOf(
                plan(id = "current", name = "Vitamin D", isActive = true),
                plan(id = "archived", name = "Metformin", isActive = false, reminderMinute = 8 * 60),
                plan(id = "active", name = "Inhaler", isActive = true, reminderMinute = 21 * 60)
            ),
            currentPlanId = "current"
        )

        assertEquals(listOf("archived", "active"), templates.map(MedicationHistoryTemplate::id))
        assertEquals("Archived", templates.first().sourceLabel)
        assertEquals("Saved", templates.last().sourceLabel)
    }

    @Test
    fun buildMedicationHistoryTemplatesPreservesFourDailyRemindersAndPrnCadence() {
        val templates = buildMedicationHistoryTemplates(
            plans = listOf(
                plan(
                    id = "four",
                    name = "Antibiotic",
                    isActive = true,
                    reminderMinute = 6 * 60,
                    reminderMinutes = listOf(6 * 60, 12 * 60, 18 * 60, 23 * 60),
                    windowMinutes = 45
                ),
                plan(
                    id = "prn",
                    name = "Inhaler",
                    isActive = true,
                    reminderMinute = 9 * 60,
                    isPrn = true
                ),
                plan(
                    id = "mwf",
                    name = "Weekly supplement",
                    isActive = true,
                    reminderMinute = 8 * 60,
                    recurrenceType = PlannerRecurrenceType.SELECTED_WEEKDAYS,
                    recurrenceWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                )
            )
        )

        val fourDaily = templates.first { it.id == "four" }
        assertEquals("4 times daily", fourDaily.frequencyLabel)
        assertEquals(12 * 60, fourDaily.secondaryReminderMinute)
        assertEquals(18 * 60, fourDaily.thirdReminderMinute)
        assertEquals(23 * 60, fourDaily.fourthReminderMinute)
        assertEquals(45, fourDaily.windowMinutes)

        val asNeeded = templates.first { it.id == "prn" }
        assertEquals(true, asNeeded.isPrn)
        assertEquals("As needed", asNeeded.frequencyLabel)
        assertEquals("Inhaler · As needed · Saved", asNeeded.displayLabel)

        assertEquals("Mon/Wed/Fri", templates.first { it.id == "mwf" }.frequencyLabel)
    }

    @Test
    fun buildMedicationRecurrenceForFrequencyUsesExistingPlannerTypes() {
        val selected = buildMedicationRecurrenceForFrequency(
            frequency = "Mon/Wed/Fri",
            reminderTimes = listOf(8 * 60),
            anchorWeekday = DayOfWeek.THURSDAY
        )
        assertEquals(PlannerRecurrenceType.SELECTED_WEEKDAYS, selected.type)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), selected.weekdays)
        assertEquals(listOf(8 * 60), selected.timesOfDayMinutes)

        val weekly = buildMedicationRecurrenceForFrequency(
            frequency = "Weekly",
            reminderTimes = listOf(9 * 60),
            anchorWeekday = DayOfWeek.THURSDAY
        )
        assertEquals(PlannerRecurrenceType.WEEKLY_INTERVAL, weekly.type)
        assertEquals(setOf(DayOfWeek.THURSDAY), weekly.weekdays)

        val everyOtherDay = buildMedicationRecurrenceForFrequency(
            frequency = "Every other day",
            reminderTimes = listOf(10 * 60),
            anchorWeekday = DayOfWeek.THURSDAY
        )
        assertEquals(PlannerRecurrenceType.EVERY_N_DAYS, everyOtherDay.type)
        assertEquals(2, everyOtherDay.interval)
    }

    @Test
    fun filterMedicationHistoryTemplatesMatchesNameDoseMealTimingAndSource() {
        val templates = listOf(
            MedicationHistoryTemplate(
                id = "archived",
                name = "Metformin",
                dose = "1",
                unit = "tablet",
                reminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                takeWithFood = true,
                mealTiming = "With food",
                refillNeededAfterDoses = 14,
                displayNotes = "Breakfast",
                form = "tablet",
                route = "oral",
                pharmacyName = "Local Rx",
                prescriberName = "Dr. Hale",
                cautions = listOf("Take with food"),
                isArchived = true
            ),
            MedicationHistoryTemplate(
                id = "saved",
                name = "Inhaler",
                dose = "2",
                unit = "dose",
                reminderMinute = 21 * 60,
                secondaryReminderMinute = null,
                takeWithFood = false,
                mealTiming = "Anytime",
                refillNeededAfterDoses = null,
                displayNotes = null,
                form = "inhaler",
                route = "inhaled",
                pharmacyName = null,
                prescriberName = null,
                cautions = listOf("Track rescue usage"),
                isArchived = false,
                windowMinutes = 45
            )
        )

        assertEquals(listOf("archived"), filterMedicationHistoryTemplates(templates, "archive").map(MedicationHistoryTemplate::id))
        assertEquals(listOf("archived"), filterMedicationHistoryTemplates(templates, "with food").map(MedicationHistoryTemplate::id))
        assertEquals(listOf("saved"), filterMedicationHistoryTemplates(templates, "2 dose").map(MedicationHistoryTemplate::id))
        assertEquals(listOf("saved"), filterMedicationHistoryTemplates(templates, "45 min").map(MedicationHistoryTemplate::id))
        assertEquals(listOf("archived"), filterMedicationHistoryTemplates(templates, "METFORMIN breakfast").map(MedicationHistoryTemplate::id))
        assertEquals(templates, filterMedicationHistoryTemplates(templates, "   "))
    }

    @Test
    fun prioritizeRecentMedicationHistoryTemplatesMovesRecentSelectionsToTopWithinCurrentResults() {
        val templates = listOf(
            MedicationHistoryTemplate("archived", "Metformin", "1", "tablet", 8 * 60, null, true, "With food", 14, "Breakfast", "tablet", "oral", null, null, emptyList(), true),
            MedicationHistoryTemplate("saved", "Inhaler", "2", "dose", 21 * 60, null, false, "Anytime", null, null, "inhaler", "inhaled", null, null, listOf("Track rescue usage"), false),
            MedicationHistoryTemplate("later", "Vitamin D", "1", "tablet", 9 * 60, null, false, "Anytime", null, null, "tablet", "oral", null, null, emptyList(), false)
        )

        val prioritized = prioritizeRecentMedicationHistoryTemplates(
            templates = templates,
            recentIds = listOf("later", "archived", "missing")
        )

        assertEquals(listOf("later", "archived", "saved"), prioritized.map(MedicationHistoryTemplate::id))
    }

    @Test
    fun encodeAndParseRecentMedicationTemplateIdsRoundTripWithDeduping() {
        val encoded = encodeRecentMedicationTemplateIds(
            listOf("later", "archived", "later", "", "saved", "extra", "overflow")
        )

        assertEquals(listOf("later", "archived", "saved", "extra", "overflow"), parseRecentMedicationTemplateIds(encoded))
    }

    @Test
    fun prioritizeContextualMedicationHistoryTemplatesUsesContextScoringAndFallsBackToRecentWhenBlank() {
        val templates = listOf(
            MedicationHistoryTemplate(
                id = "archived",
                name = "Metformin",
                dose = "1",
                unit = "tablet",
                reminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                takeWithFood = true,
                mealTiming = "With food",
                refillNeededAfterDoses = 14,
                displayNotes = "Breakfast routine",
                form = "tablet",
                route = "oral",
                pharmacyName = null,
                prescriberName = null,
                cautions = emptyList(),
                isArchived = true
            ),
            MedicationHistoryTemplate(
                id = "saved",
                name = "Vitamin D",
                dose = "2",
                unit = "tablet",
                reminderMinute = 20 * 60,
                secondaryReminderMinute = null,
                takeWithFood = false,
                mealTiming = "Anytime",
                refillNeededAfterDoses = null,
                displayNotes = null,
                form = "tablet",
                route = "oral",
                pharmacyName = null,
                prescriberName = null,
                cautions = emptyList(),
                isArchived = false
            )
        )

        val contextual = prioritizeContextualMedicationHistoryTemplates(
            templates = templates,
            recentIds = listOf("saved", "archived"),
            name = "Metformin",
            dosage = "1",
            unit = "tablet",
            frequency = "Once daily",
            mealTiming = "With food",
            notes = "Breakfast",
            suggestions = emptyList()
        )

        assertEquals(listOf("archived", "saved"), contextual.map(MedicationHistoryTemplate::id))

        val fallback = prioritizeContextualMedicationHistoryTemplates(
            templates = templates,
            recentIds = listOf("saved", "archived"),
            name = "",
            dosage = "",
            unit = "",
            frequency = "",
            mealTiming = "",
            notes = "",
            suggestions = emptyList()
        )

        assertEquals(listOf("saved", "archived"), fallback.map(MedicationHistoryTemplate::id))
    }

    private fun plan(
        id: String,
        name: String,
        isActive: Boolean,
        reminderMinute: Int = 9 * 60,
        reminderMinutes: List<Int> = listOf(reminderMinute),
        isPrn: Boolean = false,
        recurrenceType: PlannerRecurrenceType? = null,
        recurrenceInterval: Int = 1,
        recurrenceWeekdays: Set<DayOfWeek> = emptySet(),
        windowMinutes: Int = 15
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = name,
        dosage = "1",
        unit = "tablet",
        notes = null,
        startAt = LocalDateTime.of(2026, 5, 24, 8, 0),
        endAt = null,
        reminderMinuteOfDay = reminderMinute,
        takeWithFood = false,
        missedCount = 0,
        refillNeededAfterDoses = null,
        isActive = isActive,
        schedule = MedicationSchedule(
            id = "schedule-$id",
            medicationPlanId = id,
            recurrence = PlannerRecurrence(
                type = when {
                    isPrn -> PlannerRecurrenceType.PRN
                    recurrenceType != null -> recurrenceType
                    reminderMinutes.size > 1 -> PlannerRecurrenceType.MULTIPLE_TIMES_DAILY
                    else -> PlannerRecurrenceType.DAILY
                },
                interval = recurrenceInterval,
                weekdays = recurrenceWeekdays,
                timesOfDayMinutes = if (isPrn) emptyList() else reminderMinutes
            ),
            plannerVisible = !isPrn,
            isPrn = isPrn,
            windowMinutes = windowMinutes
        ),
        safetyProfile = MedicationSafetyProfile(
            medicationPlanId = id,
            form = if (name == "Inhaler") "inhaler" else "tablet",
            route = if (name == "Inhaler") "inhaled" else "oral",
            instructions = if (name == "Vitamin D") "Take with breakfast" else null,
            mealTiming = if (name == "Metformin") "With food" else "Anytime"
        )
    )
}
