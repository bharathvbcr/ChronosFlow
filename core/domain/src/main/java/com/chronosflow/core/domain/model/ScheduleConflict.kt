package com.chronosflow.core.domain.model

data class ScheduleConflict(
    val primaryBlockId: String,
    val conflictingBlockId: String,
    val overlapStartMinute: Int,
    val overlapEndMinute: Int,
    val severity: ScheduleConflictSeverity,
    val reason: String
)

enum class ScheduleConflictSeverity {
    INFO,
    WARNING,
    BLOCKING
}
