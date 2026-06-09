package com.chronosflow.core.domain.planner

import com.chronosflow.core.domain.model.TimeBlock

sealed interface PlannerCommand {
    val id: String
    val label: String

    suspend fun execute(service: PlannerService): PlannerOperationResult
    suspend fun undo(service: PlannerService): PlannerOperationResult
}

data class CreateTimeBlockCommand(
    override val id: String,
    private val block: TimeBlock
) : PlannerCommand {
    override val label: String = "Create block"

    private var created = false

    override suspend fun execute(service: PlannerService): PlannerOperationResult {
        val result = service.createBlock(block)
        created = result is PlannerOperationResult.Applied
        return result
    }

    override suspend fun undo(service: PlannerService): PlannerOperationResult {
        return if (created) {
            created = false
            service.deleteBlockById(block.id)
        } else {
            PlannerOperationResult.Rejected("Nothing to undo for create", block.id)
        }
    }
}

data class MoveTimeBlockCommand(
    override val id: String,
    private val blockId: String,
    private val targetStartMinute: Int,
    private val originalStartMinute: Int
) : PlannerCommand {
    override val label: String = "Move block"

    override suspend fun execute(service: PlannerService): PlannerOperationResult {
        return service.moveBlock(blockId, targetStartMinute)
    }

    override suspend fun undo(service: PlannerService): PlannerOperationResult {
        return service.moveBlock(blockId, originalStartMinute)
    }
}

data class ResizeTimeBlockCommand(
    override val id: String,
    private val blockId: String,
    private val targetDurationMinutes: Int,
    private val originalDurationMinutes: Int
) : PlannerCommand {
    override val label: String = "Resize block"

    override suspend fun execute(service: PlannerService): PlannerOperationResult {
        return service.resizeBlock(blockId, targetDurationMinutes)
    }

    override suspend fun undo(service: PlannerService): PlannerOperationResult {
        return service.resizeBlock(blockId, originalDurationMinutes)
    }
}

data class DeleteTimeBlockCommand(
    override val id: String,
    private val blockId: String,
    private val blockSnapshot: TimeBlock
) : PlannerCommand {
    override val label: String = "Delete block"
    private var snapshotRemoved = false

    override suspend fun execute(service: PlannerService): PlannerOperationResult {
        val result = service.deleteBlock(blockSnapshot)
        snapshotRemoved = result is PlannerOperationResult.Applied
        return result
    }

    override suspend fun undo(service: PlannerService): PlannerOperationResult {
        return if (snapshotRemoved) {
            snapshotRemoved = false
            service.createBlock(blockSnapshot)
        } else {
            PlannerOperationResult.Rejected("Nothing to undo for delete", blockId)
        }
    }
}

