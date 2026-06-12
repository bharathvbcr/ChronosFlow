package com.chronosflow.core.domain.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEventLogTest {

    @Test
    fun `record keeps newest entry at the head with the clock timestamp`() {
        val log = AppEventLog()
        var now = 1_000L
        log.clock = { now }

        log.record(AppEventCategory.SESSION, "first")
        now = 2_000L
        log.record(AppEventCategory.SYNC, "second")

        val entries = log.entries.value
        assertEquals(2, entries.size)
        assertEquals("second", entries[0].message)
        assertEquals(2_000L, entries[0].timestampMillis)
        assertEquals(AppEventCategory.SYNC, entries[0].category)
        assertEquals("first", entries[1].message)
    }

    @Test
    fun `record caps the buffer at 100 entries dropping the oldest`() {
        val log = AppEventLog()
        repeat(105) { index -> log.record(AppEventCategory.SESSION, "event-$index") }

        val entries = log.entries.value
        assertEquals(100, entries.size)
        assertEquals("event-104", entries.first().message)
        assertEquals("event-5", entries.last().message)
    }

    @Test
    fun `clear empties the buffer`() {
        val log = AppEventLog()
        log.record(AppEventCategory.ERROR, "boom")

        log.clear()

        assertTrue(log.entries.value.isEmpty())
    }
}
