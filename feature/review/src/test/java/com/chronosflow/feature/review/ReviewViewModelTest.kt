package com.chronosflow.feature.review

import app.cash.turbine.test
import com.chronosflow.core.ai.AssistNarrative
import com.chronosflow.core.ai.ReviewAssistPlanner
import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.data.assist.ProactiveAssistCache
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.repository.ReviewRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    private val reviewRepository: ReviewRepository = mockk()
    private val reviewAssistPlanner: ReviewAssistPlanner = mockk()
    private val proactiveAssistCache: ProactiveAssistCache = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `coachNarrative emits planner result and caches today's line`() = runTest {
        val narrative = AssistNarrative(
            headline = "Today stayed close to plan.",
            nextStep = "Keep the current rhythm.",
            source = AssistGenAiSource.GEMINI_NANO
        )
        every { reviewRepository.observeDailyReview(any()) } returns flowOf(summary(LocalDate.now()))
        coEvery { reviewAssistPlanner.suggestSummary(any(), any()) } returns narrative
        val viewModel = ReviewViewModel(reviewRepository, reviewAssistPlanner, proactiveAssistCache)

        viewModel.coachNarrative.test {
            var item = awaitItem()
            if (item == null) {
                item = awaitItem()
            }
            assertEquals(narrative, item)
            cancelAndIgnoreRemainingEvents()
        }
        verify {
            proactiveAssistCache.putDailyCoachLine(
                date = LocalDate.now(),
                headline = "Today stayed close to plan.",
                nextStep = "Keep the current rhythm.",
                source = "GEMINI_NANO"
            )
        }
    }

    @Test
    fun `coachNarrative stays null and skips cache when nothing planned`() = runTest {
        every { reviewRepository.observeDailyReview(any()) } returns flowOf(null)
        val viewModel = ReviewViewModel(reviewRepository, reviewAssistPlanner, proactiveAssistCache)

        viewModel.coachNarrative.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 0) {
            proactiveAssistCache.putDailyCoachLine(any(), any(), any(), any())
        }
    }

    private fun summary(date: LocalDate): DailyReviewSummary = DailyReviewSummary(
        date = date,
        plannedMinutes = 240,
        actualMinutes = 200,
        missedMinutes = 40,
        driftMinutes = 20,
        completedBlockCount = 4,
        missedBlockCount = 1,
        insights = emptyList()
    )
}
