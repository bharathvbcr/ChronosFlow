package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock

data class PlannerConflict(
    val sourceBlockId: String,
    val targetBlockId: String,
    val overlapStartMinute: Int,
    val overlapEndMinute: Int
)

data class PlannerMoveResult(
    val allowed: Boolean,
    val snappedMinute: Int,
    val conflicts: List<PlannerConflict> = emptyList(),
    val blockedByLock: Boolean = false,
    val reason: String? = null
)

data class RebalanceSuggestion(
    val blockId: String,
    val suggestedStartMinute: Int,
    val reason: String
)

data class ZoomWindow(
    val startMinute: Int = 0,
    val durationMinutes: Int = 1440
) {
    val endMinute: Int
        get() = (startMinute + durationMinutes) % 1440

    fun wrapMinute(minute: Int): Int = ((minute % 1440) + 1440) % 1440

    fun containsMinute(minute: Int): Boolean {
        val normalized = wrapMinute(minute)
        if (durationMinutes >= 1440) return true
        val end = endMinute
        return if (startMinute <= end) {
            normalized in startMinute..<end
        } else {
            normalized >= startMinute || normalized < end
        }
    }
}

fun TimeBlock.isMoveAllowed(): Boolean {
    return flexibility != BlockFlexibility.FIXED && !isLocked
}
