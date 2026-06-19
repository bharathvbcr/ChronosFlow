package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import javax.inject.Inject

class CreateBlockUseCase @Inject constructor(
    private val plannerService: PlannerService
) {
    suspend operator fun invoke(block: TimeBlock): PlannerOperationResult = plannerService.createBlock(block)
}
