package com.ChronosFlow.VBCR.feature.daydial.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class JournalHistorySummaryTest {
    @Test
    fun `summary reports total when nothing is truncated`() {
        assertEquals(
            "5 reflections in the last 14 days",
            journalHistorySummary(total = 5, shown = 5, windowDays = 14)
        )
    }

    @Test
    fun `summary reports showing N of M when truncated`() {
        assertEquals(
            "Showing 10 of 25 reflections from the last 30 days",
            journalHistorySummary(total = 25, shown = 10, windowDays = 30)
        )
    }

    @Test
    fun `summary uses singular label for a single reflection`() {
        assertEquals(
            "1 reflection in the last 7 days",
            journalHistorySummary(total = 1, shown = 1, windowDays = 7)
        )
    }
}
