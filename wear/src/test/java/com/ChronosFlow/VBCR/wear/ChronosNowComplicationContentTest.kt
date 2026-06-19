package com.ChronosFlow.VBCR.wear

import com.ChronosFlow.VBCR.wear.model.WearBlock
import com.ChronosFlow.VBCR.wear.model.WearDaySummary
import com.ChronosFlow.VBCR.wear.presentation.WearStartPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosNowComplicationContentTest {

    private val nowMillis = 1_700_000_000_000L
    private val twoPm = 14 * 60

    @Test
    fun `running focus shows a minutes-left glance, elapsed progress, and taps to focus`() {
        val content = chronosNowComplicationContent(
            summary = WearDaySummary(),
            focus = WearFocusStateStore.FocusState(
                active = true,
                title = "Deep work",
                plannedEndAtMillis = nowMillis + 23 * 60_000L,
                totalSeconds = 25 * 60
            ),
            nowMinute = twoPm,
            nowMillis = nowMillis
        )

        assertEquals("23m", content.short)
        assertEquals("Focus", content.title)
        assertTrue(content.long.startsWith("Focus · "))
        assertEquals(WearStartPage.FOCUS, content.tapPage)
        // 23 of 25 minutes remain -> ~8% elapsed.
        assertEquals(0.08f, content.progress!!, 0.01f)
        // A running session ticks live to its planned end.
        assertEquals(nowMillis + 23 * 60_000L, content.countDownToMillis)
    }

    @Test
    fun `current block shows remaining time and elapsed progress, taps to now`() {
        val content = chronosNowComplicationContent(
            summary = WearDaySummary(
                nowTitle = "Deep work",
                nowEndMinute = twoPm + 40,
                blocks = listOf(WearBlock(startMinute = 13 * 60, endMinute = twoPm + 40))
            ),
            focus = WearFocusStateStore.FocusState(),
            nowMinute = twoPm,
            nowMillis = nowMillis
        )

        assertEquals("40m", content.short)
        assertEquals("Deep work", content.title)
        assertEquals("Deep work · 40m left", content.long)
        assertEquals(WearStartPage.NOW, content.tapPage)
        // Block 13:00–14:40 (100 min); now 14:00 -> 60/100 elapsed.
        assertEquals(0.6f, content.progress!!, 0.001f)
        // End instant = now + 40 remaining minutes, for the live face countdown.
        assertEquals(nowMillis + 40 * 60_000L, content.countDownToMillis)
    }

    @Test
    fun `next block shows a relative start and no progress`() {
        val content = chronosNowComplicationContent(
            summary = WearDaySummary(nextTitle = "Gym", nextStartMinute = twoPm + 25),
            focus = WearFocusStateStore.FocusState(),
            nowMinute = twoPm,
            nowMillis = nowMillis
        )

        assertEquals("→25m", content.short)
        assertEquals("Next", content.title)
        assertEquals("Next: Gym · in 25m", content.long)
        assertNull(content.progress)
        // The next block hasn't started, so there's nothing to count down yet.
        assertNull(content.countDownToMillis)
    }

    @Test
    fun `next block also mentions an upcoming break`() {
        val content = chronosNowComplicationContent(
            summary = WearDaySummary(
                nextTitle = "Gym",
                nextStartMinute = twoPm + 25,
                nextBreakStartMinute = twoPm + 10
            ),
            focus = WearFocusStateStore.FocusState(),
            nowMinute = twoPm,
            nowMillis = nowMillis
        )

        assertTrue(content.long.contains("Next: Gym"))
        assertTrue(content.long.contains("Break "))
    }

    @Test
    fun `a synced but empty day reads as free`() {
        val content = chronosNowComplicationContent(
            summary = WearDaySummary(receivedAtMillis = nowMillis),
            focus = WearFocusStateStore.FocusState(),
            nowMinute = twoPm,
            nowMillis = nowMillis
        )

        assertEquals("Free", content.short)
        assertEquals("Nothing scheduled", content.long)
        assertNull(content.progress)
    }

    @Test
    fun `a never-synced watch points at the phone`() {
        val content = chronosNowComplicationContent(
            summary = WearDaySummary(receivedAtMillis = 0L),
            focus = WearFocusStateStore.FocusState(),
            nowMinute = twoPm,
            nowMillis = nowMillis
        )

        assertEquals("Open on phone to sync", content.long)
    }
}
