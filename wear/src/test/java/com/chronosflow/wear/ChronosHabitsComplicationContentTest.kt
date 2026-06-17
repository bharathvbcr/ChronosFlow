package com.chronosflow.wear

import com.chronosflow.wear.model.WearDaySummary
import com.chronosflow.wear.presentation.WearStartPage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosHabitsComplicationContentTest {

    @Test
    fun `partial progress shows done-over-total with a fraction, taps to habits`() {
        val content = chronosHabitsComplicationContent(WearDaySummary(habitsDone = 2, habitsTotal = 5))

        assertEquals("2/5", content.short)
        assertEquals("Habits", content.title)
        assertEquals("2 of 5 habits done", content.long)
        assertEquals(0.4f, content.progress!!, 0.001f)
        assertEquals(WearStartPage.HABITS, content.tapPage)
    }

    @Test
    fun `all done reads as complete with a full bar`() {
        val content = chronosHabitsComplicationContent(WearDaySummary(habitsDone = 5, habitsTotal = 5))

        assertEquals("5/5", content.short)
        assertEquals("All 5 habits done", content.long)
        assertEquals(1f, content.progress!!, 0.001f)
    }

    @Test
    fun `a synced day with no habits reads as none`() {
        val content = chronosHabitsComplicationContent(WearDaySummary(receivedAtMillis = 1L))

        assertEquals("—", content.short)
        assertEquals("No habits today", content.long)
    }

    @Test
    fun `a never-synced watch points at the phone`() {
        assertEquals(
            "Open on phone to sync",
            chronosHabitsComplicationContent(WearDaySummary(receivedAtMillis = 0L)).long
        )
    }
}
