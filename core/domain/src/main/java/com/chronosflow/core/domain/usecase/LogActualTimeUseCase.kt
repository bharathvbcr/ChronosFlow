package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.ActualTimeSegment
import com.chronosflow.core.domain.repository.ReviewRepository
import javax.inject.Inject

class LogActualTimeUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    suspend operator fun invoke(segment: ActualTimeSegment) {
        reviewRepository.saveActualTimeSegment(segment)
    }
}
