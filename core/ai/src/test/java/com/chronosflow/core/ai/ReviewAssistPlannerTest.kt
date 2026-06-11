package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.AssistTextGeneration
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.SummaryStyle
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.ReviewInsight
import com.chronosflow.core.domain.model.ReviewInsightSeverity
import com.chronosflow.core.domain.model.ReviewInsightType
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewAssistPlannerTest {
    @Test
    fun `suggestSummary uses AI narrative when available`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "Strong finish|Carry the best block forward as tomorrow's anchor.",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = ReviewAssistPlanner(coordinator)

        val narrative = planner.suggestSummary(sampleSummary(driftMinutes = 10), weeklyContext())

        assertEquals("Strong finish", narrative.headline)
        assertEquals(AssistGenAiSource.GEMINI_NANO, narrative.source)
    }

    @Test
    fun `suggestSummary uses weekly recovery step when completion is low`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = ReviewAssistPlanner(coordinator)

        val narrative = planner.suggestSummary(
            summary = sampleSummary(driftMinutes = 10),
            weekly = weeklyContext().copy(
                plannedMinutes = 2_400,
                actualMinutes = 1_200,
                daysReviewed = 5
            )
        )

        assertTrue(narrative.nextStep.contains("earlier in the day", ignoreCase = true))
    }

    @Test
    fun `suggestSummary uses local drift headline when AI unavailable`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = ReviewAssistPlanner(coordinator)

        val narrative = planner.suggestSummary(
            summary = sampleSummary(driftMinutes = 120),
            weekly = weeklyContext()
        )

        assertEquals(AssistGenAiSource.LOCAL, narrative.source)
        assertEquals("High drift today: 120 minutes moved off-plan.", narrative.headline)
    }

    @Test
    fun `suggestDigest summarizes insights with on-device summarizer`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.summarize(any(), SummaryStyle.THREE_BULLETS) } returns AssistTextGeneration(
            text = "• Energy held in the morning\n• Afternoon drifted",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = ReviewAssistPlanner(coordinator)

        val digest = planner.suggestDigest(twoInsights())

        assertEquals(AssistGenAiSource.GEMINI_NANO, digest.source)
        assertTrue(digest.text.contains("morning", ignoreCase = true))
    }

    @Test
    fun `suggestDigest falls back to local digest when summarizer empty`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.summarize(any(), SummaryStyle.THREE_BULLETS) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = ReviewAssistPlanner(coordinator)

        val digest = planner.suggestDigest(twoInsights())

        assertEquals(AssistGenAiSource.LOCAL, digest.source)
        assertTrue(digest.text.contains("Energy peak"))
    }

    private fun twoInsights() = listOf(
        ReviewInsight(
            id = "insight-1",
            type = ReviewInsightType.ENERGY_PEAK,
            title = "Energy peak",
            detail = "Morning energy held steady.",
            relatedBlockId = null,
            severity = ReviewInsightSeverity.INFO
        ),
        ReviewInsight(
            id = "insight-2",
            type = ReviewInsightType.HABIT_WINDOW,
            title = "Habit window missed",
            detail = "Evening habit slipped.",
            relatedBlockId = null,
            severity = ReviewInsightSeverity.WARNING
        )
    )

    private fun sampleSummary(driftMinutes: Int) = DailyReviewSummary(
        date = LocalDate.of(2026, 5, 25),
        plannedMinutes = 480,
        actualMinutes = 360,
        missedMinutes = 30,
        driftMinutes = driftMinutes,
        completedBlockCount = 4,
        missedBlockCount = 1,
        insights = listOf(
            ReviewInsight(
                id = "insight-1",
                type = ReviewInsightType.ENERGY_PEAK,
                title = "Energy peak",
                detail = "Morning energy held steady.",
                relatedBlockId = null,
                severity = ReviewInsightSeverity.INFO
            )
        )
    )

    private fun weeklyContext() = WeeklyReviewContext(
        daysReviewed = 5,
        plannedMinutes = 2400,
        actualMinutes = 1800,
        missedMinutes = 120,
        driftMinutes = 200,
        completedBlocks = 20,
        missedBlocks = 3,
        insightCount = 4
    )
}
