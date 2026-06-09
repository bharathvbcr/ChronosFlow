package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import javax.inject.Inject

class ResizeBlockUseCase @Inject constructor(
    private val plannerService: PlannerService
) {
    suspend operator fun invoke(blockId: String, durationMinutes: Int): PlannerOperationResult {
        return plannerService.resizeBlock(blockId, durationMinutes)
    }

    suspend operator fun invoke(blockId: String, startMinute: Int, durationMinutes: Int): PlannerOperationResult {
        return plannerService.resizeBlockWindow(blockId, startMinute, durationMinutes)
    }
}
