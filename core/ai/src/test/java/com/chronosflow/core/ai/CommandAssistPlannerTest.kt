package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistTextGeneration
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.data.privacy.AssistantPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandAssistPlannerTest {
    @Test
    fun `localRankCommandIds matches title keywords`() {
        val planner = CommandAssistPlanner(mockk(relaxed = true), mockk(relaxed = true))
        val candidates = listOf(
            CommandAssistCandidate("daydial.plan", "Open plan", setOf("plan", "schedule")),
            CommandAssistCandidate("review.open", "Open review", setOf("review"))
        )

        val ids = planner.localRankCommandIds("open plan", candidates)

        assertEquals("daydial.plan", ids.first())
    }

    @Test
    fun `rankCommandIdsWithAssist prefers model-selected ids`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "review.open",
            source = com.chronosflow.core.ai.genai.AssistGenAiSource.GEMINI_NANO
        )
        val preferences = mockk<AssistantPreferences>()
        every { preferences.assistantPrivacyModeValue() } returns PrivacyMode.ON_DEVICE_ONLY.name
        val planner = CommandAssistPlanner(coordinator, preferences)
        val candidates = listOf(
            CommandAssistCandidate("daydial.plan", "Open plan", setOf("plan")),
            CommandAssistCandidate("review.open", "Open review", setOf("review"))
        )

        val ids = planner.rankCommandIdsWithAssist("daily review summary", candidates)

        assertTrue(ids.contains("review.open"))
    }
}
