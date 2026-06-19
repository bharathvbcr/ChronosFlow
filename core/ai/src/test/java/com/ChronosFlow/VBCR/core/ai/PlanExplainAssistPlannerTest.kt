package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightSeverity
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanExplainAssistPlannerTest {
    @Test
    fun `explainPlan uses AI text when available`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any(), any()) } returns AssistTextGeneration(
            text = "Today is front-loaded with meetings; protect a recovery block after lunch.",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = PlanExplainAssistPlanner(coordinator)

        val explanation = planner.explainPlan(
            blocks = emptyList(),
            review = sampleReview(),
            timezone = "America/New_York",
            privacyMode = PrivacyMode.ON_DEVICE_ONLY
        )

        assertTrue(explanation.text.contains("front-loaded", ignoreCase = true))
        assertEquals(AssistGenAiSource.GEMINI_NANO, explanation.source)
    }

    @Test
    fun `explainPlan falls back locally when AI unavailable`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any(), any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = PlanExplainAssistPlanner(coordinator)

        val explanation = planner.explainPlan(
            blocks = emptyList(),
            review = sampleReview(),
            timezone = "America/New_York",
            privacyMode = PrivacyMode.CLOUD_ALLOWED
        )

        assertEquals(AssistGenAiSource.LOCAL, explanation.source)
        assertTrue(explanation.text.contains("Planned", ignoreCase = true))
    }

    private fun sampleReview() = DailyReviewSummary(
        date = LocalDate.of(2026, 5, 25),
        plannedMinutes = 480,
        actualMinutes = 360,
        missedMinutes = 30,
        driftMinutes = 45,
        completedBlockCount = 4,
        missedBlockCount = 1,
        insights = listOf(
            ReviewInsight(
                id = "insight-1",
                type = ReviewInsightType.DRIFT,
                title = "Schedule drift",
                detail = "Actual trailed plan in the afternoon.",
                severity = ReviewInsightSeverity.WARNING
            )
        )
    )
}
