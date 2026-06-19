package com.ChronosFlow.VBCR.feature.daydial.model

import androidx.compose.runtime.Immutable

enum class FocusExecutionStatus {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED,
    SKIPPED
}

/** Whether a focus phase is a work interval or a rest break. */
enum class FocusPhaseKind { FOCUS, BREAK }

/** A single segment of a split (Pomodoro-style) focus session. */
@Immutable
data class FocusPhase(
    val kind: FocusPhaseKind,
    val durationMinutes: Int
)

@Immutable
data class FocusExecutionState(
    val blockId: String? = null,
    val blockTitle: String = "",
    val blockStartMinute: Int = 0,
    val plannedDurationMinutes: Int = 0,
    val startedEpochMs: Long = 0L,
    val status: FocusExecutionStatus = FocusExecutionStatus.IDLE,
    val pausedAtEpochMs: Long? = null,
    val totalPausedMs: Long = 0L,
    val completionNote: String = "",
    /**
     * Ordered work/break segments for a split session. Empty means a single
     * flat block (legacy behavior). When non-empty, [plannedDurationMinutes]
     * tracks the *current* phase and [currentPhaseIndex] points into this list.
     */
    val phases: List<FocusPhase> = emptyList(),
    val currentPhaseIndex: Int = 0,
    /**
     * True when the current phase's time has elapsed and the session is holding
     * (status [FocusExecutionStatus.PAUSED]) until the user taps to advance to
     * the next phase.
     */
    val awaitingPhaseAdvance: Boolean = false
) {
    val isSplitSession: Boolean get() = phases.size > 1

    val currentPhase: FocusPhase?
        get() = phases.getOrNull(currentPhaseIndex)

    val nextPhase: FocusPhase?
        get() = phases.getOrNull(currentPhaseIndex + 1)

    val isOnBreak: Boolean
        get() = currentPhase?.kind == FocusPhaseKind.BREAK

    val isLastPhase: Boolean
        get() = phases.isEmpty() || currentPhaseIndex >= phases.lastIndex
}
