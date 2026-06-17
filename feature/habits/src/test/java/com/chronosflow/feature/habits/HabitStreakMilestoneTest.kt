package com.chronosflow.feature.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HabitStreakMilestoneTest {

    @Test
    fun `below the first tier returns null`() {
        assertNull(habitStreakMilestoneLabel(0))
        assertNull(habitStreakMilestoneLabel(6))
    }

    @Test
    fun `exact tier values are celebrated`() {
        assertEquals("🔥 7-day streak", habitStreakMilestoneLabel(7))
        assertEquals("🔥 30-day streak", habitStreakMilestoneLabel(30))
        assertEquals("🔥 365-day streak", habitStreakMilestoneLabel(365))
    }

    @Test
    fun `between tiers reports the highest reached tier`() {
        assertEquals("🔥 7-day streak", habitStreakMilestoneLabel(13))
        assertEquals("🔥 30-day streak", habitStreakMilestoneLabel(49))
        assertEquals("🔥 100-day streak", habitStreakMilestoneLabel(200))
    }
}
