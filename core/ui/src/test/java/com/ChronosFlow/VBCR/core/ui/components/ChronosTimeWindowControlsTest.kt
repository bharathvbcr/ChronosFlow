package com.ChronosFlow.VBCR.core.ui.components

import com.ChronosFlow.VBCR.core.ui.shell.ChronosCompactShellBottomClearance
import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosTimeWindowControlsTest {
    @Test
    fun `formatDurationLabel formats hours and minutes`() {
        assertEquals("1h 30m", formatDurationLabel(90))
        assertEquals("2h 30m", formatDurationLabel(90 + 60))
        assertEquals("0m", formatDurationLabel(0))
    }

    @Test
    fun `formatDurationLabel drops zero minute remainder`() {
        assertEquals("2h", formatDurationLabel(120))
        assertEquals("5h", formatDurationLabel(300))
    }

    @Test
    fun `formatDurationLabel handles pure minutes`() {
        assertEquals("45m", formatDurationLabel(45))
    }

    @Test
    fun `nudgeMinuteText uses parsed fallback when current value invalid`() {
        assertEquals("9:30 AM", nudgeMinuteText("bad input", 30, fallbackMinute = 9 * 60))
        assertEquals("11:59 PM", nudgeMinuteText("bad input", 10, fallbackMinute = 23 * 60 + 50))
    }

    @Test
    fun `nudgeMinuteText clamps to valid day boundaries`() {
        assertEquals("11:59 PM", nudgeMinuteText("11:59 PM", 10))
        assertEquals("12:00 AM", nudgeMinuteText("12:00 AM", -1))
    }

    @Test
    fun `applyDurationToWindow enforces minimum duration and max end minute`() {
        assertEquals(495, applyDurationToWindow(8 * 60, 0))
        assertEquals(510, applyDurationToWindow(8 * 60, 30))
        assertEquals(1440, applyDurationToWindow(1435, 30))
    }

    @Test
    fun `scaffold defaults use compact shell bottom clearance`() {
        assertEquals(ChronosCompactShellBottomClearance, ChronosScaffoldDefaults.CompactFabListBottomPadding)
        assertEquals(ChronosScaffoldDefaults.CompactFabListBottomPadding, ChronosScaffoldDefaults.StandardFabListBottomPadding)
    }
}
