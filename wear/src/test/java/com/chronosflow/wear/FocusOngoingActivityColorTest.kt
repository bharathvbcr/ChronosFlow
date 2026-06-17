package com.chronosflow.wear

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the watch focus ongoing-activity state palette — the watch counterpart to the phone's
 * tested `focusBarState`/`focusProgressBarColorRes`. Pause must win over the ending-soon emphasis,
 * a finished/zeroed timer is not "ending soon", and the running case uses the supplied accent.
 */
class FocusOngoingActivityColorTest {

    private val accent = 0xFF00AABB.toInt()

    @Test
    fun `running session uses the supplied accent`() {
        assertEquals(accent, focusOngoingActivityColor(paused = false, remainingSeconds = 25 * 60, accentColor = accent))
    }

    @Test
    fun `final stretch turns warm`() {
        assertEquals(ChronosTileUi.WARN_COLOR, focusOngoingActivityColor(paused = false, remainingSeconds = ENDING_SOON_THRESHOLD_SECONDS, accentColor = accent))
        assertEquals(ChronosTileUi.WARN_COLOR, focusOngoingActivityColor(paused = false, remainingSeconds = 1, accentColor = accent))
    }

    @Test
    fun `a finished timer is not ending-soon`() {
        assertEquals(accent, focusOngoingActivityColor(paused = false, remainingSeconds = 0, accentColor = accent))
    }

    @Test
    fun `paused is muted and takes precedence over the final stretch`() {
        assertEquals(ChronosTileUi.MUTED_COLOR, focusOngoingActivityColor(paused = true, remainingSeconds = 25 * 60, accentColor = accent))
        assertEquals(ChronosTileUi.MUTED_COLOR, focusOngoingActivityColor(paused = true, remainingSeconds = 30, accentColor = accent))
    }
}
