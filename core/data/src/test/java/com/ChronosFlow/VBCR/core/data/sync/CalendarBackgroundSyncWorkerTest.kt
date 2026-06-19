package com.ChronosFlow.VBCR.core.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalendarBackgroundSyncWorkerTest {
    @Test
    fun `sync window spans the start of today through the window days ahead in the given zone`() {
        // America/New_York is EDT (UTC-4) in June, so local midnight is 04:00 UTC.
        val zone = ZoneId.of("America/New_York")
        val today = LocalDate.of(2026, 6, 12)

        val (start, end) = CalendarBackgroundSyncWorker.syncWindow(today, zone)

        assertEquals(Instant.parse("2026-06-12T04:00:00Z"), start)
        assertEquals(Instant.parse("2026-06-19T04:00:00Z"), end)
        assertEquals(7L, CalendarBackgroundSyncWorker.SYNC_WINDOW_DAYS)
    }
}
