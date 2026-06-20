package com.ChronosFlow.VBCR.core.data.repository

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.data.dao.ReviewDao
import com.ChronosFlow.VBCR.core.data.model.DailyReviewEntity
import com.ChronosFlow.VBCR.core.data.model.ReviewInsightEntity
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightSeverity
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ReviewRepositoryImplTest {

    private val reviewDao: ReviewDao = mockk()
    private lateinit var repository: ReviewRepositoryImpl

    @Before
    fun setup() {
        repository = ReviewRepositoryImpl(reviewDao)
    }

    @Test
    fun `observeDailyReview prefers normalized insight rows over legacy json`() = runTest {
        val date = LocalDate.of(2026, 5, 8)
        every { reviewDao.observeDailyReview(date) } returns flowOf(
            DailyReviewEntity(
                date = date,
                plannedMinutes = 120,
                actualMinutes = 90,
                missedMinutes = 30,
                driftMinutes = -30,
                completedBlockCount = 1,
                missedBlockCount = 1,
                insightsJson = """[{"id":"legacy","type":"DRIFT","title":"Legacy","detail":"Legacy JSON","relatedBlockId":null,"severity":"INFO"}]"""
            )
        )
        every { reviewDao.observeReviewInsights(date) } returns flowOf(
            listOf(
                ReviewInsightEntity(
                    id = "row-1",
                    date = date,
                    type = ReviewInsightType.MISSED_BLOCK.name,
                    title = "Missed deep work",
                    detail = "No matching actual segment",
                    relatedBlockId = "block-1",
                    severity = ReviewInsightSeverity.WARNING.name
                )
            )
        )

        repository.observeDailyReview(date).test {
            val result = awaitItem()
            assertEquals(120, result?.plannedMinutes)
            assertEquals(1, result?.insights?.size)
            assertEquals("row-1", result?.insights?.first()?.id)
            assertEquals(ReviewInsightType.MISSED_BLOCK, result?.insights?.first()?.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveDailyReview replaces normalized insight rows after saving summary`() = runTest {
        val date = LocalDate.of(2026, 5, 8)
        val summary = DailyReviewSummary(
            date = date,
            plannedMinutes = 180,
            actualMinutes = 120,
            missedMinutes = 60,
            driftMinutes = -60,
            completedBlockCount = 2,
            missedBlockCount = 1,
            insights = listOf(
                ReviewInsight(
                    id = "insight-1",
                    type = ReviewInsightType.DRIFT,
                    title = "Schedule drift",
                    detail = "Actual time was 60m under plan",
                    severity = ReviewInsightSeverity.WARNING
                )
            )
        )
        coEvery { reviewDao.saveDailyReviewTransactional(any(), any(), any()) } returns Unit

        repository.saveDailyReview(summary)

        coVerify {
            reviewDao.saveDailyReviewTransactional(
                review = match { it.date == date && it.plannedMinutes == 180 },
                date = date,
                insights = match { rows ->
                    rows.size == 1 &&
                        rows.first().id == "insight-1" &&
                        rows.first().type == ReviewInsightType.DRIFT.name &&
                        rows.first().severity == ReviewInsightSeverity.WARNING.name
                }
            )
        }
    }
}
