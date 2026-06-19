package com.ChronosFlow.VBCR.feature.daydial.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepLogSheetLogicTest {

    @Test
    fun `duration spans across midnight when wake is earlier than bed`() {
        // Bed 23:00, wake 07:00 -> 8h.
        assertEquals(8 * 60, sleepDurationMinutes(23 * 60, 7 * 60))
        // Bed 22:30, wake 06:00 -> 7h30.
        assertEquals(7 * 60 + 30, sleepDurationMinutes(22 * 60 + 30, 6 * 60))
    }

    @Test
    fun `duration is direct when wake is later the same day`() {
        // Afternoon nap 13:00 -> 14:30.
        assertEquals(90, sleepDurationMinutes(13 * 60, 14 * 60 + 30))
    }

    @Test
    fun `equal bed and wake times mean nothing logged`() {
        assertEquals(0, sleepDurationMinutes(23 * 60, 23 * 60))
    }

    @Test
    fun `format collapses zero-minute and zero-hour spans`() {
        assertEquals("7h 30m", formatSleepDuration(7 * 60 + 30))
        assertEquals("8h", formatSleepDuration(8 * 60))
        assertEquals("45m", formatSleepDuration(45))
    }

    @Test
    fun `summary is null until both ends are set and span is positive`() {
        assertNull(sleepDurationSummary(null, 7 * 60))
        assertNull(sleepDurationSummary(23 * 60, null))
        assertNull(sleepDurationSummary(23 * 60, 23 * 60))
        assertEquals("8h in bed", sleepDurationSummary(23 * 60, 7 * 60))
    }

    @Test
    fun `duration hint flags implausibly short or long spans only`() {
        // 8h night across midnight -> believable, no hint.
        assertNull(sleepDurationHint(23 * 60, 7 * 60))
        // Unset ends -> no hint.
        assertNull(sleepDurationHint(null, 7 * 60))
        assertNull(sleepDurationHint(23 * 60, null))
        // 2h -> too short.
        assertEquals(
            "That's a short night — check the times if that's not right.",
            sleepDurationHint(1 * 60, 3 * 60)
        )
        // Bed 07:00, wake 23:00 (classic AM/PM mix-up) -> 16h, too long.
        assertEquals(
            "Over 12 hours — double-check the AM/PM on your times.",
            sleepDurationHint(7 * 60, 23 * 60)
        )
        // Identical bed and wake times -> dedicated nudge.
        assertEquals(
            "Bed and wake times match — set different times.",
            sleepDurationHint(23 * 60, 23 * 60)
        )
    }

    @Test
    fun `quality labels cover and clamp the rating scale`() {
        assertEquals("Poor", sleepQualityLabel(1))
        assertEquals("Okay", sleepQualityLabel(3))
        assertEquals("Great", sleepQualityLabel(5))
        // Out-of-range values clamp instead of throwing.
        assertEquals("Poor", sleepQualityLabel(0))
        assertEquals("Great", sleepQualityLabel(9))
    }

    @Test
    fun `window label shows both clock times and flags overnight windows`() {
        assertNull(sleepWindowLabel(null, 7 * 60))
        assertNull(sleepWindowLabel(23 * 60, null))
        // Overnight: wake is the next day.
        assertEquals("11:00 PM → 7:00 AM (next day)", sleepWindowLabel(23 * 60, 7 * 60))
        // Same-day nap: no annotation.
        assertEquals("1:00 PM → 2:30 PM", sleepWindowLabel(13 * 60, 14 * 60 + 30))
    }

    @Test
    fun `vs-recommended compares the span to an eight hour night`() {
        // Unset ends -> null.
        assertNull(sleepVsRecommendedLabel(null, 7 * 60))
        // Exactly 8h across midnight.
        assertEquals("On target for 8 hours", sleepVsRecommendedLabel(23 * 60, 7 * 60))
        // 6h30 -> 1h30 short.
        assertEquals("1h 30m short of 8 hours", sleepVsRecommendedLabel(0, 6 * 60 + 30))
        // 9h -> 1h over.
        assertEquals("1h over 8 hours", sleepVsRecommendedLabel(22 * 60, 7 * 60))
    }

    @Test
    fun `quality content description pairs the number with its label`() {
        assertEquals("Sleep quality 1 of 5, Poor", sleepQualityContentDescription(1))
        assertEquals("Sleep quality 4 of 5, Good", sleepQualityContentDescription(4))
    }

    @Test
    fun `restfulness synthesises quality and interruptions`() {
        assertEquals("Restful night", sleepRestfulnessLabel(quality = 5, interruptions = 0))
        assertEquals("Rough night", sleepRestfulnessLabel(quality = 1, interruptions = 0))
        // Many interruptions dominate even a high self-rating.
        assertEquals("Disrupted night", sleepRestfulnessLabel(quality = 5, interruptions = 4))
        assertEquals("Average night", sleepRestfulnessLabel(quality = 3, interruptions = 1))
    }

    @Test
    fun `interruptions content description pluralises and floors at zero`() {
        assertEquals("0 interruptions", sleepInterruptionsContentDescription(0))
        assertEquals("1 interruption", sleepInterruptionsContentDescription(1))
        assertEquals("3 interruptions", sleepInterruptionsContentDescription(3))
        assertEquals("0 interruptions", sleepInterruptionsContentDescription(-2))
    }
}
