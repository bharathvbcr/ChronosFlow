package com.chronosflow.feature.daydial.model

import androidx.compose.runtime.Immutable

enum class FocusExecutionStatus {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED,
    SKIPPED
}

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
    val completionNote: String = ""
)
