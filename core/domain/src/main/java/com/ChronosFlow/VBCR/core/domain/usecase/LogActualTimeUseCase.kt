package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import javax.inject.Inject

class LogActualTimeUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    suspend operator fun invoke(segment: ActualTimeSegment) {
        reviewRepository.saveActualTimeSegment(segment)
    }
}
