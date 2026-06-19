package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyCheckIn
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyCorrelationEngineTest {
    private val coordinator = mockk<GenAiAssistCoordinator>(relaxed = true)
    private val engine = EnergyCorrelationEngine(coordinator)

    @Test
    fun `returns insufficient data insight when check-ins are sparse`() {
        val insights = engine.analyze(
            blocks = emptyList(),
            actualSegments = emptyList(),
            focusSessions = emptyList(),
            checkIns = emptyList()
        )
        assertEquals(ReviewInsightType.ENERGY_PEAK, insights.first().type)
        assertTrue(insights.first().title.contains("Not enough data"))
    }

    @Test
    fun `detects energy peak with multiple check-ins`() {
        val checkIns = listOf(
            checkIn(hour = 9, energy = 5),
            checkIn(hour = 10, energy = 4),
            checkIn(hour = 14, energy = 2)
        )
        val insights = engine.analyze(
            blocks = emptyList(),
            actualSegments = emptyList(),
            focusSessions = emptyList(),
            checkIns = checkIns
        )
        assertTrue(insights.any { it.type == ReviewInsightType.ENERGY_PEAK })
    }

    @Test
    fun `analyzeWithAssist merges AI insights when available`() = runTest {
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "HABIT_CORRELATION|Habits lag focus|Protect one anchor block before adding habits.",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val checkIns = listOf(
            checkIn(hour = 9, energy = 5),
            checkIn(hour = 10, energy = 4),
            checkIn(hour = 14, energy = 2)
        )

        val insights = engine.analyzeWithAssist(
            blocks = emptyList(),
            actualSegments = emptyList(),
            focusSessions = emptyList(),
            checkIns = checkIns
        )

        assertTrue(insights.any { it.type == ReviewInsightType.HABIT_CORRELATION })
        assertTrue(insights.any { it.source == AssistGenAiSource.GEMINI_NANO })
        val converted = engine.toReviewInsights(insights, LocalDate.of(2026, 5, 24))
        assertEquals(AssistGenAiSource.GEMINI_NANO.name, converted.first { it.type == ReviewInsightType.HABIT_CORRELATION }.assistSource)
    }

    private fun checkIn(hour: Int, energy: Int): MoodEnergyCheckIn = MoodEnergyCheckIn(
        id = "c-$hour",
        blockId = null,
        moodScore = 3,
        stressScore = 2,
        energyScore = energy,
        focusScore = 3,
        notes = null,
        recordedAt = LocalDateTime.of(2026, 5, 24, hour, 0),
        checkInDate = LocalDate.of(2026, 5, 24)
    )
}
