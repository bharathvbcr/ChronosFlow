package com.chronosflow.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentBlockNotificationTest {
    @Test
    fun `boundary picks the earliest upcoming edge`() {
        assertEquals(
            10 * 60,
            nextBlockBoundaryMinute(activeEndMinute = 10 * 60, nextStartMinute = 11 * 60, nowMinute = 9 * 60)
        )
        assertEquals(
            11 * 60,
            nextBlockBoundaryMinute(activeEndMinute = 9 * 60, nextStartMinute = 11 * 60, nowMinute = 10 * 60)
        )
        assertNull(nextBlockBoundaryMinute(activeEndMinute = null, nextStartMinute = null, nowMinute = 10 * 60))
        assertNull(nextBlockBoundaryMinute(activeEndMinute = 9 * 60, nextStartMinute = null, nowMinute = 10 * 60))
    }

    @Test
    fun `notification text names the end time and next block`() {
        assertEquals(
            "Until 2:30 PM · Next: Deep work at 3:00 PM",
            currentBlockNotificationText(
                endMinute = 14 * 60 + 30,
                nextTitle = "Deep work",
                nextStartMinute = 15 * 60
            )
        )
        assertEquals(
            "Until 11:00 PM · Last block of the day",
            currentBlockNotificationText(endMinute = 23 * 60, nextTitle = null, nextStartMinute = null)
        )
    }

    @Test
    fun `short critical text rounds remaining time up to whole minutes`() {
        assertEquals("25m", liveUpdateShortCriticalText(25 * 60))
        assertEquals("1m", liveUpdateShortCriticalText(30))
        assertEquals("<1m", liveUpdateShortCriticalText(0))
        assertEquals("<1m", liveUpdateShortCriticalText(-5))
    }
}
