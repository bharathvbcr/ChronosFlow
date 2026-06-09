package com.chronosflow.feature.daydial.dial

import com.chronosflow.core.domain.planner.DialGeometry
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.feature.daydial.model.PlannerHapticCue
import javax.inject.Inject

data class DialDragPreview(
    val blockId: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val hapticCue: PlannerHapticCue,
    val conflictReason: String? = null
)

data class DialDragCommit(
    val blockId: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val hapticCue: PlannerHapticCue = PlannerHapticCue.SUCCESSFUL_DROP
)

/**
 * Testable boundary between pointer input and planner validation outcomes.
 * Persistence remains in ViewModel via [PlannerService] on commit only.
 */
class ChronosDialInteractionEngine @Inject constructor() {
    private val geometry: DialGeometry = DialGeometry()
    private val snapGridMinutes: Int = 15

    fun snapMinute(minute: Int): Int = geometry.snap(minute, snapGridMinutes)

    fun previewMove(
        blockId: String,
        blockStartMinute: Int,
        blockDurationMinutes: Int,
        dragStartMinute: Int,
        currentMinute: Int,
        isLocked: Boolean,
        validation: PlannerOperationResult
    ): DialDragPreview {
        if (isLocked) {
            return DialDragPreview(
                blockId = blockId,
                startMinute = blockStartMinute,
                durationMinutes = blockDurationMinutes,
                hapticCue = PlannerHapticCue.LOCKED_COLLISION,
                conflictReason = "Block is locked"
            )
        }
        val minuteDiff = circularMinuteDelta(dragStartMinute, currentMinute)
        val projected = blockStartMinute + minuteDiff
        val snapped = snapMinute(projected)
        val (cue, reason) = hapticForValidation(validation, snapped != blockStartMinute)
        return DialDragPreview(
            blockId = blockId,
            startMinute = snapped,
            durationMinutes = blockDurationMinutes,
            hapticCue = cue,
            conflictReason = reason
        )
    }

    fun commitMove(
        blockId: String,
        snappedMinute: Int,
        durationMinutes: Int
    ): DialDragCommit = DialDragCommit(
        blockId = blockId,
        startMinute = snappedMinute,
        durationMinutes = durationMinutes
    )

    fun hapticForValidation(
        result: PlannerOperationResult,
        positionChanged: Boolean
    ): Pair<PlannerHapticCue, String?> = when (result) {
        is PlannerOperationResult.Locked -> PlannerHapticCue.LOCKED_COLLISION to result.message
        is PlannerOperationResult.Conflict -> PlannerHapticCue.CONFLICT_BOUNDARY to result.message
        is PlannerOperationResult.Rejected -> PlannerHapticCue.CONFLICT_BOUNDARY to result.message
        is PlannerOperationResult.Applied -> {
            if (positionChanged) PlannerHapticCue.SNAP to null
            else PlannerHapticCue.NONE to null
        }
    }

    fun circularMinuteDelta(from: Int, to: Int): Int {
        val raw = (to - from + 1440) % 1440
        return if (raw > 720) raw - 1440 else raw
    }

    fun circularDuration(start: Int, end: Int): Int {
        val raw = (end - start + 1440) % 1440
        return if (raw == 0) 1440 else raw
    }
}
