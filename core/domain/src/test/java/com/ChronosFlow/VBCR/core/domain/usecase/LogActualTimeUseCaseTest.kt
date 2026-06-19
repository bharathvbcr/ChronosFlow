package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LogActualTimeUseCaseTest {
    private val reviewRepository: ReviewRepository = mockk()
    private val useCase = LogActualTimeUseCase(reviewRepository)

    @Test
    fun `saves actual time segment`() = runTest {
        val segment = UseCaseTestFixtures.actualTimeSegment()
        coEvery { reviewRepository.saveActualTimeSegment(segment) } returns Unit

        useCase(segment)

        coVerify(exactly = 1) { reviewRepository.saveActualTimeSegment(segment) }
    }
}
