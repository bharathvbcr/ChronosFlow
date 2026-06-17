package com.chronosflow.core.notifications

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic guard for the split (Pomodoro) live-bar rendering: parsing the cross-process phase
 * plan, sizing/ordering the segments (with a live current-phase length), the cumulative progress
 * point, and the per-segment color precedence.
 */
class FocusSplitSegmentTest {
    @Test
    fun `parses the compact phase plan into second-length segments`() {
        val plan = parseFocusPhasePlan("F25,B5,F25,B5")
        assertEquals(
            listOf(
                FocusPhaseSegment(isBreak = false, durationSeconds = 1500),
                FocusPhaseSegment(isBreak = true, durationSeconds = 300),
                FocusPhaseSegment(isBreak = false, durationSeconds = 1500),
                FocusPhaseSegment(isBreak = true, durationSeconds = 300)
            ),
            plan
        )
    }

    @Test
    fun `phase plan parsing tolerates blanks, case, and malformed tokens`() {
        assertTrue(parseFocusPhasePlan(null).isEmpty())
        assertTrue(parseFocusPhasePlan("").isEmpty())
        assertTrue(parseFocusPhasePlan("   ").isEmpty())
        // Lowercase initials accepted; junk / non-positive tokens dropped.
        assertEquals(
            listOf(
                FocusPhaseSegment(isBreak = false, durationSeconds = 600),
                FocusPhaseSegment(isBreak = true, durationSeconds = 300)
            ),
            parseFocusPhasePlan("f10, x9, B5, F0, F")
        )
    }

    @Test
    fun `bar segments are empty for a flat session`() {
        assertTrue(focusBarSegments(emptyList(), currentPhaseIndex = 0, currentPhaseTotalSeconds = 1500).isEmpty())
        assertTrue(
            focusBarSegments(
                listOf(FocusPhaseSegment(isBreak = false, durationSeconds = 1500)),
                currentPhaseIndex = 0,
                currentPhaseTotalSeconds = 1500
            ).isEmpty()
        )
    }

    @Test
    fun `current segment uses the live length so a plus or minus 5m adjustment is reflected`() {
        val plan = parseFocusPhasePlan("F25,B5,F25")
        // The user extended the current (index 1, the break) phase to 8m via +/-5m while running.
        val segments = focusBarSegments(plan, currentPhaseIndex = 1, currentPhaseTotalSeconds = 8 * 60)

        assertEquals(3, segments.size)
        assertEquals(1500, segments[0].lengthSeconds)
        assertEquals(8 * 60, segments[1].lengthSeconds) // live length, not the stale 300 from the plan
        assertTrue(segments[1].isCurrent)
        assertTrue(segments[1].isBreak)
        assertEquals(1500, segments[2].lengthSeconds)
    }

    @Test
    fun `progress point sums completed phases plus elapsed in the current phase`() {
        val plan = parseFocusPhasePlan("F25,B5,F25,B5")
        val segments = focusBarSegments(plan, currentPhaseIndex = 2, currentPhaseTotalSeconds = 1500)
        // Phases 0 (1500) + 1 (300) done = 1800; 10:00 elapsed of the 25:00 third phase = +600.
        assertEquals(1800 + 600, focusSegmentedProgressPoint(segments, currentPhaseTimeLeftSeconds = 15 * 60))
        // Overrunning a non-final phase fills up to the end of that phase, not into future phases.
        assertEquals(1800 + 1500, focusSegmentedProgressPoint(segments, currentPhaseTimeLeftSeconds = -100))

        // An overrun in the FINAL phase clamps to the full bar (never beyond it).
        val lastSegments = focusBarSegments(plan, currentPhaseIndex = 3, currentPhaseTotalSeconds = 300)
        assertEquals(
            lastSegments.sumOf { it.lengthSeconds },
            focusSegmentedProgressPoint(lastSegments, currentPhaseTimeLeftSeconds = -100)
        )
    }

    @Test
    fun `segment color precedence is break then current state then steady accent`() {
        // Break segments always read as a break, regardless of being current.
        assertEquals(
            R.color.focus_break_segment,
            focusSegmentColorRes(isBreak = true, isCurrent = true, state = FocusBarState.RUNNING, sdkInt = 36)
        )
        // The current focus phase carries the live state (e.g. paused).
        assertEquals(
            focusProgressBarColorRes(FocusBarState.PAUSED, 36),
            focusSegmentColorRes(isBreak = false, isCurrent = true, state = FocusBarState.PAUSED, sdkInt = 36)
        )
        // Other focus phases stay on the steady running accent.
        assertEquals(
            focusProgressBarColorRes(FocusBarState.RUNNING, Build.VERSION_CODES.S),
            focusSegmentColorRes(
                isBreak = false,
                isCurrent = false,
                state = FocusBarState.ENDING_SOON,
                sdkInt = Build.VERSION_CODES.S
            )
        )
    }
}
