package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.ReviewInsight
import javax.inject.Inject

data class WeeklyReviewContext(
    val daysReviewed: Int,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val missedMinutes: Int,
    val driftMinutes: Int,
    val completedBlocks: Int,
    val missedBlocks: Int,
    val insightCount: Int
) {
    val completionPercent: Int
        get() = if (plannedMinutes == 0) 0 else ((actualMinutes.toFloat() / plannedMinutes) * 100).toInt()
}

class ReviewAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggestSummary(
        summary: DailyReviewSummary,
        weekly: WeeklyReviewContext
    ): AssistNarrative {
        val baseline = localSummary(summary, weekly)
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(summary, weekly, baseline)
        )
        return generation.text?.let { raw ->
            parseAssistNarrative(
                text = raw,
                source = generation.source,
                fallbackHeadline = baseline.headline,
                fallbackNextStep = baseline.nextStep
            )
        } ?: baseline
    }

    private fun buildPrompt(
        summary: DailyReviewSummary,
        weekly: WeeklyReviewContext,
        baseline: AssistNarrative
    ): String = buildString {
        appendLine("Write a daily review coach summary for ChronosFlow.")
        appendLine("Return one line as headline|nextStep.")
        appendLine("Use only the metrics provided. No invented tasks.")
        appendLine("Today planned minutes: ${summary.plannedMinutes}")
        appendLine("Today actual minutes: ${summary.actualMinutes}")
        appendLine("Today missed minutes: ${summary.missedMinutes}")
        appendLine("Today drift minutes: ${summary.driftMinutes}")
        appendLine("Today completed blocks: ${summary.completedBlockCount}")
        appendLine("Today missed blocks: ${summary.missedBlockCount}")
        appendLine("Today insight count: ${summary.insights.size}")
        appendLine("7-day completion percent: ${weekly.completionPercent}")
        appendLine("7-day insight count: ${weekly.insightCount}")
        summary.insights.take(3).forEach { insight ->
            appendLine("Insight: ${insight.title} — ${insight.detail}")
        }
        appendLine("Baseline headline: ${baseline.headline}")
        appendLine("Baseline next step: ${baseline.nextStep}")
    }

    private fun localSummary(
        summary: DailyReviewSummary,
        weekly: WeeklyReviewContext
    ): AssistNarrative {
        val headline = when {
            summary.driftMinutes >= 90 ->
                "High drift today: ${summary.driftMinutes} minutes moved off-plan."
            summary.missedMinutes >= 60 ->
                "Several planned blocks slipped today."
            else ->
                "Today stayed reasonably close to plan."
        }
        val nextStep = when {
            summary.driftMinutes >= 90 ->
                "Protect fewer priorities tomorrow and reserve a recovery block for spillover."
            weekly.completionPercent < 65 ->
                "Protect the first high-value block earlier in the day before filling the rest of the plan."
            else ->
                "Keep the current rhythm and promote the strongest completed block into tomorrow's anchor."
        }
        return AssistNarrative(
            headline = headline,
            nextStep = nextStep,
            source = com.chronosflow.core.ai.genai.AssistGenAiSource.LOCAL
        )
    }
}
