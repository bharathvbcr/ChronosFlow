package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.AssistTextGeneration
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
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

class InsightsRecommendationsPlannerTest {
    @Test
    fun `suggest uses AI recommendations when available`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "Move admin after lunch|Reduces drift during low-energy hours",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = InsightsRecommendationsPlanner(coordinator)

        val recommendations = planner.suggest(sampleSummary(), emptyList())

        assertEquals("Move admin after lunch", recommendations.first().text)
        assertEquals(AssistGenAiSource.GEMINI_NANO, recommendations.first().source)
    }

    @Test
    fun `localRecommendations prefers drift guidance when summary shows high drift`() {
        val planner = InsightsRecommendationsPlanner(mockk(relaxed = true))

        val recommendations = planner.localRecommendations(
            summary = sampleSummary(driftMinutes = 90),
            insights = emptyList()
        )

        assertTrue(recommendations.first().text.contains("drift", ignoreCase = true))
        assertEquals(AssistGenAiSource.LOCAL, recommendations.first().source)
    }

    private fun sampleSummary(driftMinutes: Int = 20) = DailyReviewSummary(
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
                severity = ReviewInsightSeverity.INFO
            )
        )
    )
}
