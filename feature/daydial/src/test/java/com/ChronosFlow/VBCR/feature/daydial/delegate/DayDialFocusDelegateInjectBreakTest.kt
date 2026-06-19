package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.domain.usecase.LogActualTimeUseCase
import com.ChronosFlow.VBCR.feature.daydial.FocusExecutionState
import com.ChronosFlow.VBCR.feature.daydial.FocusExecutionStatus
import com.ChronosFlow.VBCR.feature.daydial.model.FocusPhase
import com.ChronosFlow.VBCR.feature.daydial.model.FocusPhaseKind
import com.ChronosFlow.VBCR.feature.daydial.testManualMissedBlockRegistry
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayDialFocusDelegateInjectBreakTest {

    private fun delegate(): DayDialFocusDelegate = DayDialFocusDelegate(
        repository = mockk<TimeBlockRepository>(relaxed = true),
        logActualTimeUseCase = mockk<LogActualTimeUseCase>(relaxed = true),
        manualMissedBlockRegistry = testManualMissedBlockRegistry()
    )

    // ───────────────────────────── flat session ──────────────────────────────

    @Test
    fun `injectBreakNow on a flat session promotes it to split with three phases`() {
        val d = delegate()
        // Running flat session, 25 minutes planned, started 5 minutes ago.
        d.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                plannedDurationMinutes = 25,
                startedEpochMs = System.currentTimeMillis() - 5 * 60_000,
                status = FocusExecutionStatus.RUNNING
            )
        )

        d.injectBreakNow(breakMinutes = 5)

        val state = d.focusExecutionState.value
        assertTrue("session should now be split", state.isSplitSession)
        // Phases: [focus(5), break(5), focus(15)]
        assertEquals(3, state.phases.size)
        assertEquals(FocusPhaseKind.FOCUS, state.phases[0].kind)
        assertEquals(FocusPhaseKind.BREAK, state.phases[1].kind)
        assertEquals(5, state.phases[1].durationMinutes)
        assertEquals(FocusPhaseKind.FOCUS, state.phases[2].kind)
        // The elapsed focus is already spent, so the session drops straight to the
        // phase boundary (the "Start break" prompt) rather than flashing focus at 0:00.
        assertEquals(FocusExecutionStatus.PAUSED, state.status)
        assertTrue(state.awaitingPhaseAdvance)
        // Still pointing at phase 0; advancePhase() moves into the break.
        assertEquals(0, state.currentPhaseIndex)
    }

    @Test
    fun `injectBreakNow on flat session with no remaining time after break omits trailing phase`() {
        val d = delegate()
        // 10-minute block, started 7 minutes ago — only 3 minutes remain, break is 5m.
        d.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                plannedDurationMinutes = 10,
                startedEpochMs = System.currentTimeMillis() - 7 * 60_000,
                status = FocusExecutionStatus.RUNNING
            )
        )

        d.injectBreakNow(breakMinutes = 5)

        val state = d.focusExecutionState.value
        // No trailing focus phase since remaining < 0 after break.
        val trailingFocus = state.phases.count { it.kind == FocusPhaseKind.FOCUS && it !== state.phases.first() }
        assertEquals(0, trailingFocus)
        assertTrue(state.phases.any { it.kind == FocusPhaseKind.BREAK && it.durationMinutes == 5 })
    }

    // ─────────────────────────── split session ───────────────────────────────

    @Test
    fun `injectBreakNow on a split session inserts break after current phase`() {
        val d = delegate()
        val initialPhases = listOf(
            FocusPhase(FocusPhaseKind.FOCUS, 25),
            FocusPhase(FocusPhaseKind.FOCUS, 25)
        )
        d.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                plannedDurationMinutes = 25,
                phases = initialPhases,
                currentPhaseIndex = 0,
                startedEpochMs = System.currentTimeMillis() - 5 * 60_000,
                status = FocusExecutionStatus.RUNNING
            )
        )

        d.injectBreakNow(breakMinutes = 10)

        val state = d.focusExecutionState.value
        // Original 2 phases → 3 phases after inserting break at index 1.
        assertEquals(3, state.phases.size)
        assertEquals(FocusPhaseKind.FOCUS, state.phases[0].kind)
        assertEquals(FocusPhaseKind.BREAK, state.phases[1].kind)
        assertEquals(10, state.phases[1].durationMinutes)
        assertEquals(FocusPhaseKind.FOCUS, state.phases[2].kind)
        // Still on phase 0 (the current focus interval).
        assertEquals(0, state.currentPhaseIndex)
    }

    // ──────────────────────────── guard rails ────────────────────────────────

    @Test
    fun `injectBreakNow is a no-op when session is idle`() {
        val d = delegate()
        d.setFocusStateForTest(FocusExecutionState(status = FocusExecutionStatus.IDLE))

        d.injectBreakNow(breakMinutes = 5)

        // State unchanged — still idle, no phases injected.
        assertFalse(d.focusExecutionState.value.isSplitSession)
        assertEquals(FocusExecutionStatus.IDLE, d.focusExecutionState.value.status)
    }

    // ─────────────────────────── end break early ─────────────────────────────

    @Test
    fun `endBreakNow drops to the phase boundary when a focus phase follows`() {
        val d = delegate()
        val phases = listOf(
            FocusPhase(FocusPhaseKind.FOCUS, 25),
            FocusPhase(FocusPhaseKind.BREAK, 5),
            FocusPhase(FocusPhaseKind.FOCUS, 25)
        )
        d.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                plannedDurationMinutes = 5,
                phases = phases,
                currentPhaseIndex = 1, // on the break
                startedEpochMs = System.currentTimeMillis() - 60_000,
                status = FocusExecutionStatus.RUNNING
            )
        )

        val shouldFinish = d.endBreakNow()

        val state = d.focusExecutionState.value
        assertFalse("a later focus phase exists, so don't finish", shouldFinish)
        // Same hold a break reaches when it elapses naturally.
        assertEquals(FocusExecutionStatus.PAUSED, state.status)
        assertTrue(state.awaitingPhaseAdvance)
        // Still pointing at the break — advancePhase() moves to the next focus.
        assertEquals(1, state.currentPhaseIndex)
    }

    @Test
    fun `endBreakNow signals finish when the break is the final phase`() {
        val d = delegate()
        val phases = listOf(
            FocusPhase(FocusPhaseKind.FOCUS, 25),
            FocusPhase(FocusPhaseKind.BREAK, 5)
        )
        d.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                plannedDurationMinutes = 5,
                phases = phases,
                currentPhaseIndex = 1, // on the trailing break
                startedEpochMs = System.currentTimeMillis() - 60_000,
                status = FocusExecutionStatus.RUNNING
            )
        )

        val shouldFinish = d.endBreakNow()

        assertTrue("nothing to return to, caller should finish", shouldFinish)
        // State untouched — the caller owns finishing.
        assertEquals(FocusExecutionStatus.RUNNING, d.focusExecutionState.value.status)
        assertFalse(d.focusExecutionState.value.awaitingPhaseAdvance)
    }

    @Test
    fun `endBreakNow is a no-op when not on a break`() {
        val d = delegate()
        d.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                plannedDurationMinutes = 25,
                startedEpochMs = System.currentTimeMillis() - 60_000,
                status = FocusExecutionStatus.RUNNING
            )
        )

        val shouldFinish = d.endBreakNow()

        assertFalse(shouldFinish)
        assertEquals(FocusExecutionStatus.RUNNING, d.focusExecutionState.value.status)
        assertFalse(d.focusExecutionState.value.awaitingPhaseAdvance)
    }

    @Test
    fun `injectBreakNow is a no-op when already on a break phase`() {
        val d = delegate()
        val phases = listOf(
            FocusPhase(FocusPhaseKind.FOCUS, 25),
            FocusPhase(FocusPhaseKind.BREAK, 5),
            FocusPhase(FocusPhaseKind.FOCUS, 25)
        )
        d.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                plannedDurationMinutes = 5,
                phases = phases,
                currentPhaseIndex = 1, // on the break phase
                startedEpochMs = System.currentTimeMillis() - 60_000,
                status = FocusExecutionStatus.RUNNING
            )
        )

        val phasesBefore = d.focusExecutionState.value.phases.size
        d.injectBreakNow(breakMinutes = 5)

        // Phase list must remain unchanged.
        assertEquals(phasesBefore, d.focusExecutionState.value.phases.size)
    }
}
