package com.chronosflow.feature.medication

import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.MedicationSchedule
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import com.chronosflow.core.domain.model.buildLegacyMedicationSchedule
import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MedicationFormSheetCoverageTest {
    @Test
    fun `buildMedicationHistoryTemplates skips current plan and keeps active templates first`() {
        val plans = listOf(
            medicationPlan(id = "current", name = "Rescue inhaler", isActive = true),
            medicationPlan(id = "archived", name = "Vitamin D", dose = "2", isActive = false),
            medicationPlan(id = "daily", name = "Vitamin D", isActive = true)
        )

        val templates = buildMedicationHistoryTemplates(plans, currentPlanId = "current")

        assertEquals(2, templates.size)
        assertEquals("active plan should be included", "daily", templates.find { !it.isArchived }?.id)
        assertEquals("archived plan should be included", "archived", templates.find { it.isArchived }?.id)
        assertEquals(1, templates.count { it.isArchived })
        assertEquals("With food", templates[0].mealTiming)
    }

    @Test
    fun `filterMedicationHistoryTemplates matches query terms across metadata`() {
        val templates = buildMedicationHistoryTemplates(
            listOf(
                medicationPlan(id = "match", name = "Vitamin D", dose = "1000", unit = "iu"),
                medicationPlan(id = "nomatch", name = "Inhaler", dose = "2", unit = "dose")
            )
        )

        val filteredByNotes = filterMedicationHistoryTemplates(templates, "vitamin")
        assertEquals(listOf("match"), filteredByNotes.map { it.id })

        val filteredByBlank = filterMedicationHistoryTemplates(templates, "")
        assertEquals(2, filteredByBlank.size)
    }

    @Test
    fun `prioritizeRecentMedicationHistoryTemplates moves recent ids to front`() {
        val templates = buildMedicationHistoryTemplates(
            listOf(
                medicationPlan(id = "first", name = "Aspirin"),
                medicationPlan(id = "second", name = "Ibuprofen"),
                medicationPlan(id = "third", name = "Paracetamol")
            )
        )
        val sorted = prioritizeRecentMedicationHistoryTemplates(
            templates = templates,
            recentIds = listOf("third", "first")
        )

        assertEquals(listOf("third", "first", "second"), sorted.map { it.id })
    }

    @Test
    fun `prioritizeContextualMedicationHistoryTemplates scores contextual matches highest`() {
        val templates = buildMedicationHistoryTemplates(
            listOf(
                medicationPlan(id = "vitamin", name = "Vitamin D", dose = "1000", unit = "iu"),
                medicationPlan(id = "inhaler", name = "Rescue inhaler", dose = "2", unit = "dose")
            )
        )
        val ranked = prioritizeContextualMedicationHistoryTemplates(
            templates = templates,
            recentIds = emptyList(),
            name = "Vitamin D 1000 IU",
            dosage = "1000",
            unit = "IU",
            frequency = "Once daily",
            mealTiming = "Anytime",
            notes = "",
            suggestions = emptyList()
        )

        assertEquals("vitamin", ranked.first().id)
    }

    @Test
    fun `parse and encode recent ids preserves order and dedupes`() {
        assertEquals(
            listOf("a", "b", "c"),
            parseRecentMedicationTemplateIds("a| b | |a|c|||")
        )

        assertEquals(
            "c|a|b",
            encodeRecentMedicationTemplateIds(listOf("c", "a", "b", "a"))
        )
    }

    @Test
    fun `buildMedicationRecurrenceForFrequency maps supported frequencies`() {
        val weekday = buildMedicationRecurrenceForFrequency(
            frequency = "Weekdays",
            reminderTimes = listOf(8 * 60),
            anchorWeekday = DayOfWeek.FRIDAY
        )
        assertEquals(PlannerRecurrenceType.WEEKDAYS, weekday.type)

        val asNeeded = buildMedicationRecurrenceForFrequency(
            frequency = "As needed",
            reminderTimes = listOf(8 * 60),
            anchorWeekday = DayOfWeek.MONDAY
        )
        assertEquals(PlannerRecurrenceType.PRN, asNeeded.type)

        val everyOtherDay = buildMedicationRecurrenceForFrequency(
            frequency = "Every other day",
            reminderTimes = listOf(8 * 60),
            anchorWeekday = DayOfWeek.MONDAY
        )
        assertEquals(2, everyOtherDay.interval)
    }

    private fun medicationPlan(
        id: String,
        name: String = "test-$id",
        dose: String = "1",
        unit: String = "mg",
        isActive: Boolean = true,
        schedule: MedicationSchedule? = null
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = name,
        dosage = dose,
        unit = unit,
        notes = null,
        startAt = LocalDateTime.of(2026, 5, 31, 8, 0),
        endAt = null,
        reminderMinuteOfDay = 8 * 60,
        takeWithFood = true,
        missedCount = 0,
        refillNeededAfterDoses = null,
        isActive = isActive,
        schedule = schedule ?: buildLegacyMedicationSchedule(
            medicationPlanId = id,
            primaryReminderMinute = 8 * 60
        ),
        safetyProfile = null
    )
}
