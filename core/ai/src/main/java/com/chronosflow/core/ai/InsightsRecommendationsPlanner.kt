package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.ReviewInsight
import javax.inject.Inject

data class InsightRecommendation(
    val text: String,
    val source: AssistGenAiSource
)

class InsightsRecommendationsPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggest(
        summary: DailyReviewSummary?,
        insights: List<ReviewInsight>
    ): List<InsightRecommendation> {
        val baseline = localRecommendations(summary, insights)
        if (summary == null) return baseline
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(summary, insights, baseline)
        )
        val parsed = generation.text?.let { parseRecommendations(it, generation.source) }.orEmpty()
        return (parsed.ifEmpty { baseline }).take(3)
    }

    fun localRecommendations(
        summary: DailyReviewSummary?,
        insights: List<ReviewInsight>
    ): List<InsightRecommendation> {
        if (insights.isNotEmpty()) {
            return insights.take(3).map { insight ->
                InsightRecommendation(
                    text = insight.title,
                    source = insight.assistSource?.let { name ->
                        runCatching { AssistGenAiSource.valueOf(name) }.getOrNull()
                    } ?: AssistGenAiSource.LOCAL
                )
            }
        }
        if (summary == null) {
            return listOf(
                InsightRecommendation(
                    text = "Complete a focus session or end-of-day review to unlock recommendations.",
                    source = AssistGenAiSource.LOCAL
                )
            )
        }
        return buildList {
            if (summary.driftMinutes >= 60) {
                add(
                    InsightRecommendation(
                        text = "Protect fewer priorities tomorrow after ${summary.driftMinutes}m of drift.",
                        source = AssistGenAiSource.LOCAL
                    )
                )
            }
            if (summary.missedMinutes >= 30) {
                add(
                    InsightRecommendation(
                        text = "Recover ${summary.missedMinutes}m of missed plan with a single catch-up block.",
                        source = AssistGenAiSource.LOCAL
                    )
                )
            }
            if (isEmpty()) {
                add(
                    InsightRecommendation(
                        text = "Keep today's rhythm and promote the strongest completed block into tomorrow's anchor.",
                        source = AssistGenAiSource.LOCAL
                    )
                )
            }
        }.take(3)
    }

    private fun buildPrompt(
        summary: DailyReviewSummary,
        insights: List<ReviewInsight>,
        baseline: List<InsightRecommendation>
    ): String = buildString {
        appendLine("Write up to three schedule recommendations for ChronosFlow insights.")
        appendLine("Return one recommendation per line as recommendation|reason.")
        appendLine("Use only supplied metrics. No invented tasks or medical advice.")
        appendLine("Planned minutes: ${summary.plannedMinutes}")
        appendLine("Actual minutes: ${summary.actualMinutes}")
        appendLine("Missed minutes: ${summary.missedMinutes}")
        appendLine("Drift minutes: ${summary.driftMinutes}")
        insights.take(5).forEach { insight ->
            appendLine("Insight: ${insight.title} — ${insight.detail}")
        }
        baseline.forEach { recommendation ->
            appendLine("Baseline: ${recommendation.text}")
        }
    }

    private fun parseRecommendations(
        text: String,
        source: AssistGenAiSource
    ): List<InsightRecommendation> = text.lineSequence()
        .map { it.trim().trim('-', '*') }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("|", limit = 2).map { it.trim() }
            val recommendation = parts.firstOrNull().orEmpty()
            if (recommendation.isBlank()) return@mapNotNull null
            InsightRecommendation(text = recommendation, source = source)
        }
        .take(3)
        .toList()
}
