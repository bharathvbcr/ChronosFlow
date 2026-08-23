package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.FocusSessionEvent
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import javax.inject.Inject

/**
 * Reduces focus-session events into the next persisted session state.
 *
 * Transitions that do not apply to the current state are rejected as no-ops (the current state is
 * returned unchanged) rather than silently replacing a live session. This keeps a stray or
 * duplicated event — a second [FocusSessionEvent.Start] racing the first, an archive for a stale
 * session id — from discarding an in-flight timer.
 */
class FocusSessionReducer @Inject constructor() {
    fun reduce(state: FocusSessionState, event: FocusSessionEvent): FocusSessionState {
        return when (event) {
            is FocusSessionEvent.Prepare -> when (state) {
                // Preparing is only meaningful before any session exists; it must never clobber a
                // live/paused/completing session's identity.
                is FocusSessionState.Idle, is FocusSessionState.Archived ->
                    FocusSessionState.Preparing(event.blockId)
                else -> state
            }
            is FocusSessionEvent.Start -> {
                // A duplicate Start carrying the id of the session already on the surface is
                // ignored — it would orphan the active timer. A Start with a DIFFERENT session id
                // is a deliberate supersession (e.g. a split session's next phase replacing the
                // just-finished one) and starts fresh.
                val liveSessionId = when (state) {
                    is FocusSessionState.Running -> state.sessionId
                    is FocusSessionState.Paused -> state.sessionId
                    is FocusSessionState.Completing -> state.sessionId
                    else -> null
                }
                if (liveSessionId != null && liveSessionId == event.sessionId) {
                    state
                } else {
                    FocusSessionState.Running(
                        sessionId = event.sessionId,
                        blockId = event.blockId,
                        startedAt = event.now,
                        plannedEndAt = event.plannedEndAt
                    )
                }
            }
            is FocusSessionEvent.Pause -> when (state) {
                is FocusSessionState.Running -> FocusSessionState.Paused(
                    sessionId = state.sessionId,
                    blockId = state.blockId,
                    startedAt = state.startedAt,
                    pausedAt = event.now,
                    plannedEndAt = state.plannedEndAt
                )
                else -> state
            }
            is FocusSessionEvent.Resume -> when (state) {
                is FocusSessionState.Paused -> {
                    // Only shift the deadline forward: a resumed clock that appears to run backwards
                    // (out-of-order event delivery, wall-clock rollback) must not shorten the plan.
                    val shiftedEnd = if (event.now.isAfter(state.pausedAt)) {
                        state.plannedEndAt.plusMillis(event.now.toEpochMilli() - state.pausedAt.toEpochMilli())
                    } else {
                        state.plannedEndAt
                    }
                    FocusSessionState.Running(
                        sessionId = state.sessionId,
                        blockId = state.blockId,
                        startedAt = state.startedAt,
                        plannedEndAt = shiftedEnd
                    )
                }
                else -> state
            }
            is FocusSessionEvent.Extend -> when (state) {
                is FocusSessionState.Running -> state.copy(plannedEndAt = event.newPlannedEndAt)
                is FocusSessionState.Paused -> state.copy(plannedEndAt = event.newPlannedEndAt)
                else -> state
            }
            FocusSessionEvent.Complete -> when (state) {
                is FocusSessionState.Running -> FocusSessionState.Completing(state.sessionId)
                is FocusSessionState.Paused -> FocusSessionState.Completing(state.sessionId)
                else -> state
            }
            is FocusSessionEvent.Archive -> when {
                // Archive must carry the id of the session actually being archived; archiving with
                // no session (or a mismatched id) leaves the live session untouched.
                state is FocusSessionState.Completing && state.sessionId == event.sessionId ->
                    FocusSessionState.Archived(event.sessionId)
                else -> state
            }
            is FocusSessionEvent.Interrupt -> when (state) {
                is FocusSessionState.Running -> FocusSessionState.InterruptedBySystem(state.sessionId, event.reason)
                is FocusSessionState.Paused -> FocusSessionState.InterruptedBySystem(state.sessionId, event.reason)
                else -> state
            }
            is FocusSessionEvent.PermissionBlocked -> when (state) {
                // The permission gate only applies before a session exists; it must not replace a
                // live session with a state that carries no session identity.
                is FocusSessionState.Idle, is FocusSessionState.Preparing ->
                    FocusSessionState.PermissionBlocked(event.permission)
                else -> state
            }
            FocusSessionEvent.ServiceKilled -> when (state) {
                is FocusSessionState.Running -> FocusSessionState.ServiceKilledRecoverable(state.sessionId)
                is FocusSessionState.Paused -> FocusSessionState.ServiceKilledRecoverable(state.sessionId)
                else -> state
            }
        }
    }
}
