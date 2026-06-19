package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import javax.inject.Inject

data class RecommendationPlanIntent(
    val planGoals: List<String>,
    val focusSummary: String,
    val source: AssistGenAiSource,
    val quickAction: RecommendationQuickAction = RecommendationQuickAction.OPEN_AI_PLAN
)

class RecommendationPlanInterpreter @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun interpret(
        recommendation: String,
        review: DailyReviewSummary?,
        blocks: List<TimeBlock>
    ): RecommendationPlanIntent {
        val baseline = localInterpret(recommendation)
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(recommendation, review, blocks, baseline)
        )
        val parsed = generation.text?.let { parseGoals(it, generation.source, recommendation, baseline.quickAction) }
        return parsed ?: baseline
    }

    private fun buildPrompt(
        recommendation: String,
        review: DailyReviewSummary?,
        blocks: List<TimeBlock>,
        baseline: RecommendationPlanIntent
    ): String = buildString {
        appendLine("Convert one insights-tab recommendation into ChronosFlow day-plan goals.")
        appendLine("Return up to three goals, one per line. No medical advice.")
        appendLine("Recommendation: $recommendation")
        appendLine("Blocks today: ${blocks.size}")
        review?.let {
            appendLine("Planned minutes: ${it.plannedMinutes}")
            appendLine("Actual minutes: ${it.actualMinutes}")
            appendLine("Drift minutes: ${it.driftMinutes}")
            appendLine("Missed minutes: ${it.missedMinutes}")
        }
        baseline.planGoals.forEach { appendLine("Baseline goal: $it") }
    }

    private fun parseGoals(
        text: String,
        source: AssistGenAiSource,
        recommendation: String,
        quickAction: RecommendationQuickAction
    ): RecommendationPlanIntent? {
        val goals = text.lineSequence()
            .map { it.trim().trim('-', '*', '•') }
            .filter { it.isNotBlank() }
            .take(3)
            .toList()
        if (goals.isEmpty()) return null
        return RecommendationPlanIntent(
            planGoals = goals,
            focusSummary = recommendation,
            source = source,
            quickAction = quickAction
        )
    }

    private fun localInterpret(recommendation: String): RecommendationPlanIntent {
        val quickAction = inferQuickAction(recommendation)
        val goals = buildList {
            add("Apply insight recommendation: $recommendation")
            when {
                recommendation.contains("buffer", ignoreCase = true) ->
                    add("Add short buffer blocks between dense meetings")
                recommendation.contains("admin", ignoreCase = true) &&
                    recommendation.contains("lunch", ignoreCase = true) ->
                    add("Move administrative blocks after lunch")
                recommendation.contains("focus", ignoreCase = true) ||
                    recommendation.contains("deep work", ignoreCase = true) ->
                    add("Protect the strongest focus window as a fixed block")
                recommendation.contains("drift", ignoreCase = true) ->
                    add("Reduce planned load to lower tomorrow's drift")
            }
        }
        return RecommendationPlanIntent(
            planGoals = goals.distinct().take(3),
            focusSummary = recommendation,
            source = AssistGenAiSource.LOCAL,
            quickAction = quickAction
        )
    }

    internal fun inferQuickAction(recommendation: String): RecommendationQuickAction {
        val normalized = recommendation.lowercase()
        return when {
            normalized.contains("fill gap") ||
                normalized.contains("fill empty") ||
                normalized.contains("empty time") ||
                normalized.contains("open gap") ||
                (normalized.contains("drift") && normalized.contains("reduce")) ->
                RecommendationQuickAction.FILL_GAPS
            normalized.contains("break") ||
                normalized.contains("buffer") ||
                normalized.contains("recovery") ->
                RecommendationQuickAction.ADD_BREAK
            else -> RecommendationQuickAction.OPEN_AI_PLAN
        }
    }
}
