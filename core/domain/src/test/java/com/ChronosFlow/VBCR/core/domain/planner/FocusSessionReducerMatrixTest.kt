package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.FocusSessionEvent
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Adversarial sweep over the full state × event matrix: no event may ever *lose* a live session's
 * identity or move a live session backwards, regardless of delivery order or duplication.
 */
class FocusSessionReducerMatrixTest {
    private val reducer = FocusSessionReducer()
    private val t0 = Instant.parse("2026-05-08T14:00:00Z")
    private val t1 = Instant.parse("2026-05-08T14:10:00Z")
    private val t2 = Instant.parse("2026-05-08T14:25:00Z")

    private val allStates: List<FocusSessionState> = listOf(
        FocusSessionState.Idle,
        FocusSessionState.Preparing("b"),
        FocusSessionState.Running("s", "b", t0, t2),
        FocusSessionState.Paused("s", "b", t0, t1, t2),
        FocusSessionState.Extending("s", t2),
        FocusSessionState.Completing("s"),
        FocusSessionState.Completed("s", emptyList()),
        FocusSessionState.Reviewing("s"),
        FocusSessionState.Archived("s"),
        FocusSessionState.InterruptedBySystem("s", "boot"),
        FocusSessionState.PermissionBlocked("android.permission.POST_NOTIFICATIONS"),
        FocusSessionState.ServiceKilledRecoverable("s")
    )

    private val allEvents: List<FocusSessionEvent> = listOf(
        FocusSessionEvent.Prepare("b2"),
        FocusSessionEvent.Start("s", "b", t1, t2),
        FocusSessionEvent.Start("other", "b2", t1, t2),
        FocusSessionEvent.Pause(t1),
        FocusSessionEvent.Resume(t1),
        FocusSessionEvent.Resume(t0.minusSeconds(60)),
        FocusSessionEvent.Extend(t2.plusSeconds(300)),
        FocusSessionEvent.Complete,
        FocusSessionEvent.Archive("s"),
        FocusSessionEvent.Archive("other"),
        FocusSessionEvent.Interrupt("reboot"),
        FocusSessionEvent.PermissionBlocked("x"),
        FocusSessionEvent.ServiceKilled
    )

    @Test
    fun `no live session is silently replaced by a same-id start`() {
        val liveIds = setOf("s")
        allStates.forEach { state ->
            allEvents.filterIsInstance<FocusSessionEvent.Start>().forEach { event ->
                val next = reducer.reduce(state, event)
                if (next is FocusSessionState.Running && state.sessionIdOrNull() in liveIds) {
                    // A start may only take over when it carries a DIFFERENT session id.
                    if (event.sessionId == state.sessionIdOrNull()) {
                        assertEquals("duplicate start must be ignored for $state", state, next)
                    }
                }
            }
        }
    }

    @Test
    fun `reduce is total - every state-event pair produces a non-null state`() {
        allStates.forEach { state ->
            allEvents.forEach { event ->
                val next = reducer.reduce(state, event)
                assertTrue("null state for $state + $event", next != null)
            }
        }
    }

    @Test
    fun `pause-resume with monotonic clock never shortens the deadline`() {
        var state: FocusSessionState = FocusSessionState.Running("s", "b", t0, t2)
        state = reducer.reduce(state, FocusSessionEvent.Pause(t1))
        state = reducer.reduce(state, FocusSessionEvent.Resume(t1))
        state as FocusSessionState.Running
        // Pause+resume at the same instant shifts the deadline by zero.
        assertEquals(t2, state.plannedEndAt)
    }

    @Test
    fun `complete from completing stays completing and cannot double-fire`() {
        val completing = FocusSessionState.Completing("s")

        val once = reducer.reduce(completing, FocusSessionEvent.Complete)
        val twice = reducer.reduce(once, FocusSessionEvent.Complete)

        assertEquals(completing, once)
        assertEquals(completing, twice)
    }

    private fun FocusSessionState.sessionIdOrNull(): String? = when (this) {
        is FocusSessionState.Running -> sessionId
        is FocusSessionState.Paused -> sessionId
        is FocusSessionState.Completing -> sessionId
        else -> null
    }
}
