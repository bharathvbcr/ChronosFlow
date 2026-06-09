package com.chronosflow.core.domain.model

import java.time.Instant

sealed interface FocusSessionState {
    data object Idle : FocusSessionState
    data class Preparing(val blockId: String) : FocusSessionState
    data class Running(
        val sessionId: String,
        val blockId: String?,
        val startedAt: Instant,
        val plannedEndAt: Instant
    ) : FocusSessionState
    data class Paused(
        val sessionId: String,
        val blockId: String?,
        val startedAt: Instant,
        val pausedAt: Instant,
        val plannedEndAt: Instant
    ) : FocusSessionState
    data class Extending(val sessionId: String, val newPlannedEndAt: Instant) : FocusSessionState
    data class Completing(val sessionId: String) : FocusSessionState
    data class Completed(val sessionId: String, val actualSegments: List<ActualTimeSegment>) : FocusSessionState
    data class Reviewing(val sessionId: String) : FocusSessionState
    data class Archived(val sessionId: String) : FocusSessionState
    data class InterruptedBySystem(val sessionId: String, val reason: String) : FocusSessionState
    data class PermissionBlocked(val permission: String) : FocusSessionState
    data class ServiceKilledRecoverable(val sessionId: String) : FocusSessionState
}

sealed interface FocusSessionEvent {
    data class Prepare(val blockId: String) : FocusSessionEvent
    data class Start(val sessionId: String, val blockId: String?, val now: Instant, val plannedEndAt: Instant) : FocusSessionEvent
    data class Pause(val now: Instant) : FocusSessionEvent
    data class Resume(val now: Instant) : FocusSessionEvent
    data class Extend(val newPlannedEndAt: Instant) : FocusSessionEvent
    data object Complete : FocusSessionEvent
    data class Archive(val sessionId: String) : FocusSessionEvent
    data class Interrupt(val reason: String) : FocusSessionEvent
    data class PermissionBlocked(val permission: String) : FocusSessionEvent
    data object ServiceKilled : FocusSessionEvent
}
