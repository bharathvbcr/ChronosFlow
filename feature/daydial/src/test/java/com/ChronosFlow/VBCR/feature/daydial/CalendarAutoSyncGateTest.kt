package com.ChronosFlow.VBCR.feature.daydial

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAutoSyncGateTest {
    @Test
    fun `each date syncs exactly once per session`() {
        val gate = CalendarAutoSyncGate()
        val today = LocalDate.parse("2026-06-09")

        assertTrue(gate.shouldSync(today))
        assertFalse(gate.shouldSync(today))
    }

    @Test
    fun `revisiting a date after browsing other days stays gated`() {
        val gate = CalendarAutoSyncGate()
        val today = LocalDate.parse("2026-06-09")
        val tomorrow = today.plusDays(1)

        assertTrue(gate.shouldSync(today))
        assertTrue(gate.shouldSync(tomorrow))
        assertFalse(gate.shouldSync(today))
        assertFalse(gate.shouldSync(tomorrow))
    }
}
