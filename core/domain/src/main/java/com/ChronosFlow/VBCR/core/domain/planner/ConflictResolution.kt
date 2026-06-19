package com.ChronosFlow.VBCR.core.domain.planner

/**
 * Outcome of [PlannerService.resolveConflicts]: the movable blocks that were relocated to clear
 * overlaps, plus how many conflicting blocks could NOT be auto-resolved (e.g. two immovable blocks
 * overlapping each other). [hadConflicts] is true whenever the day started with at least one
 * overlap, even if every overlap was then successfully repaired.
 */
data class ConflictResolutionResult(
    val moves: List<ResolvedBlockMove>,
    val unresolvedConflictCount: Int,
    val hadConflicts: Boolean,
)

/** A single block relocation, retained with its original start so the repair can be undone. */
data class ResolvedBlockMove(
    val blockId: String,
    val blockTitle: String,
    val originalStartMinute: Int,
    val newStartMinute: Int,
)

/**
 * Human-readable summary of a conflict repair for a snackbar/undo affordance, e.g.
 * "Moved 2 blocks to clear conflicts — 1 still need a manual change".
 */
fun conflictRepairSummaryMessage(result: ConflictResolutionResult): String {
    val moved = result.moves.size
    val base = "Moved $moved block${if (moved == 1) "" else "s"} to clear conflicts"
    return if (result.unresolvedConflictCount > 0) {
        "$base — ${result.unresolvedConflictCount} still need a manual change"
    } else {
        base
    }
}

/**
 * Message for when nothing could be auto-resolved (every overlap is between immovable blocks), so
 * the user has to step in. Used by the "Repair with AI" fallback and the "Fix schedule" no-op case.
 */
fun conflictRepairManualMessage(result: ConflictResolutionResult): String {
    val count = result.unresolvedConflictCount
    return "Couldn't auto-resolve $count conflict${if (count == 1) "" else "s"} — " +
        "locked or fixed blocks overlap. Adjust them manually."
}
