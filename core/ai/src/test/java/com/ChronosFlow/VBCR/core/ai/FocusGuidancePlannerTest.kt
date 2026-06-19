package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusGuidancePlannerTest {
    @Test
    fun `suggestGuidance uses AI narrative when available`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "Stay with deep work|Mute notifications for the next 20 minutes.",
            source = AssistGenAiSource.CLOUD_GEMINI
        )
        val planner = FocusGuidancePlanner(coordinator)

        val narrative = planner.suggestGuidance(
            focusTitle = "Deep work",
            linkedBlockId = "block-1",
            isRunning = true,
            isPaused = false,
            timeLeft = 1200,
            totalSeconds = 1500
        )

        assertEquals("Stay with deep work", narrative.headline)
        assertEquals(AssistGenAiSource.CLOUD_GEMINI, narrative.source)
    }

    @Test
    fun `suggestGuidance falls back to local guidance when AI unavailable`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = FocusGuidancePlanner(coordinator)

        val narrative = planner.suggestGuidance(
            focusTitle = "Focus Session",
            linkedBlockId = null,
            isRunning = false,
            isPaused = false,
            timeLeft = 1500,
            totalSeconds = 1500
        )

        assertEquals(AssistGenAiSource.LOCAL, narrative.source)
        assertEquals("Focus mode is ready for the next protected work block.", narrative.headline)
    }
}
