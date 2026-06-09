package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.AssistTextGeneration
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.ReviewInsightType
import com.chronosflow.core.domain.model.TimeBlock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeepWorkAssistPlannerTest {
    @Test
    fun `suggestInsight returns AI-enriched deep work insight`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "Protect 9:30 focus|Keep notifications off for the first deep-work block.",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val energyEngine = mockk<EnergyCorrelationEngine>(relaxed = true)
        every {
            energyEngine.deepWorkCandidates(
                blocks = any(),
                checkIns = any(),
                detector = any()
            )
        } returns listOf(
            DeepWorkCandidate(
                startMinuteOfDay = 570,
                endMinuteOfDay = 690,
                score = 80,
                reason = "Long open window before meetings",
                confidence = 0.8f
            )
        )
        val planner = DeepWorkAssistPlanner(coordinator, energyEngine)
        val blocks = listOf(sampleBlock())

        val insight = planner.suggestInsight(blocks, emptyList(), LocalDate.of(2026, 5, 25))

        assertNotNull(insight)
        assertEquals(ReviewInsightType.DEEP_WORK_WINDOW, insight?.type)
        assertEquals(AssistGenAiSource.GEMINI_NANO.name, insight?.assistSource)
        assertEquals("Protect 9:30 focus", insight?.title)
    }

    private fun sampleBlock(): TimeBlock {
        val now = Instant.parse("2026-05-25T08:00:00Z")
        return TimeBlock(
            id = "block-1",
            date = LocalDate.of(2026, 5, 25),
            title = "Email",
            category = "ADMIN",
            startMinuteOfDay = 480,
            durationMinutes = 60,
            timezone = "UTC",
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.MOVABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "test",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = now,
            updatedAt = now
        )
    }
}
