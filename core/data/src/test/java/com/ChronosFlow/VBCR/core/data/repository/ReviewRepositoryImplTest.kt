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

    // -------------------------------------------------------------------------
    // Transaction atomicity — saveDailyReview atomic insert/delete
    // -------------------------------------------------------------------------

    @Test
    fun `saveDailyReview when transactional DAO throws propagates exception`() = runTest {
        val date = LocalDate.of(2026, 6, 20)
        val summary = DailyReviewSummary(
            date = date,
            plannedMinutes = 60,
            actualMinutes = 50,
            missedMinutes = 10,
            driftMinutes = -10,
            completedBlockCount = 1,
            missedBlockCount = 0,
            insights = listOf(
                ReviewInsight(
                    id = "insight-fail",
                    type = ReviewInsightType.MISSED_BLOCK,
                    title = "Missed morning block",
                    detail = "No actual segment found",
                    severity = ReviewInsightSeverity.WARNING
                )
            )
        )
        // Simulate a failure inside the Room @Transaction (e.g. insert violates a constraint).
        coEvery {
            reviewDao.saveDailyReviewTransactional(any(), any(), any())
        } throws RuntimeException("Simulated DB failure mid-transaction")

        var caughtException: Exception? = null
        try {
            repository.saveDailyReview(summary)
        } catch (e: RuntimeException) {
            caughtException = e
        }

        // The exception must propagate: the repository must NOT swallow transaction failures.
        assert(caughtException != null) {
            "saveDailyReview must propagate exceptions from the transactional DAO call"
        }
    }

    @Test
    fun `saveDailyReview passes review entity and all insight rows to the transactional DAO`() =
        runTest {
            val date = LocalDate.of(2026, 6, 20)
            val summary = DailyReviewSummary(
                date = date,
                plannedMinutes = 240,
                actualMinutes = 200,
                missedMinutes = 40,
                driftMinutes = -40,
                completedBlockCount = 3,
                missedBlockCount = 1,
                insights = listOf(
                    ReviewInsight(
                        id = "i-1",
                        type = ReviewInsightType.DRIFT,
                        title = "Drift detected",
                        detail = "40 minutes behind",
                        severity = ReviewInsightSeverity.INFO
                    ),
                    ReviewInsight(
                        id = "i-2",
                        type = ReviewInsightType.MISSED_BLOCK,
                        title = "Missed afternoon focus",
                        detail = "No matching segment",
                        severity = ReviewInsightSeverity.WARNING
                    )
                )
            )
            coEvery { reviewDao.saveDailyReviewTransactional(any(), any(), any()) } returns Unit

            repository.saveDailyReview(summary)

            // Verify the DAO received both insights in the same call — atomicity is enforced by
            // the single saveDailyReviewTransactional invocation (delete-then-insert is inside it).
            coVerify(exactly = 1) {
                reviewDao.saveDailyReviewTransactional(
                    review = match {
                        it.date == date &&
                            it.plannedMinutes == 240 &&
                            it.completedBlockCount == 3 &&
                            it.missedBlockCount == 1
                    },
                    date = date,
                    insights = match { rows ->
                        rows.size == 2 &&
                            rows.any { it.id == "i-1" && it.type == ReviewInsightType.DRIFT.name } &&
                            rows.any { it.id == "i-2" && it.severity == ReviewInsightSeverity.WARNING.name }
                    }
                )
            }
        }

    @Test
    fun `saveDailyReview with empty insights list passes empty list to transactional DAO`() =
        runTest {
            val date = LocalDate.of(2026, 6, 20)
            val summary = DailyReviewSummary(
                date = date,
                plannedMinutes = 120,
                actualMinutes = 120,
                missedMinutes = 0,
                driftMinutes = 0,
                completedBlockCount = 2,
                missedBlockCount = 0,
                insights = emptyList()
            )
            coEvery { reviewDao.saveDailyReviewTransactional(any(), any(), any()) } returns Unit

            repository.saveDailyReview(summary)

            // An empty insights list must still trigger the transactional call so that any
            // stale rows from a previous save are deleted (atomically).
            coVerify(exactly = 1) {
                reviewDao.saveDailyReviewTransactional(
                    review = any(),
                    date = date,
                    insights = match { it.isEmpty() }
                )
            }
        }
}
