package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.TimeBlock
import javax.inject.Inject

class PlanExplainAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun explainPlan(
        blocks: List<TimeBlock>,
        review: DailyReviewSummary,
        timezone: String,
        privacyMode: PrivacyMode
    ): PlanAssistExplanation {
        val baseline = localExplanation(blocks, review, timezone)
        if (privacyMode == PrivacyMode.DISABLED) {
            return PlanAssistExplanation(
                text = "Planning explanations are disabled. Enable on-device or cloud planning to summarize this day.",
                source = AssistGenAiSource.LOCAL
            )
        }
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(blocks, review, timezone, baseline.text),
            privacyMode
        )
        val text = generation.text?.trim()?.lineSequence()?.firstOrNull { it.isNotBlank() }
            ?.takeIf { it.isNotBlank() }
        return PlanAssistExplanation(
            text = text ?: baseline.text,
            source = if (text != null) generation.source else AssistGenAiSource.LOCAL
        )
    }

    private fun buildPrompt(
        blocks: List<TimeBlock>,
        review: DailyReviewSummary,
        timezone: String,
        baseline: String
    ): String = buildString {
        appendLine("Explain today's ChronosFlow schedule in 2-3 sentences for the user.")
        appendLine("Use only supplied metrics. No invented tasks.")
        appendLine("Timezone: $timezone")
        appendLine("Planned minutes: ${review.plannedMinutes}")
        appendLine("Actual minutes: ${review.actualMinutes}")
        appendLine("Missed minutes: ${review.missedMinutes}")
        appendLine("Drift minutes: ${review.driftMinutes}")
        appendLine("Block count: ${blocks.size}")
        appendLine("Flexible blocks: ${blocks.count { it.flexibility.name != "FIXED" }}")
        appendLine("Protected blocks: ${blocks.count { it.isProtected || it.isLocked }}")
        review.insights.take(3).forEach { insight ->
            appendLine("Insight: ${insight.title}")
        }
        appendLine("Baseline explanation: $baseline")
    }

    private fun localExplanation(
        blocks: List<TimeBlock>,
        review: DailyReviewSummary,
        timezone: String
    ): PlanAssistExplanation {
        val totalPlanned = blocks.sumOf { it.durationMinutes }
        val movable = blocks.count { it.flexibility.name != "FIXED" }
        val protected = blocks.count { it.isProtected || it.isLocked }
        val strongestInsight = review.insights.maxByOrNull { it.severity.ordinal }?.title
        val text = buildString {
            append(
                "Planned $totalPlanned minutes in $timezone with $movable flexible blocks and $protected protected commitments."
            )
            append(
                " Review: actual ${review.actualMinutes}m vs planned ${review.plannedMinutes}m, missed ${review.missedMinutes}m, drift ${review.driftMinutes}m."
            )
            strongestInsight?.let { append(" Primary signal: $it.") }
        }
        return PlanAssistExplanation(text = text, source = AssistGenAiSource.LOCAL)
    }
}
