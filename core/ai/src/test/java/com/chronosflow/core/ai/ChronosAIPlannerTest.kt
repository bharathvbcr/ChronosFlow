package com.chronosflow.core.ai

import android.content.Context
import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.AssistTextGeneration
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.domain.model.DailyReviewSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChronosAIPlannerTest {

    @Test
    fun generateIdealDayPlan_fallsBackToLocalHeuristicsWhenNanoFailsInOnDeviceMode() = runTest {
        val context = mockk<Context>()
        every { context.packageName } returns "com.chronosflow.test"
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = ChronosAIPlanner(context = context, genAiAssistCoordinator = coordinator)

        val result = planner.generateIdealDayPlan(
            userPreferences = "protect focus and recovery",
            date = LocalDate.of(2026, 5, 24),
            currentTimeZone = "America/Chicago",
            privacyMode = PrivacyMode.ON_DEVICE_ONLY
        )

        assertTrue(result.proposedBlocks.isNotEmpty())
        assertTrue(result.explanation.contains("local planning heuristic") || result.explanation.contains("local heuristics"))
    }

    @Test
    fun generateIdealDayPlan_rescuesMalformedJsonWithOneCorrectiveRetry() = runTest {
        val context = mockk<Context>()
        every { context.packageName } returns "com.chronosflow.test"
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        val validJson = """
            {"blocks":[{"title":"Deep work","category":"WORK","startMinuteOfDay":540,"durationMinutes":90,"flexibility":"RESIZABLE","isProtected":false}],"reason":"r","conflictsResolved":[],"explanation":"e"}
        """.trimIndent()
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returnsMany listOf(
            AssistTextGeneration(text = "sorry, here is your plan (no JSON)", source = AssistGenAiSource.GEMINI_NANO),
            AssistTextGeneration(text = validJson, source = AssistGenAiSource.GEMINI_NANO)
        )
        val planner = ChronosAIPlanner(context = context, genAiAssistCoordinator = coordinator)

        val result = planner.generateIdealDayPlan(
            userPreferences = "deep work",
            date = LocalDate.of(2026, 5, 24),
            currentTimeZone = "America/Chicago",
            privacyMode = PrivacyMode.ON_DEVICE_ONLY
        )

        // The plan came from the retried JSON, not the local heuristic fallback.
        assertEquals(1, result.proposedBlocks.size)
        assertEquals("Deep work", result.proposedBlocks.first().title)
        assertEquals(AssistGenAiSource.GEMINI_NANO, result.explanationSource)
        coVerify(exactly = 2) { coordinator.generateAssistText(any(), any(), any()) }
    }

    @Test
    fun generateReviewBackedDayPlan_fallsBackToHeuristicsWhenCloudAndNanoFail() = runTest {
        val context = mockk<Context>()
        every { context.packageName } returns "com.chronosflow.test"
        val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
        coEvery { coordinator.generateAssistText(any(), any(), any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = ChronosAIPlanner(context = context, genAiAssistCoordinator = coordinator)

        val result = planner.generateReviewBackedDayPlan(
            userPreferences = "deep work",
            review = DailyReviewSummary(
                date = LocalDate.of(2026, 5, 24),
                plannedMinutes = 180,
                actualMinutes = 90,
                missedMinutes = 45,
                driftMinutes = 30,
                completedBlockCount = 2,
                missedBlockCount = 1,
                insights = emptyList()
            ),
            existingBlocks = emptyList(),
            currentTimeZone = "America/Chicago",
            privacyMode = PrivacyMode.CLOUD_ALLOWED
        )

        assertTrue(result.proposedBlocks.isNotEmpty())
        assertTrue(
            result.explanation.contains("Cloud Gemini was unavailable") ||
                result.explanation.contains("local")
        )
    }
}
