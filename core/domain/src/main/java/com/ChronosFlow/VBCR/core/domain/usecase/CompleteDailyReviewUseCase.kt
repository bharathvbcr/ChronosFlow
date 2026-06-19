package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import javax.inject.Inject

class CompleteDailyReviewUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    suspend operator fun invoke(summary: DailyReviewSummary) {
        reviewRepository.saveDailyReview(summary)
    }
}
