package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import javax.inject.Inject

class CreateBlockUseCase @Inject constructor(
    private val plannerService: PlannerService
) {
    suspend operator fun invoke(block: TimeBlock): PlannerOperationResult = plannerService.createBlock(block)
}
