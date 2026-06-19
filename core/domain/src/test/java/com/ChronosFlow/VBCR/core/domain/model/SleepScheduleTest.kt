package com.ChronosFlow.VBCR.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SleepScheduleTest {
    private val overnight = SleepSchedule(
        enabled = true,
        startMinute = 21 * 60,
        endMinute = 7 * 60
    )

    @Test
    fun `contains matches overnight quiet hours`() {
        assertTrue(overnight.contains(22 * 60))
        assertTrue(overnight.contains(6 * 60 + 30))
        assertFalse(overnight.contains(12 * 60))
    }

    @Test
    fun `intersects catches blocks that overlap sleep window`() {
        assertTrue(overnight.intersects(startMinute = 20 * 60 + 45, durationMinutes = 30))
        assertTrue(overnight.intersects(startMinute = 6 * 60 + 45, durationMinutes = 30))
        assertFalse(overnight.intersects(startMinute = 8 * 60, durationMinutes = 60))
    }

    @Test
    fun `deferInstant moves bedtime alarms to next wake time`() {
        val zoneId = ZoneId.of("America/Chicago")
        val deferred = overnight.deferInstant(
            Instant.parse("2026-05-26T03:30:00Z"),
            zoneId
        )

        assertEquals(
            Instant.parse("2026-05-26T12:00:00Z"),
            deferred
        )
    }

    @Test
    fun `deferInstant keeps pre dawn alarms on same local date`() {
        val zoneId = ZoneId.of("America/Chicago")
        val deferred = overnight.deferInstant(
            Instant.parse("2026-05-26T10:30:00Z"),
            zoneId
        )

        assertEquals(
            Instant.parse("2026-05-26T12:00:00Z"),
            deferred
        )
    }
}
