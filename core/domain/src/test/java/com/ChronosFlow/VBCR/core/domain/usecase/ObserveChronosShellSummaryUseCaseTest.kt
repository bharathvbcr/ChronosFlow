package com.ChronosFlow.VBCR.core.domain.usecase

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightSeverity
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveChronosShellSummaryUseCaseTest {
    @Test
    fun `summarizes missed blocks active focus and unread insights`() = runTest {
        val date = LocalDate.of(2026, 5, 27)
        val currentMinute = MutableStateFlow(10 * 60)
        val timeBlockRepository = mockk<TimeBlockRepository>()
        val reviewRepository = mockk<ReviewRepository>()
        val focusSessionRepository = mockk<FocusSessionRepository>()
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(
            listOf(
                timeBlock("missed", date, startMinute = 8 * 60, durationMinutes = 30),
                timeBlock("future", date, startMinute = 11 * 60, durationMinutes = 30),
                timeBlock(
                    id = "actual",
                    date = date,
                    startMinute = 7 * 60,
                    durationMinutes = 30,
                    actualStartMinute = 7 * 60,
                    actualEndMinute = 7 * 60 + 30
                )
            )
        )
        every { reviewRepository.observeDailyReview(date) } returns flowOf(
            dailyReview(
                date = date,
                severities = listOf(
                    ReviewInsightSeverity.INFO,
                    ReviewInsightSeverity.WARNING,
                    ReviewInsightSeverity.CRITICAL
                )
            )
        )
        every { focusSessionRepository.observeRecoverableSession() } returns flowOf(
            FocusSessionState.Running(
                sessionId = "session-1",
                blockId = "future",
                startedAt = Instant.parse("2026-05-27T14:00:00Z"),
                plannedEndAt = Instant.parse("2026-05-27T15:00:00Z")
            )
        )
        val useCase = ObserveChronosShellSummaryUseCase(
            timeBlockRepository = timeBlockRepository,
            reviewRepository = reviewRepository,
            focusSessionRepository = focusSessionRepository
        )

        useCase(date, currentMinute).test {
            val summary = awaitItem()
            assertEquals(1, summary.missedBlocksCount)
            assertEquals(true, summary.focusActive)
            assertEquals(2, summary.unreadInsightsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun timeBlock(
        id: String,
        date: LocalDate,
        startMinute: Int,
        durationMinutes: Int,
        actualStartMinute: Int? = null,
        actualEndMinute: Int? = null
    ): TimeBlock {
        val now = Instant.parse("2026-05-27T12:00:00Z")
        return TimeBlock(
            id = id,
            date = date,
            title = id,
            category = "FOCUS",
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            timezone = "UTC",
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "TEST",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = actualStartMinute,
            actualEndMinuteOfDay = actualEndMinute,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun dailyReview(
        date: LocalDate,
        severities: List<ReviewInsightSeverity>
    ): DailyReviewSummary = DailyReviewSummary(
        date = date,
        plannedMinutes = 90,
        actualMinutes = 30,
        missedMinutes = 60,
        driftMinutes = 15,
        completedBlockCount = 1,
        missedBlockCount = 1,
        insights = severities.mapIndexed { index, severity ->
            ReviewInsight(
                id = "insight-$index",
                type = ReviewInsightType.MISSED_BLOCK,
                title = "Insight $index",
                detail = "Detail $index",
                severity = severity
            )
        }
    )
}
