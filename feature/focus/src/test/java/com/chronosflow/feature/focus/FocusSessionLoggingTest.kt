package com.chronosflow.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusSessionLoggingTest {
    @Test
    fun `elapsed minutes from timer snapshot`() {
        assertEquals(10, focusElapsedMinutes(totalSeconds = 1500, timeLeftSeconds = 900, plannedDurationMinutes = 25))
        assertEquals(1, focusElapsedMinutes(totalSeconds = 1500, timeLeftSeconds = 1490, plannedDurationMinutes = 25))
        assertEquals(25, focusElapsedMinutes(totalSeconds = 1500, timeLeftSeconds = 0, plannedDurationMinutes = 25))
        assertEquals(10, focusElapsedMinutes(totalSeconds = 1500, timeLeftSeconds = 600, plannedDurationMinutes = 10))
    }

    @Test
    fun `elapsed minutes clamp handles empty plan and zero elapsed`() {
        assertEquals(1, focusElapsedMinutes(totalSeconds = 1200, timeLeftSeconds = 1200, plannedDurationMinutes = 0))
    }

    @Test
    fun `elapsed minutes cap respects planned duration when short`() {
        assertEquals(
            12,
            focusElapsedMinutes(
                totalSeconds = 3000,
                timeLeftSeconds = 0,
                plannedDurationMinutes = 12
            )
        )
    }
}
