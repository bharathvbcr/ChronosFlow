package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodEnergyCheckInAssistPlannerTest {
    @Test
    fun `suggestAfterCheckIn uses AI narrative when available`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "Energy is climbing|Use the next 25 minutes for one protected task.",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = MoodEnergyCheckInAssistPlanner(coordinator)

        val narrative = planner.suggestAfterCheckIn(
            moodScore = 4,
            stressScore = 2,
            energyScore = 4,
            focusScore = 4,
            linkedBlockTitle = "Deep work"
        )

        assertEquals("Energy is climbing", narrative.headline)
        assertEquals(AssistGenAiSource.GEMINI_NANO, narrative.source)
    }

    @Test
    fun `suggestAfterCheckIn falls back to local coaching when AI unavailable`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = MoodEnergyCheckInAssistPlanner(coordinator)

        val narrative = planner.suggestAfterCheckIn(
            moodScore = 2,
            stressScore = 4,
            energyScore = 2,
            focusScore = 2
        )

        assertEquals(AssistGenAiSource.LOCAL, narrative.source)
        assertEquals(
            "Stress is high and energy is low — protect recovery before pushing harder.",
            narrative.headline
        )
    }
}
