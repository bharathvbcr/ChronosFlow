package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import javax.inject.Inject

class MoveBlockUseCase @Inject constructor(
    private val plannerService: PlannerService
) {
    suspend operator fun invoke(blockId: String, targetStartMinute: Int): PlannerOperationResult {
        return plannerService.moveBlock(blockId, targetStartMinute)
    }
}
