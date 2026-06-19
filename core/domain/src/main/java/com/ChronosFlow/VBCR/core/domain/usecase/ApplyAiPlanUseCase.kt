package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import javax.inject.Inject

class ApplyAiPlanUseCase @Inject constructor(
    private val createBlockUseCase: CreateBlockUseCase
) {
    suspend operator fun invoke(suggestions: List<TimeBlock>): Int {
        var accepted = 0
        suggestions.forEach { block ->
            if (createBlockUseCase(block).canUndo) accepted++
        }
        return accepted
    }
}
