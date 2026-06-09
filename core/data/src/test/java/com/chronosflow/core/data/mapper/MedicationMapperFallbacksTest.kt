package com.chronosflow.core.data.mapper

import com.chronosflow.core.data.model.MedicationDoseEventEntity
import com.chronosflow.core.data.model.MedicationSafetyProfileEntity
import com.chronosflow.core.data.model.MedicationScheduleEntity
import com.chronosflow.core.domain.model.MedicationDoseEventType
import com.chronosflow.core.domain.model.MedicationSchedule
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import com.chronosflow.core.domain.model.PlannerRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MedicationMapperFallbacksTest {

    @Test
    fun `medication schedule falls back to daily when recurrence type is invalid`() {
        val entity = MedicationScheduleEntity(
            id = "schedule-med-1",
            medicationPlanId = "med-1",
            recurrenceType = "BROKEN_TYPE",
            intervalCount = 2,
            weekdaysCsv = "MONDAY,INVALID",
            doseTimesCsv = "480,  600,notanumber,600",
            plannerVisible = true,
            pausedUntil = null,
            windowMinutes = 15,
            isPrn = false,
            createdAt = Instant.parse("2026-05-01T10:00:00Z"),
            updatedAt = Instant.parse("2026-05-01T10:30:00Z")
        )

        val schedule = entity.toDomain()

        assertEquals(PlannerRecurrenceType.DAILY, schedule.recurrence.type)
        assertEquals(2, schedule.recurrence.interval)
        assertEquals(listOf(480, 600), schedule.recurrence.timesOfDayMinutes)
    }

    @Test
    fun `medication dose event falls back to taken when stored enum is invalid`() {
        val entity = MedicationDoseEventEntity(
            id = "dose-1",
            medicationPlanId = "med-1",
            eventType = "UNKNOWN",
            eventDate = LocalDate.of(2026, 5, 1),
            recordedAt = Instant.parse("2026-05-01T11:00:00Z"),
            scheduledMinuteOfDay = 540,
            reason = null,
            doseAmount = "2 tabs"
        )

        val domain = entity.toDomain()

        assertEquals(MedicationDoseEventType.TAKEN, domain.type)
        assertEquals("dose-1", domain.id)
    }

    @Test
    fun `medication safety profile parses and serializes caution list`() {
        val entity = MedicationSafetyProfileEntity(
            medicationPlanId = "med-2",
            form = "tablet",
            route = "oral",
            strength = "10mg",
            instructions = "Take with water",
            mealTiming = "With food",
            supplyRemaining = 4,
            refillThreshold = 5,
            pharmacyName = "Care Pharmacy",
            prescriberName = "Dr. Smith",
            cautionsCsv = " sleepy | nausea |"
        )

        val profile = entity.toDomain()

        assertEquals("med-2", profile.medicationPlanId)
        assertEquals(listOf("sleepy", "nausea"), profile.cautions)
        assertEquals(4, profile.supplyRemaining)
        assertTrue(profile.refillSoon)

        val roundTripped = profile.toEntity()
        assertEquals("med-2", roundTripped.medicationPlanId)
        assertEquals("sleepy|nausea", roundTripped.cautionsCsv)
    }

    @Test
    fun `medication schedule serialization normalizes times before persistence`() {
        val schedule = MedicationSchedule(
            id = "schedule-med-3",
            medicationPlanId = "med-3",
            recurrence = PlannerRecurrence(
                type = com.chronosflow.core.domain.model.PlannerRecurrenceType.MULTIPLE_TIMES_DAILY,
                timesOfDayMinutes = listOf(1450, -5, 300, 300)
            ),
            plannerVisible = false,
            windowMinutes = 20
        )

        val entity = schedule.toEntity(
            createdAt = Instant.parse("2026-05-01T10:00:00Z"),
            updatedAt = Instant.parse("2026-05-01T10:10:00Z")
        )

        assertEquals("10,300,1435", entity.doseTimesCsv)
    }
}
