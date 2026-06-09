package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.repository.ReviewRepository
import javax.inject.Inject

class CompleteDailyReviewUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    suspend operator fun invoke(summary: DailyReviewSummary) {
        reviewRepository.saveDailyReview(summary)
    }
}
