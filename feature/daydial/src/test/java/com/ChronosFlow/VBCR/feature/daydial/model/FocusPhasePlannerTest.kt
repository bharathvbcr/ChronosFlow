package com.ChronosFlow.VBCR.feature.daydial.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPhasePlannerTest {

    @Test
    fun `one hour at 25-5 splits into two work-break cycles`() {
        val phases = FocusPhasePlanner.plan(totalMinutes = 60, workMinutes = 25, breakMinutes = 5)

        assertEquals(
            listOf(
                FocusPhase(FocusPhaseKind.FOCUS, 25),
                FocusPhase(FocusPhaseKind.BREAK, 5),
                FocusPhase(FocusPhaseKind.FOCUS, 25),
                FocusPhase(FocusPhaseKind.BREAK, 5)
            ),
            phases
        )
    }

    @Test
    fun `remainder shorter than a cycle becomes a trailing focus interval`() {
        // 70 = one 30+5 cycle (35) + 30 leftover focus + 5 leftover... actually
        // 70 / 35 = 2 full cycles (70) with no remainder.
        val even = FocusPhasePlanner.plan(totalMinutes = 70, workMinutes = 30, breakMinutes = 5)
        assertEquals(4, even.size)

        // 50 minutes at 20/5: cycle = 25, two cycles = 50, no trailing focus.
        val cycles = FocusPhasePlanner.plan(totalMinutes = 50, workMinutes = 20, breakMinutes = 5)
        assertEquals(
            listOf(
                FocusPhase(FocusPhaseKind.FOCUS, 20),
                FocusPhase(FocusPhaseKind.BREAK, 5),
                FocusPhase(FocusPhaseKind.FOCUS, 20),
                FocusPhase(FocusPhaseKind.BREAK, 5)
            ),
            cycles
        )

        // 65 at 25/5: cycle = 30, two cycles = 60, remainder 5 -> trailing focus.
        val withTail = FocusPhasePlanner.plan(totalMinutes = 65, workMinutes = 25, breakMinutes = 5)
        assertEquals(FocusPhaseKind.FOCUS, withTail.last().kind)
        assertEquals(5, withTail.last().durationMinutes)
        assertEquals(65, withTail.sumOf { it.durationMinutes })
    }

    @Test
    fun `no break requested yields a single flat focus phase`() {
        val phases = FocusPhasePlanner.plan(totalMinutes = 60, workMinutes = 25, breakMinutes = 0)
        assertEquals(listOf(FocusPhase(FocusPhaseKind.FOCUS, 60)), phases)
    }

    @Test
    fun `block too short to fit a work interval plus break stays a single block`() {
        val phases = FocusPhasePlanner.plan(totalMinutes = 25, workMinutes = 25, breakMinutes = 5)
        assertEquals(listOf(FocusPhase(FocusPhaseKind.FOCUS, 25)), phases)
    }

    @Test
    fun `total minutes are preserved across the plan`() {
        val phases = FocusPhasePlanner.plan(totalMinutes = 90, workMinutes = 25, breakMinutes = 5)
        assertEquals(90, phases.sumOf { it.durationMinutes })
        assertTrue(phases.size > 1)
    }

    @Test
    fun `focusMinutes sums only focus phases`() {
        val phases = FocusPhasePlanner.plan(totalMinutes = 60, workMinutes = 25, breakMinutes = 5)
        assertEquals(50, FocusPhasePlanner.focusMinutes(phases))
    }
}
