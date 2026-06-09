package com.chronosflow.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TimeBlockTest {

    private val baseBlock = TimeBlock(
        id = "1",
        date = LocalDate.now(),
        title = "Test Block",
        category = "WORK",
        startMinuteOfDay = 600, // 10:00 AM
        durationMinutes = 60,
        timezone = "UTC",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.RESIZABLE,
        energyLevel = EnergyIntensity.HIGH,
        source = "USER",
        taskId = null,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `plannedEndMinuteOfDay calculation`() {
        assertEquals(660, baseBlock.plannedEndMinuteOfDay)
    }

    @Test
    fun `plannedEndMinuteOfDay wraps around midnight`() {
        val midnightBlock = baseBlock.copy(startMinuteOfDay = 1410, durationMinutes = 60) // 11:30 PM
        assertEquals(30, midnightBlock.plannedEndMinuteOfDay)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid startMinuteOfDay throws exception`() {
        baseBlock.copy(startMinuteOfDay = 1500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid durationMinutes throws exception`() {
        baseBlock.copy(durationMinutes = 0)
    }

    @Test
    fun `withUpdatedStart updates properties correctly`() {
        val updated = baseBlock.withUpdatedStart(720)
        assertEquals(720, updated.startMinuteOfDay)
    }

    @Test
    fun `withUpdatedDuration updates properties correctly`() {
        val updated = baseBlock.withUpdatedDuration(120)
        assertEquals(120, updated.durationMinutes)
    }
}
