package com.chronosflow.core.domain.planner

enum class PlannerFeedbackType {
    SNAP_TO_INCREMENT,
    CONFLICT_BOUNDARY,
    LOCKED_BLOCK_COLLISION,
    SUCCESSFUL_DROP
}

data class PlannerFeedback(
    val type: PlannerFeedbackType,
    val blockId: String,
    val message: String? = null
)
