package com.ChronosFlow.VBCR.core.domain.planner

import java.time.LocalDate

data class BlockSpan(
    val blockId: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int
) {
    val isValid: Boolean
        get() = endMinute > startMinute
}

sealed class PlannerOperationResult(
    val message: String,
    val blockId: String?,
    val canUndo: Boolean = true,
    val snappedToMinute: Int? = null,
    val affectedBlockIds: List<String> = emptyList()
) {
    class Applied(
        message: String,
        blockId: String,
        snappedToMinute: Int?,
        affectedBlockIds: List<String> = emptyList()
    ) : PlannerOperationResult(message, blockId, true, snappedToMinute, affectedBlockIds)

    class Conflict(
        message: String,
        blockId: String,
        val conflicts: List<String>
    ) : PlannerOperationResult(message, blockId, false, affectedBlockIds = conflicts)

    class Locked(
        message: String,
        blockId: String
    ) : PlannerOperationResult(message, blockId, false)

    class Rejected(message: String, blockId: String) : PlannerOperationResult(message, blockId, false)
}

