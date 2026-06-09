package com.chronosflow.feature.medication

import com.chronosflow.core.domain.model.MedicationPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationRowStateTest {
    @Test
    fun `medication row action labels name the plan`() {
        val plan = plan(name = "Vitamin D")

        assertEquals("Open actions for Vitamin D", medicationContextActionLabel(plan))
        assertEquals("Edit Vitamin D", medicationEditActionLabel(plan))
        assertEquals("Archive Vitamin D", medicationArchiveActionLabel(plan))
    }

    @Test
    fun `dose action labels name the medication and dose outcome`() {
        val plan = plan(name = "Vitamin D")

        assertEquals("Mark Vitamin D taken", medicationTakenActionLabel(plan))
        assertEquals("Mark Vitamin D missed", medicationMissedActionLabel(plan))
    }

    @Test
    fun `details action label names expanded state and medication`() {
        val plan = plan(name = "Vitamin D")

        assertEquals("Show details for Vitamin D", medicationDetailsActionLabel(plan, expanded = false))
        assertEquals("Hide details for Vitamin D", medicationDetailsActionLabel(plan, expanded = true))
    }

    @Test
    fun `schedule control labels name the medication plan`() {
        val plan = plan(name = "Vitamin D")

        assertEquals("Skip Vitamin D today", medicationSkipActionLabel(plan))
        assertEquals("Pause Vitamin D", medicationPauseActionLabel(plan))
        assertEquals("Resume Vitamin D", medicationResumeActionLabel(plan))
        assertEquals("Snooze Vitamin D for 30 minutes", medicationSnoozeActionLabel(plan, minutes = 30))
    }

    @Test
    fun `context sheet action labels name every primary medication action`() {
        val plan = plan(name = "Vitamin D")

        val labels = medicationContextSheetActionLabels(plan)

        assertEquals("Mark Vitamin D taken", labels.taken)
        assertEquals("Mark Vitamin D missed", labels.missed)
        assertEquals("Edit Vitamin D", labels.edit)
        assertEquals("Archive Vitamin D", labels.archive)
    }

    @Test
    fun `attention dismiss action label names the alert`() {
        assertEquals("Dismiss Medication alarms are off alert", medicationAttentionDismissActionLabel("Medication alarms are off"))
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
