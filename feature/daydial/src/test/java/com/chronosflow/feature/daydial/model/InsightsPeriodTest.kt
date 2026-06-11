package com.chronosflow.feature.daydial.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InsightsPeriodTest {
    private val anchor = LocalDate.of(2026, 6, 10)

    @Test
    fun `day range is the anchor only`() {
        assertEquals(listOf(anchor), InsightsPeriod.DAY.dateRange(anchor))
    }

    @Test
    fun `week range is the trailing seven days ending at the anchor`() {
        val range = InsightsPeriod.WEEK.dateRange(anchor)

        assertEquals(7, range.size)
        assertEquals(LocalDate.of(2026, 6, 4), range.first())
        assertEquals(anchor, range.last())
    }

    @Test
    fun `month range is the trailing thirty days ending at the anchor`() {
        val range = InsightsPeriod.MONTH.dateRange(anchor)

        assertEquals(30, range.size)
        assertEquals(LocalDate.of(2026, 5, 12), range.first())
        assertEquals(anchor, range.last())
    }

    @Test
    fun `range is ordered oldest to newest with no gaps`() {
        val range = InsightsPeriod.WEEK.dateRange(anchor)

        range.zipWithNext().forEach { (earlier, later) ->
            assertEquals(earlier.plusDays(1), later)
        }
    }
}
