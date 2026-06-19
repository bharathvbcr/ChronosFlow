package com.ChronosFlow.VBCR.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChronosTimeUtilsTest {
    @Test
    fun `parseFlexibleMinute supports 12 hour and 24 hour formats`() {
        assertEquals(9 * 60 + 5, parseFlexibleMinute("9:05"))
        assertEquals(9 * 60 + 5, parseFlexibleMinute("9:05 AM"))
        assertEquals(21 * 60, parseFlexibleMinute("21:00"))
        assertEquals(12 * 60, parseFlexibleMinute("12:00 PM"))
        assertEquals(0, parseFlexibleMinute("12:00 AM"))
    }

    @Test
    fun `parseFlexibleMinute rejects invalid times`() {
        assertNull(parseFlexibleMinute("25:00"))
        assertNull(parseFlexibleMinute("12:60"))
        assertNull(parseFlexibleMinute("foo"))
        assertNull(parseFlexibleMinute(""))
    }

    @Test
    fun `formatDisplayMinute normalizes to 12 hour label`() {
        assertEquals("12:00 AM", formatDisplayMinute(0))
        assertEquals("12:00 AM", formatDisplayMinute(24 * 60))
        assertEquals("11:59 PM", formatDisplayMinute(-1))
        assertEquals("8:05 PM", formatDisplayMinute(1205))
    }

    @Test
    fun `formatClockMinute wraps via 24 hour modulo`() {
        assertEquals("01:05", formatClockMinute(65))
        assertEquals("23:59", formatClockMinute(-1))
        assertEquals("00:00", formatClockMinute(24 * 60))
    }
}

