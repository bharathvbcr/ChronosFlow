package com.chronosflow.wear

import com.chronosflow.wear.presentation.WearFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearFormatTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `a recent sync produces no stale label`() {
        assertNull(WearFormat.syncAgeLabel(now - 30 * 60_000L, now))
    }

    @Test
    fun `a never-synced device produces no label`() {
        assertNull(WearFormat.syncAgeLabel(0L, now))
    }

    @Test
    fun `staleness under two hours is shown in minutes`() {
        assertEquals("Synced 75m ago", WearFormat.syncAgeLabel(now - 75 * 60_000L, now))
    }

    @Test
    fun `staleness of two hours or more is shown in hours`() {
        assertEquals("Synced 3h ago", WearFormat.syncAgeLabel(now - 3 * 60 * 60_000L, now))
    }

    @Test
    fun `minutes under an hour read as minutes`() {
        assertEquals("45m", WearFormat.minutesWords(45))
        assertEquals("0m", WearFormat.minutesWords(0))
    }

    @Test
    fun `minutes past an hour combine hours and minutes, dropping a zero remainder`() {
        assertEquals("1h 30m", WearFormat.minutesWords(90))
        assertEquals("2h", WearFormat.minutesWords(120))
    }

    @Test
    fun `remaining label counts down to the block end then reads ending`() {
        // 14:00 now, block ends 14:23 -> 23m left.
        assertEquals("23m left", WearFormat.remainingLabel(endMinute = 14 * 60 + 23, nowMinute = 14 * 60))
        assertEquals("1h 5m left", WearFormat.remainingLabel(endMinute = 15 * 60 + 5, nowMinute = 14 * 60))
        assertEquals("ending", WearFormat.remainingLabel(endMinute = 14 * 60, nowMinute = 14 * 60))
        assertEquals("ending", WearFormat.remainingLabel(endMinute = 13 * 60, nowMinute = 14 * 60))
    }

    @Test
    fun `window label shows the block's start to end range`() {
        assertEquals("09:00–09:30", WearFormat.windowLabel(startMinute = 9 * 60, endMinute = 9 * 60 + 30))
        assertEquals("23:30–00:15", WearFormat.windowLabel(startMinute = 23 * 60 + 30, endMinute = 24 * 60 + 15))
    }

    @Test
    fun `starts-in label counts down to the next block then reads now`() {
        assertEquals("in 15m", WearFormat.startsInLabel(startMinute = 14 * 60 + 15, nowMinute = 14 * 60))
        assertEquals("in 1h 5m", WearFormat.startsInLabel(startMinute = 15 * 60 + 5, nowMinute = 14 * 60))
        assertEquals("now", WearFormat.startsInLabel(startMinute = 14 * 60, nowMinute = 14 * 60))
    }
}
