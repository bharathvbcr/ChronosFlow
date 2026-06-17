package com.chronosflow.core.notifications

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CurrentBlockNotificationTest {
    @Test
    fun `completed block is not shown as the current block`() {
        val completed = block(id = "done", startMinute = 9 * 60, durationMinutes = 60)
            .copy(actualStartMinuteOfDay = 9 * 60, actualEndMinuteOfDay = 9 * 60 + 30)

        val (active, next) = selectCurrentAndNextBlock(listOf(completed), nowMinute = 9 * 60 + 45)

        assertNull(active)
        assertNull(next)
    }

    @Test
    fun `selection picks the active in-window block and the next upcoming one`() {
        val current = block(id = "current", startMinute = 9 * 60, durationMinutes = 60)
        val upcoming = block(id = "upcoming", startMinute = 11 * 60, durationMinutes = 30)

        val (active, next) = selectCurrentAndNextBlock(listOf(upcoming, current), nowMinute = 9 * 60 + 30)

        assertEquals("current", active?.id)
        assertEquals("upcoming", next?.id)
    }

    private fun block(id: String, startMinute: Int, durationMinutes: Int): TimeBlock {
        val now = Instant.parse("2026-05-08T12:00:00Z")
        return TimeBlock(
            id = id,
            date = LocalDate.of(2026, 5, 8),
            title = id,
            category = "WORK",
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            timezone = "UTC",
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "TEST",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun `upcoming block count tallies only eligible future blocks`() {
        val current = block(id = "current", startMinute = 9 * 60, durationMinutes = 60)   // active now
        val nextA = block(id = "a", startMinute = 11 * 60, durationMinutes = 30)
        val nextB = block(id = "b", startMinute = 13 * 60, durationMinutes = 30)
        val completed = block(id = "done", startMinute = 14 * 60, durationMinutes = 30)
            .copy(actualEndMinuteOfDay = 14 * 60 + 30)

        // now = 9:30 → current excluded (not after now), completed excluded, two future blocks remain.
        assertEquals(2, upcomingBlockCount(listOf(current, nextA, nextB, completed), nowMinute = 9 * 60 + 30))
        // After the last block starts, nothing is left.
        assertEquals(0, upcomingBlockCount(listOf(current, nextA, nextB), nowMinute = 13 * 60 + 15))
    }

    @Test
    fun `focus action is offered for work-style blocks but not rest blocks`() {
        assertTrue(blockSupportsFocus("WORK"))
        assertTrue(blockSupportsFocus("study"))
        assertTrue(blockSupportsFocus("Exercise"))
        assertTrue(blockSupportsFocus("calendar"))
        assertTrue(blockSupportsFocus(""))           // custom/unknown keeps the action
        assertFalse(blockSupportsFocus("break"))
        assertFalse(blockSupportsFocus("BREAK"))
        assertFalse(blockSupportsFocus(" Sleep "))
        assertFalse(blockSupportsFocus("meal"))
        assertFalse(blockSupportsFocus("food"))
    }

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
    fun `window text shows the block range, collapsing a shared meridian`() {
        assertEquals("2:00–2:30 PM", currentBlockWindowText(startMinute = 14 * 60, endMinute = 14 * 60 + 30))
        // Crosses noon → both meridians are shown.
        assertEquals("11:30 AM–12:15 PM", currentBlockWindowText(startMinute = 11 * 60 + 30, endMinute = 12 * 60 + 15))
    }

    @Test
    fun `next render clamps to a periodic refresh while a live surface is showing`() {
        // Showing, boundary far off (60m away) → wake at the 10m periodic refresh instead.
        assertEquals(
            9 * 60 + 10,
            nextRenderMinute(boundaryMinute = 10 * 60, nowMinute = 9 * 60, showingLive = true, refreshIntervalMinutes = 10)
        )
        // Showing, boundary nearer than the next refresh → wake at the boundary (don't overshoot).
        assertEquals(
            9 * 60 + 5,
            nextRenderMinute(boundaryMinute = 9 * 60 + 5, nowMinute = 9 * 60, showingLive = true, refreshIntervalMinutes = 10)
        )
        // Not showing (idle/pre-day) → no periodic wakes, just the boundary.
        assertEquals(
            10 * 60,
            nextRenderMinute(boundaryMinute = 10 * 60, nowMinute = 9 * 60, showingLive = false, refreshIntervalMinutes = 10)
        )
        // No boundary at all → nothing to schedule.
        assertNull(nextRenderMinute(boundaryMinute = null, nowMinute = 9 * 60, showingLive = true, refreshIntervalMinutes = 10))
    }

    @Test
    fun `notification text names the time window and next event`() {
        assertEquals(
            "2:00–2:30 PM · Next: Deep work at 3:00 PM",
            currentBlockNotificationText(
                startMinute = 14 * 60,
                endMinute = 14 * 60 + 30,
                upcoming = listOf(UpcomingGlance("Deep work", 15 * 60, isBreak = false))
            )
        )
        assertEquals(
            "10:00–11:00 PM · Last block of the day",
            currentBlockNotificationText(startMinute = 22 * 60, endMinute = 23 * 60, upcoming = emptyList())
        )
    }

    @Test
    fun `notification text surfaces the next break and the next event in time order`() {
        // Break at 2:45, event at 3:00 → both shown, break first (chronological).
        assertEquals(
            "2:00–2:30 PM · Break at 2:45 PM · Next: Standup at 3:00 PM",
            currentBlockNotificationText(
                startMinute = 14 * 60,
                endMinute = 14 * 60 + 30,
                upcoming = listOf(
                    UpcomingGlance("Break", 14 * 60 + 45, isBreak = true),
                    UpcomingGlance("Standup", 15 * 60, isBreak = false)
                )
            )
        )
    }

    @Test
    fun `a custom-titled break keeps its name`() {
        assertEquals(
            "12:30–1:00 PM · Break: Lunch at 1:00 PM",
            currentBlockNotificationText(
                startMinute = 12 * 60 + 30,
                endMinute = 13 * 60,
                upcoming = listOf(UpcomingGlance("Lunch", 13 * 60, isBreak = true))
            )
        )
    }

    @Test
    fun `select upcoming finds the next event and next break, skipping completed`() {
        val completed = block(id = "done", startMinute = 13 * 60, durationMinutes = 30)
            .copy(category = "BREAK", actualEndMinuteOfDay = 13 * 60 + 30)
        val nextEvent = block(id = "standup", startMinute = 14 * 60, durationMinutes = 30) // WORK
        val laterEvent = block(id = "review", startMinute = 16 * 60, durationMinutes = 30) // WORK
        val nextBreak = block(id = "coffee", startMinute = 15 * 60, durationMinutes = 15)
            .copy(category = "break", title = "Coffee")

        val glances = selectUpcomingGlances(
            listOf(laterEvent, nextBreak, completed, nextEvent),
            nowMinute = 13 * 60 + 45
        )

        // One event (the nearest) + one break, chronologically ordered; completed break ignored.
        assertEquals(2, glances.size)
        assertEquals("standup", glances[0].title)
        assertFalse(glances[0].isBreak)
        assertEquals("Coffee", glances[1].title)
        assertTrue(glances[1].isBreak)
    }

    @Test
    fun `previous block end picks the latest finished block, ignoring completed and future ones`() {
        val morning = block(id = "morning", startMinute = 9 * 60, durationMinutes = 60)    // ends 10:00
        val midday = block(id = "midday", startMinute = 10 * 60 + 30, durationMinutes = 30) // ends 11:00
        val future = block(id = "future", startMinute = 13 * 60, durationMinutes = 60)      // ends 14:00
        val completed = block(id = "done", startMinute = 8 * 60, durationMinutes = 30)
            .copy(actualEndMinuteOfDay = 8 * 60 + 30)

        // now = 11:15 → latest eligible end at/before now is midday's 11:00.
        assertEquals(
            11 * 60,
            previousBlockEndMinute(listOf(future, morning, completed, midday), nowMinute = 11 * 60 + 15)
        )
        // Nothing has finished yet at 8:30.
        assertNull(previousBlockEndMinute(listOf(morning, future), nowMinute = 8 * 60 + 30))
    }

    @Test
    fun `up next text states the absolute start time`() {
        assertEquals("Starts at 1:00 PM", upNextNotificationText(13 * 60, emptyList(), redact = false))
        assertEquals("Starts at 9:05 AM", upNextNotificationText(9 * 60 + 5, emptyList(), redact = false))
    }

    @Test
    fun `up next text previews the item following the upcoming block`() {
        // Upcoming block at 1:00, a break right after at 1:30 → previewed.
        assertEquals(
            "Starts at 1:00 PM · Break at 1:30 PM",
            upNextNotificationText(
                13 * 60,
                following = listOf(UpcomingGlance("Break", 13 * 60 + 30, isBreak = true)),
                redact = false
            )
        )
        // A following event keeps its name under the "Then" label.
        assertEquals(
            "Starts at 1:00 PM · Then: Gym at 2:00 PM",
            upNextNotificationText(
                13 * 60,
                following = listOf(UpcomingGlance("Gym", 14 * 60, isBreak = false)),
                redact = false
            )
        )
        // Under redaction the following title is dropped, the time kept.
        assertEquals(
            "Starts at 1:00 PM · Then at 2:00 PM",
            upNextNotificationText(
                13 * 60,
                following = listOf(UpcomingGlance("Gym", 14 * 60, isBreak = false)),
                redact = true
            )
        )
    }

    @Test
    fun `up next shows only within the lookahead window`() {
        // 60 min away → within the 120 min window.
        assertTrue(shouldShowUpNext(nextStartMinute = 9 * 60, lookaheadMinutes = 120, nowMinute = 8 * 60))
        // 3 hours away (e.g. 6am before a 9am first block) → suppressed.
        assertFalse(shouldShowUpNext(nextStartMinute = 9 * 60, lookaheadMinutes = 120, nowMinute = 6 * 60))
    }

    @Test
    fun `up next refresh wakes at the window open then at the block start`() {
        // 6am, next at 9am, 120 window → first wake at 7am (window open).
        assertEquals(7 * 60, upNextRefreshMinute(nextStartMinute = 9 * 60, lookaheadMinutes = 120, nowMinute = 6 * 60))
        // 8am (already inside the window) → next wake at the 9am start.
        assertEquals(9 * 60, upNextRefreshMinute(nextStartMinute = 9 * 60, lookaheadMinutes = 120, nowMinute = 8 * 60))
    }

    @Test
    fun `redacted current block text keeps times but drops every title`() {
        assertEquals(
            "2:00–2:30 PM · Break at 2:45 PM · Next at 3:00 PM",
            redactedCurrentBlockText(
                startMinute = 14 * 60,
                endMinute = 14 * 60 + 30,
                upcoming = listOf(
                    UpcomingGlance("Coffee", 14 * 60 + 45, isBreak = true),
                    UpcomingGlance("Standup", 15 * 60, isBreak = false)
                )
            )
        )
        assertEquals(
            "10:00–11:00 PM · Last block of the day",
            redactedCurrentBlockText(startMinute = 22 * 60, endMinute = 23 * 60, upcoming = emptyList())
        )
    }
}
