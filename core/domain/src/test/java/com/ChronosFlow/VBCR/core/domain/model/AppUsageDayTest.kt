package com.ChronosFlow.VBCR.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AppUsageDayTest {

    private fun day(offset: Long, productive: Int, distracting: Int) = AppUsageDay(
        date = LocalDate.of(2026, 6, 1).plusDays(offset),
        productiveMinutes = productive,
        distractingMinutes = distracting,
        neutralMinutes = 0
    )

    @Test
    fun `focusRatioTrend compares recent half against earlier half`() {
        val window = listOf(
            day(0, productive = 30, distracting = 70),
            day(1, productive = 30, distracting = 70),
            day(2, productive = 70, distracting = 30),
            day(3, productive = 70, distracting = 30)
        )

        val trend = focusRatioTrend(window)

        assertEquals(30, trend?.earlierPercent)
        assertEquals(70, trend?.recentPercent)
        assertEquals(40, trend?.deltaPercent)
        assertTrue(trend?.isImproving == true)
    }

    @Test
    fun `focusRatioTrend is null when a half has no tracked time`() {
        val window = listOf(
            day(0, productive = 50, distracting = 50),
            day(1, productive = 50, distracting = 50),
            day(2, productive = 0, distracting = 0),
            day(3, productive = 0, distracting = 0)
        )

        assertNull(focusRatioTrend(window))
    }

    @Test
    fun `focusRatioTrend is null for a window shorter than two days`() {
        assertNull(focusRatioTrend(emptyList()))
        assertNull(focusRatioTrend(listOf(day(0, productive = 60, distracting = 40))))
    }

    @Test
    fun `distractionNudge fires when today is well above the usual average`() {
        val today = LocalDate.of(2026, 6, 4)
        val window = listOf(
            day(0, productive = 100, distracting = 20),
            day(1, productive = 100, distracting = 20),
            day(2, productive = 100, distracting = 20),
            AppUsageDay(today, productiveMinutes = 40, distractingMinutes = 120, neutralMinutes = 0)
        )

        val nudge = distractionNudge(window, today)

        assertEquals(120, nudge?.todayDistractingMinutes)
        assertEquals(20, nudge?.averageDistractingMinutes)
    }

    @Test
    fun `distractionNudge is null when today is within the usual range`() {
        val today = LocalDate.of(2026, 6, 4)
        val window = listOf(
            day(0, productive = 100, distracting = 60),
            day(1, productive = 100, distracting = 60),
            AppUsageDay(today, productiveMinutes = 100, distractingMinutes = 60, neutralMinutes = 0)
        )

        assertNull(distractionNudge(window, today))
    }

    @Test
    fun `bestFocusDay picks the day with the most focused time`() {
        val window = listOf(
            day(0, productive = 60, distracting = 30),
            day(1, productive = 200, distracting = 30),
            day(2, productive = 120, distracting = 30)
        )

        assertEquals(LocalDate.of(2026, 6, 2), bestFocusDay(window)?.date)
        assertNull(bestFocusDay(emptyList()))
    }
}
