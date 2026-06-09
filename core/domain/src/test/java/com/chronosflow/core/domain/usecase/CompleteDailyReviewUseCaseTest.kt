package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.repository.ReviewRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CompleteDailyReviewUseCaseTest {
    private val reviewRepository: ReviewRepository = mockk()
    private val useCase = CompleteDailyReviewUseCase(reviewRepository)

    @Test
    fun `saves completed daily review summary`() = runTest {
        val summary = UseCaseTestFixtures.dailyReviewSummary()
        coEvery { reviewRepository.saveDailyReview(summary) } returns Unit

        useCase(summary)

        coVerify(exactly = 1) { reviewRepository.saveDailyReview(summary) }
    }
}
