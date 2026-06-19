package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationPlanInterpreterTest {
    @Test
    fun `interpret maps buffer recommendation to buffer goal locally`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = RecommendationPlanInterpreter(coordinator)

        val intent = planner.interpret(
            recommendation = "Add 15m buffers around meetings",
            review = null,
            blocks = emptyList()
        )

        assertTrue(intent.planGoals.any { it.contains("buffer", ignoreCase = true) })
        assertEquals(AssistGenAiSource.LOCAL, intent.source)
        assertEquals(RecommendationQuickAction.ADD_BREAK, intent.quickAction)
    }

    @Test
    fun `inferQuickAction maps fill gaps recommendations`() {
        val coordinator = mockk<GenAiAssistCoordinator>()
        val planner = RecommendationPlanInterpreter(coordinator)

        assertEquals(
            RecommendationQuickAction.FILL_GAPS,
            planner.inferQuickAction("Fill empty gaps before your next meeting")
        )
    }

    @Test
    fun `interpret uses AI goals when available`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "Move admin after lunch\nProtect 9:30 focus window",
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = RecommendationPlanInterpreter(coordinator)

        val intent = planner.interpret(
            recommendation = "Move admin blocks after lunch",
            review = sampleReview(),
            blocks = emptyList()
        )

        assertEquals(AssistGenAiSource.CLOUD_GEMINI, intent.source)
        assertEquals(2, intent.planGoals.size)
        assertEquals("Move admin after lunch", intent.planGoals.first())
    }

    private fun sampleReview() = DailyReviewSummary(
        date = LocalDate.of(2026, 5, 25),
        plannedMinutes = 480,
        actualMinutes = 300,
        missedMinutes = 40,
        driftMinutes = 90,
        completedBlockCount = 3,
        missedBlockCount = 2,
        insights = emptyList()
    )
}
