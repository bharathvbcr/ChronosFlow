package com.ChronosFlow.VBCR.core.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class ConvertersTest {

    @Test
    fun `instant from timestamp null becomes null`() {
        assertNull(Converters().fromTimestamp(null))
    }

    @Test
    fun `timestamp from instant returns epoch millis`() {
        val instant = Instant.parse("2026-05-08T14:00:00Z")
        val converters = Converters()

        assertEquals(instant, converters.fromTimestamp(instant.toEpochMilli()))
    }

    @Test
    fun `date converters are inverse operations`() {
        val converters = Converters()
        val date = LocalDate.parse("2026-05-08")

        assertEquals(date, converters.fromLocalDate(date.toString()))
        assertEquals(date.toString(), converters.toLocalDateString(date))
    }

    @Test
    fun `date converters handle null values`() {
        val converters = Converters()

        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDateString(null))
    }

    @Test
    fun `datetime converters are inverse operations`() {
        val converters = Converters()
        val dateTime = LocalDateTime.parse("2026-05-08T14:00:00")

        assertEquals(dateTime, converters.fromLocalDateTime(dateTime.toString()))
        assertEquals(dateTime.toString(), converters.toLocalDateTimeString(dateTime))
    }

    @Test
    fun `datetime converters handle null values`() {
        val converters = Converters()

        assertNull(converters.fromLocalDateTime(null))
        assertNull(converters.toLocalDateTimeString(null))
    }
}

