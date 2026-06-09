package com.chronosflow.core.domain.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationPlannerProjectionTest {

    @Test
    fun `buildLegacyMedicationSchedule expands multiple daily reminder times`() {
        val schedule = buildLegacyMedicationSchedule(
            medicationPlanId = "med-1",
            primaryReminderMinute = 8 * 60,
            secondaryReminderMinute = 20 * 60,
            plannerVisible = true
        )

        assertEquals(PlannerRecurrenceType.MULTIPLE_TIMES_DAILY, schedule.recurrence.type)
        assertEquals(listOf(8 * 60, 20 * 60), schedule.recurrence.timesOfDayMinutes)
        assertTrue(schedule.plannerVisible)
    }

    @Test
    fun `deriveMedicationAnalytics counts taken missed and snoozed doses`() {
        val today = LocalDate.of(2026, 5, 25)
        val now = Instant.parse("2026-05-25T14:00:00Z")
        val events = listOf(
            MedicationDoseEvent(
                id = "taken-1",
                medicationPlanId = "med-1",
                type = MedicationDoseEventType.TAKEN,
                eventDate = today,
                recordedAt = now,
                scheduledMinuteOfDay = 8 * 60,
                reason = null,
                doseAmount = "1"
            ),
            MedicationDoseEvent(
                id = "taken-2",
                medicationPlanId = "med-1",
                type = MedicationDoseEventType.TAKEN,
                eventDate = today.minusDays(1),
                recordedAt = now.minusSeconds(24 * 60 * 60),
                scheduledMinuteOfDay = 8 * 60,
                reason = null,
                doseAmount = "1"
            ),
            MedicationDoseEvent(
                id = "taken-3",
                medicationPlanId = "med-1",
                type = MedicationDoseEventType.TAKEN,
                eventDate = today.minusDays(2),
                recordedAt = now.minusSeconds(2 * 24 * 60 * 60),
                scheduledMinuteOfDay = 8 * 60,
                reason = null,
                doseAmount = "1"
            ),
            MedicationDoseEvent(
                id = "missed-1",
                medicationPlanId = "med-1",
                type = MedicationDoseEventType.MISSED,
                eventDate = today.minusDays(3),
                recordedAt = now.minusSeconds(3 * 24 * 60 * 60),
                scheduledMinuteOfDay = 8 * 60,
                reason = "Forgot",
                doseAmount = null
            ),
            MedicationDoseEvent(
                id = "snoozed-1",
                medicationPlanId = "med-1",
                type = MedicationDoseEventType.SNOOZED,
                eventDate = today,
                recordedAt = now.minusSeconds(30 * 60),
                scheduledMinuteOfDay = 8 * 60,
                reason = "Delayed",
                doseAmount = null
            )
        )

        val analytics = deriveMedicationAnalytics(
            events = events,
            today = today,
            supplyRemaining = 2,
            refillThreshold = 3
        )

        assertEquals(0.75f, analytics.adherenceRate, 0.0001f)
        assertEquals(3, analytics.takenCountLast7Days)
        assertEquals(1, analytics.missedCountLast14Days)
        assertEquals(1, analytics.snoozedCountLast7Days)
        assertTrue(analytics.refillSoon)
    }
}
