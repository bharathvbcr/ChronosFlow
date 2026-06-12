package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.AssistTextGeneration
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.HabitDailyCompletion
import com.chronosflow.core.domain.model.MedicationDailyAdherence
import com.chronosflow.core.domain.model.MoodEnergyHourAverage
import com.chronosflow.core.domain.model.MoodEnergyTrends
import com.chronosflow.core.domain.model.ReviewInsight
import com.chronosflow.core.domain.model.ReviewInsightSeverity
import com.chronosflow.core.domain.model.ReviewInsightType
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
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

    @Test
    fun `suggest includes trend lines in the prompt`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        val promptSlot = slot<String>()
        coEvery { coordinator.generateAssistText(capture(promptSlot)) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = InsightsRecommendationsPlanner(coordinator)

        planner.suggest(sampleSummary(), emptyList(), sampleTrends())

        val prompt = promptSlot.captured
        assertTrue(prompt.contains("Peak energy hour over the last 14 days: 9:00"))
        assertTrue(prompt.contains("Habit completions last 7 days: 3; prior 7 days: 7"))
        assertTrue(prompt.contains("Medication doses last 14 days: 10 taken, 4 missed"))
    }

    @Test
    fun `localRecommendations surfaces trend guidance when no insights exist`() {
        val planner = InsightsRecommendationsPlanner(mockk(relaxed = true))

        val recommendations = planner.localRecommendations(
            summary = sampleSummary(driftMinutes = 0).copy(missedMinutes = 0, insights = emptyList()),
            insights = emptyList(),
            trends = sampleTrends()
        )

        val texts = recommendations.map { it.text }
        assertTrue(texts.any { it.contains("Energy usually peaks around 09:00") })
        assertTrue(texts.any { it.contains("4 medication doses slipped") })
        assertTrue(texts.any { it.contains("Habit completions dipped") })
    }

    @Test
    fun `empty trends leave local recommendations unchanged`() {
        val planner = InsightsRecommendationsPlanner(mockk(relaxed = true))

        val withDefault = planner.localRecommendations(sampleSummary(), emptyList())
        val withEmpty = planner.localRecommendations(sampleSummary(), emptyList(), CompanionTrendContext())

        assertEquals(withDefault, withEmpty)
    }

    private fun sampleTrends(): CompanionTrendContext {
        val today = LocalDate.of(2026, 6, 11)
        return CompanionTrendContext(
            moodEnergyTrends = MoodEnergyTrends(
                hourOfDayAverages = listOf(
                    MoodEnergyHourAverage(hourOfDay = 9, avgMood = 4f, avgStress = 2f, avgEnergy = 4.5f, avgFocus = 4f, sampleCount = 5),
                    MoodEnergyHourAverage(hourOfDay = 15, avgMood = 3f, avgStress = 3f, avgEnergy = 2.5f, avgFocus = 3f, sampleCount = 5)
                )
            ),
            habitCompletion = (0 until 14).map { offset ->
                HabitDailyCompletion(
                    date = today.minusDays((13 - offset).toLong()),
                    // Prior week completes daily; last week falls off.
                    completedCount = if (offset < 7) 1 else if (offset % 2 == 0) 1 else 0,
                    missedCount = 0
                )
            },
            medicationAdherence = (0 until 14).map { offset ->
                MedicationDailyAdherence(
                    date = today.minusDays((13 - offset).toLong()),
                    takenCount = if (offset % 7 < 5) 1 else 0,
                    missedCount = if (offset % 7 >= 5) 1 else 0
                )
            }
        )
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
