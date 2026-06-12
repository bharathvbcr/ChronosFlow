package com.chronosflow.core.domain.planner

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.ScheduleConflictSeverity
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.repository.TimeBlockRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class PlannerService @Inject constructor(
    private val repository: TimeBlockRepository,
    private val conflictDetectionEngine: ConflictDetectionEngine = ConflictDetectionEngine()
) {
    suspend fun getBlocksForDate(date: java.time.LocalDate): List<TimeBlock> {
        return repository.getTimeBlocksByDate(date).first()
    }

    suspend fun createBlock(block: TimeBlock): PlannerOperationResult {
        return if (block.isLocked) {
            PlannerOperationResult.Rejected("Cannot create locked block", block.id)
        } else {
            val result = validatePlacement(
                date = block.date,
                blockId = block.id,
                startMinute = block.startMinuteOfDay,
                duration = block.durationMinutes,
                flexibility = block.flexibility
            )
            when (result) {
                is PlannerOperationResult.Applied -> {
                    repository.saveTimeBlock(block)
                    result
                }
                else -> result
            }
        }
    }

    suspend fun moveBlock(blockId: String, targetStartMinute: Int): PlannerOperationResult {
        val target = getLatestBlock(blockId) ?: return PlannerOperationResult.Rejected("Block not found", blockId)
        if (target.isLocked || target.flexibility == BlockFlexibility.FIXED) {
            return PlannerOperationResult.Locked("Block is locked or fixed", blockId)
        }
        val snappedMinute = snapToGrid(targetStartMinute, 15)
        val validation = validatePlacement(
            date = target.date,
            blockId = target.id,
            startMinute = snappedMinute,
            duration = target.durationMinutes,
            flexibility = target.flexibility
        )
        if (validation !is PlannerOperationResult.Applied) return validation
        repository.saveTimeBlock(target.withUpdatedStart(snappedMinute))
        return PlannerOperationResult.Applied(
            "Block moved",
            target.id,
            snappedMinute,
            listOf(target.id)
        )
    }

    suspend fun previewMove(blockId: String, targetStartMinute: Int): PlannerOperationResult {
        val target = getLatestBlock(blockId) ?: return PlannerOperationResult.Rejected("Block not found", blockId)
        if (target.isLocked || target.flexibility == BlockFlexibility.FIXED) {
            return PlannerOperationResult.Locked("Block is locked or fixed", blockId)
        }
        val snappedMinute = snapToGrid(targetStartMinute, 15)
        val validation = validatePlacement(
            date = target.date,
            blockId = target.id,
            startMinute = snappedMinute,
            duration = target.durationMinutes,
            flexibility = target.flexibility
        )
        return when (validation) {
            is PlannerOperationResult.Applied -> PlannerOperationResult.Applied(
                message = "Placement preview valid",
                blockId = target.id,
                snappedToMinute = snappedMinute,
                affectedBlockIds = listOf(target.id)
            )
            else -> validation
        }
    }

    suspend fun resizeBlock(blockId: String, durationMinutes: Int): PlannerOperationResult {
        val target = getLatestBlock(blockId) ?: return PlannerOperationResult.Rejected("Block not found", blockId)
        return resizeBlockWindow(blockId, target.startMinuteOfDay, durationMinutes)
    }

    suspend fun resizeBlockWindow(blockId: String, startMinute: Int, durationMinutes: Int): PlannerOperationResult {
        val target = getLatestBlock(blockId) ?: return PlannerOperationResult.Rejected("Block not found", blockId)
        if (target.isLocked || target.flexibility == BlockFlexibility.FIXED) {
            return PlannerOperationResult.Locked("Block is locked or fixed", blockId)
        }
        if (target.flexibility == BlockFlexibility.MOVABLE) {
            return PlannerOperationResult.Rejected("Block is movable only", blockId)
        }
        val clamped = durationMinutes.coerceIn(1, 1440)
        val validation = validatePlacement(
            date = target.date,
            blockId = target.id,
            startMinute = startMinute,
            duration = clamped,
            flexibility = target.flexibility
        )
        if (validation !is PlannerOperationResult.Applied) return validation
        repository.saveTimeBlock(
            target.copy(
                startMinuteOfDay = normalizeMinute(startMinute),
                durationMinutes = clamped,
                updatedAt = Instant.now()
            )
        )
        return PlannerOperationResult.Applied(
            "Block resized",
            blockId,
            normalizeMinute(startMinute),
            listOf(blockId)
        )
    }

    suspend fun previewResize(blockId: String, durationMinutes: Int): PlannerOperationResult {
        val target = getLatestBlock(blockId) ?: return PlannerOperationResult.Rejected("Block not found", blockId)
        return previewResizeWindow(blockId, target.startMinuteOfDay, durationMinutes)
    }

    suspend fun previewResizeWindow(blockId: String, startMinute: Int, durationMinutes: Int): PlannerOperationResult {
        val target = getLatestBlock(blockId) ?: return PlannerOperationResult.Rejected("Block not found", blockId)
        if (target.isLocked || target.flexibility == BlockFlexibility.FIXED) {
            return PlannerOperationResult.Locked("Block is locked or fixed", blockId)
        }
        if (target.flexibility == BlockFlexibility.MOVABLE) {
            return PlannerOperationResult.Rejected("Block is movable only", blockId)
        }
        val clamped = durationMinutes.coerceIn(1, 1440)
        val validation = validatePlacement(
            date = target.date,
            blockId = target.id,
            startMinute = startMinute,
            duration = clamped,
            flexibility = target.flexibility
        )
        return when (validation) {
            is PlannerOperationResult.Applied -> PlannerOperationResult.Applied(
                message = "Resize preview valid",
                blockId = target.id,
                snappedToMinute = normalizeMinute(startMinute),
                affectedBlockIds = listOf(target.id)
            )
            else -> validation
        }
    }

    suspend fun deleteBlock(block: TimeBlock): PlannerOperationResult {
        repository.deleteTimeBlock(block)
        return PlannerOperationResult.Applied("Block deleted", block.id, null, emptyList())
    }

    suspend fun deleteBlockById(blockId: String): PlannerOperationResult {
        val block = getLatestBlock(blockId) ?: return PlannerOperationResult.Rejected("Block not found", blockId)
        return deleteBlock(block)
    }

    /**
     * Repacks flexible blocks to remove gaps.
     *
     * When [fromMinute] is supplied (the current minute-of-day for today), packing starts at that
     * floor instead of midnight: future blocks are compacted and overdue-but-unfinished blocks are
     * pulled forward into the remaining day, while blocks that are already underway stay anchored.
     * A block counts as underway when its execution has been logged ([TimeBlock.actualStartMinuteOfDay]
     * is set, i.e. completed or in progress) or the clock currently falls inside its window. This
     * turns "compact the whole day" into "reflow what's left from now", so a slipped afternoon is
     * recovered without repacking onto work that is already happening. Passing null (other days)
     * preserves the original full-day behaviour.
     */
    suspend fun rebalanceDay(
        date: java.time.LocalDate,
        fromMinute: Int? = null
    ): PlannerOperationResult {
        val floor = (fromMinute ?: 0).coerceIn(0, 1440)
        val allBlocks = getBlocksForDate(date)

        fun TimeBlock.isUnderway(): Boolean =
            actualStartMinuteOfDay != null ||
                (startMinuteOfDay < floor && floor < startMinuteOfDay + durationMinutes)

        val movable = allBlocks
            .filter { it.flexibility == BlockFlexibility.MOVABLE || it.flexibility == BlockFlexibility.RESIZABLE }
            .filterNot { it.isLocked || it.isUnderway() }
            .sortedBy { it.startMinuteOfDay }

        if (movable.isEmpty()) {
            return PlannerOperationResult.Rejected("No flexible blocks to rebalance", "")
        }

        // Everything not being moved anchors the layout: fixed/optional blocks, locked blocks, and
        // flexible blocks that have already started or elapsed.
        val movableIds = movable.mapTo(HashSet()) { it.id }
        val anchors: List<TimeBlock> = allBlocks.filterNot { it.id in movableIds }

        var cursor = floor
        val movedBlocks = mutableListOf<TimeBlock>()
        movable.forEach { block ->
            val maxStart = 1440 - block.durationMinutes
            val candidate = findNextAvailableStart(block, cursor, anchors, maxStart)
            if (candidate != null) {
                if (candidate != block.startMinuteOfDay) {
                    val moved = block.withUpdatedStart(candidate)
                    repository.saveTimeBlock(moved)
                    movedBlocks += moved
                }
                cursor = candidate + block.durationMinutes + 5
            }
        }
        if (movedBlocks.isEmpty()) {
            return PlannerOperationResult.Rejected("Everything already fits — nothing to rebalance", "")
        }
        return PlannerOperationResult.Applied(
            message = rebalanceSummaryMessage(movedBlocks),
            blockId = "",
            snappedToMinute = null,
            affectedBlockIds = movedBlocks.map { it.id }
        )
    }

    suspend fun logActualWindow(blockId: String, actualStartMinute: Int, actualEndMinute: Int): PlannerOperationResult {
        val block = getLatestBlock(blockId) ?: return PlannerOperationResult.Rejected("Block not found", blockId)
        val normalizedStart = normalizeMinute(actualStartMinute)
        val normalizedEnd = normalizeMinute(actualEndMinute)
        repository.saveTimeBlock(
            block.copy(
                actualStartMinuteOfDay = normalizedStart,
                actualEndMinuteOfDay = normalizedEnd,
                updatedAt = Instant.now()
            )
        )
        return PlannerOperationResult.Applied("Actual execution logged", blockId, normalizedStart, listOf(blockId))
    }

    suspend fun makeAiSuggestedBlock(
        date: java.time.LocalDate,
        title: String,
        startMinute: Int,
        durationMinutes: Int,
        category: String,
        timezone: String
    ): TimeBlock {
        return TimeBlock(
            id = UUID.randomUUID().toString(),
            date = date,
            title = title,
            category = category,
            startMinuteOfDay = normalizeMinute(startMinute),
            durationMinutes = durationMinutes.coerceIn(1, 1440),
            timezone = timezone,
            provenance = BlockProvenance.AI_SUGGESTED,
            flexibility = BlockFlexibility.OPTIONAL,
            energyLevel = EnergyIntensity.MODERATE,
            source = "AI",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private suspend fun validatePlacement(
        date: java.time.LocalDate,
        blockId: String,
        startMinute: Int,
        duration: Int,
        flexibility: BlockFlexibility
    ): PlannerOperationResult {
        if (flexibility == BlockFlexibility.FIXED) {
            return PlannerOperationResult.Rejected("Fixed blocks cannot be moved or resized", blockId)
        }

        val target = getLatestBlock(blockId)
        val previewTarget = target?.copy(
            startMinuteOfDay = normalizeMinute(startMinute),
            durationMinutes = duration.coerceIn(1, 1440)
        ) ?: TimeBlock(
            id = blockId,
            date = date,
            title = "Placement preview",
            category = "PREVIEW",
            startMinuteOfDay = normalizeMinute(startMinute),
            durationMinutes = duration.coerceIn(1, 1440),
            timezone = java.time.ZoneId.systemDefault().id,
            provenance = BlockProvenance.USER_CREATED,
            flexibility = flexibility,
            energyLevel = EnergyIntensity.MODERATE,
            source = "PLANNER",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val allBlocks = getBlocksForDate(date).filter { it.id != blockId } + previewTarget
        val conflicts = conflictDetectionEngine.detect(allBlocks)
            .filter { it.primaryBlockId == blockId || it.conflictingBlockId == blockId }
        if (conflicts.isNotEmpty()) {
            return if (conflicts.any { it.severity == ScheduleConflictSeverity.BLOCKING }) {
                PlannerOperationResult.Locked("Locked block collision detected", blockId)
            } else {
                PlannerOperationResult.Conflict(
                    "Conflicts detected",
                    blockId,
                    conflicts.map {
                        if (it.primaryBlockId == blockId) it.conflictingBlockId else it.primaryBlockId
                    }.distinct()
                )
            }
        }
        return PlannerOperationResult.Applied("Placement valid", blockId, startMinute, emptyList())
    }

    private suspend fun getLatestBlock(blockId: String): TimeBlock? {
        return repository.getTimeBlockById(blockId)
    }

    private fun findNextAvailableStart(
        block: TimeBlock,
        minMinute: Int,
        anchors: List<TimeBlock>,
        maxMinute: Int
    ): Int? {
        var candidate = minMinute
        while (candidate <= maxMinute) {
            if (anchors.none { it.overlaps(candidate, block.durationMinutes) }) {
                return candidate
            }
            candidate += 5
        }
        return null
    }

    private fun normalizeMinute(minute: Int): Int {
        return ((minute % 1440) + 1440) % 1440
    }

    private fun snapToGrid(minute: Int, increment: Int): Int {
        return ((minute + increment / 2) / increment) * increment % 1440
    }

    private fun TimeBlock.overlaps(startMinute: Int, duration: Int): Boolean {
        val startA = this.startMinuteOfDay
        val endA = this.startMinuteOfDay + this.durationMinutes
        val startB = startMinute
        val endB = startMinute + duration
        val max = 1440

        val segmentsA = if (endA > max) listOf(startA to max, 0 to endA - max) else listOf(startA to endA)
        val segmentsB = if (endB > max) listOf(startB to max, 0 to endB - max) else listOf(startB to endB)

        return segmentsA.any { (aStart, aEnd) ->
            segmentsB.any { (bStart, bEnd) ->
                aStart < bEnd && bStart < aEnd
            }
        }
    }
}

internal fun rebalanceSummaryMessage(movedBlocks: List<TimeBlock>): String {
    val moves = movedBlocks.take(3).joinToString(", ") { block ->
        "${block.title.ifBlank { "Untitled" }} → ${formatRebalanceMinute(block.startMinuteOfDay)}"
    }
    val extra = movedBlocks.size - 3
    return buildString {
        append("Moved ${movedBlocks.size} block${if (movedBlocks.size == 1) "" else "s"}: ")
        append(moves)
        if (extra > 0) append(" and $extra more")
    }
}

private fun formatRebalanceMinute(minute: Int): String {
    val normalized = ((minute % 1440) + 1440) % 1440
    return "%d:%02d".format(normalized / 60, normalized % 60)
}
