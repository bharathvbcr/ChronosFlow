package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import javax.inject.Inject

class MoveBlockUseCase @Inject constructor(
    private val plannerService: PlannerService
) {
    suspend operator fun invoke(blockId: String, targetStartMinute: Int): PlannerOperationResult {
        return plannerService.moveBlock(blockId, targetStartMinute)
    }
}
