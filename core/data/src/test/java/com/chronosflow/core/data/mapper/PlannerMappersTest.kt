package com.chronosflow.core.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.time.DayOfWeek

class PlannerMappersTest {

    @Test
    fun `weekday parsing ignores blanks invalid values and deduplicates order free set`() {
        val weekdays = "MONDAY, ,WEDNESDAY,MONDAY,BOGUS,FRIDAY".toDayOfWeekSet()

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), weekdays)
    }

    @Test
    fun `weekday serialization omitted when empty`() {
        assertEquals(null, emptySet<DayOfWeek>().toCsv())
        val csv = setOf(DayOfWeek.TUESDAY, DayOfWeek.MONDAY).toCsv()
        assertArrayEquals(arrayOf("MONDAY", "TUESDAY"), csv?.split(",")?.sorted()?.toTypedArray())
    }

    @Test
    fun `minute list handles whitespace and invalid entries`() {
        assertEquals(listOf(5, 15, 30), "5,15,abc,5, 30, 15".toMinuteList())
        assertEquals(emptyList<Int>(), "".toMinuteList())
    }

    @Test
    fun `minute csv normalizes order and removes duplicates`() {
        assertEquals("5,15,30", listOf(30, 5, 15, 5).toMinuteCsv())
        assertEquals("", emptyList<Int>().toMinuteCsv())
    }

    @Test
    fun `string list and csv conversion ignore blank entries and preserve trimming`() {
        val values = "|alpha|  |beta|gamma|".toStringList()
        val csv = listOf(" alpha ", "beta", "", "gamma", "beta").toCsv()

        assertEquals(listOf("alpha", "beta", "gamma"), values)
        assertEquals("alpha|beta|gamma", csv)
        assertEquals(null, emptyList<String>().toCsv())
    }
}
