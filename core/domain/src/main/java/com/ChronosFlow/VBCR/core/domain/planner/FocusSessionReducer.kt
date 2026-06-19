package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.FocusSessionEvent
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import javax.inject.Inject

class FocusSessionReducer @Inject constructor() {
    fun reduce(state: FocusSessionState, event: FocusSessionEvent): FocusSessionState {
        return when (event) {
            is FocusSessionEvent.Prepare -> FocusSessionState.Preparing(event.blockId)
            is FocusSessionEvent.Start -> FocusSessionState.Running(
                sessionId = event.sessionId,
                blockId = event.blockId,
                startedAt = event.now,
                plannedEndAt = event.plannedEndAt
            )
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
                is FocusSessionState.Paused -> FocusSessionState.Running(
                    sessionId = state.sessionId,
                    blockId = state.blockId,
                    startedAt = state.startedAt,
                    plannedEndAt = state.plannedEndAt.plusMillis(event.now.toEpochMilli() - state.pausedAt.toEpochMilli())
                )
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
            is FocusSessionEvent.Archive -> FocusSessionState.Archived(event.sessionId)
            is FocusSessionEvent.Interrupt -> when (state) {
                is FocusSessionState.Running -> FocusSessionState.InterruptedBySystem(state.sessionId, event.reason)
                is FocusSessionState.Paused -> FocusSessionState.InterruptedBySystem(state.sessionId, event.reason)
                else -> state
            }
            is FocusSessionEvent.PermissionBlocked -> FocusSessionState.PermissionBlocked(event.permission)
            FocusSessionEvent.ServiceKilled -> when (state) {
                is FocusSessionState.Running -> FocusSessionState.ServiceKilledRecoverable(state.sessionId)
                is FocusSessionState.Paused -> FocusSessionState.ServiceKilledRecoverable(state.sessionId)
                else -> state
            }
        }
    }
}
