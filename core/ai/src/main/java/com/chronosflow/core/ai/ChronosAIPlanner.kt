package com.chronosflow.core.ai

import android.content.Context
import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.CloudGeminiModels
import com.chronosflow.core.ai.genai.DayPlanResponseParser
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.GenAiRuntimeStatus
import com.chronosflow.core.ai.genai.LocalPlanningHeuristics
import com.chronosflow.core.ai.genai.PlanningPromptBuilder
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.TimeBlock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

enum class PrivacyMode {
    ON_DEVICE_ONLY,
    CLOUD_ALLOWED,
    DISABLED
}

data class ProposedSuggestionBlock(
    val id: String,
    val title: String,
    val category: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val provenance: BlockProvenance,
    val flexibility: BlockFlexibility,
    val isLocked: Boolean,
    val isProtected: Boolean,
    val timezone: String
)

data class StructuredDayPlanSuggestion(
    val proposedBlocks: List<ProposedSuggestionBlock>,
    val reason: String,
    val conflictsResolved: List<String>,
    val requireConfirmation: Boolean,
    val explanation: String,
    val explanationSource: AssistGenAiSource = AssistGenAiSource.LOCAL
)

@Singleton
class ChronosAIPlanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    private val deepWorkWindowDetector = DeepWorkWindowDetector()

    val genAiRuntimeStatus: StateFlow<GenAiRuntimeStatus> = genAiAssistCoordinator.runtimeStatus

    suspend fun refreshGenAiStatus(): GenAiRuntimeStatus {
        return genAiAssistCoordinator.refreshRuntimeStatus()
    }

    suspend fun generateIdealDayPlan(
        userPreferences: String,
        date: LocalDate,
        currentTimeZone: String,
        privacyMode: PrivacyMode
    ): StructuredDayPlanSuggestion {
        val todayLabel = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        if (privacyMode == PrivacyMode.DISABLED) {
            return disabledSuggestion(todayLabel)
        }

        val prompt = PlanningPromptBuilder.dayPlanPrompt(
            userPreferences = userPreferences,
            date = date,
            timezone = currentTimeZone,
            review = null,
            existingBlocks = emptyList()
        )

        return when (privacyMode) {
            PrivacyMode.ON_DEVICE_ONLY -> generateWithNanoOrFallback(
                prompt = prompt,
                timezone = currentTimeZone,
                date = date,
                userPreferences = userPreferences,
                review = null,
                existingBlocks = emptyList()
            )
            PrivacyMode.CLOUD_ALLOWED -> generateWithCloudOrFallback(
                prompt = prompt,
                timezone = currentTimeZone,
                date = date,
                userPreferences = userPreferences,
                review = null,
                existingBlocks = emptyList()
            )
            PrivacyMode.DISABLED -> disabledSuggestion(todayLabel)
        }
    }

    suspend fun repairDayPlan(
        currentPlan: String,
        conflictDescription: String,
        privacyMode: PrivacyMode
    ): String {
        if (privacyMode == PrivacyMode.DISABLED) {
            return "Plan repair is disabled. Enable on-device planning to review conflicts without sending schedule data to a cloud model."
        }

        val prompt = PlanningPromptBuilder.repairPrompt(currentPlan, conflictDescription)
        genAiAssistCoordinator.generateAssistText(prompt, privacyMode).text?.let { return it }
        return LocalPlanningHeuristics.repairDayPlan(currentPlan, conflictDescription)
    }

    suspend fun generateReviewBackedDayPlan(
        userPreferences: String,
        review: DailyReviewSummary,
        existingBlocks: List<TimeBlock>,
        currentTimeZone: String,
        privacyMode: PrivacyMode
    ): StructuredDayPlanSuggestion {
        if (privacyMode == PrivacyMode.DISABLED) {
            return StructuredDayPlanSuggestion(
                proposedBlocks = emptyList(),
                reason = "Planning suggestions are disabled for ${review.date}.",
                conflictsResolved = emptyList(),
                requireConfirmation = true,
                explanation = "Turn on on-device planning or cloud-allowed planning to generate review-backed suggestions."
            )
        }

        val prompt = PlanningPromptBuilder.dayPlanPrompt(
            userPreferences = userPreferences,
            date = review.date,
            timezone = currentTimeZone,
            review = review,
            existingBlocks = existingBlocks
        )

        val generated = when (privacyMode) {
            PrivacyMode.ON_DEVICE_ONLY -> generateWithNanoOrFallback(
                prompt = prompt,
                timezone = currentTimeZone,
                date = review.date,
                userPreferences = userPreferences,
                review = review,
                existingBlocks = existingBlocks
            )
            PrivacyMode.CLOUD_ALLOWED -> generateWithCloudOrFallback(
                prompt = prompt,
                timezone = currentTimeZone,
                date = review.date,
                userPreferences = userPreferences,
                review = review,
                existingBlocks = existingBlocks
            )
            PrivacyMode.DISABLED -> return StructuredDayPlanSuggestion(
                proposedBlocks = emptyList(),
                reason = "Planning suggestions are disabled for ${review.date}.",
                conflictsResolved = emptyList(),
                requireConfirmation = true,
                explanation = "Turn on on-device planning or cloud-allowed planning to generate review-backed suggestions."
            )
        }

        if (review.missedMinutes == 0 && review.missedBlockCount == 0 && review.driftMinutes == 0) {
            return generated
        }

        val baseline = generated.copy(
            explanation = generated.explanation,
            requireConfirmation = true
        )
        return LocalPlanningHeuristics.generateReviewBackedDayPlan(
            packageName = context.packageName,
            userPreferences = userPreferences,
            review = review,
            existingBlocks = existingBlocks,
            currentTimeZone = currentTimeZone,
            baseline = baseline
        )
    }

    fun explainPlan(blocks: List<TimeBlock>, timezone: String): String {
        val totalPlanned = blocks.sumOf { it.durationMinutes }
        val movable = blocks.count { it.flexibility.name != "FIXED" }
        val protected = blocks.count { it.isProtected || it.isLocked }
        return "Planned $totalPlanned minutes in $timezone with $movable flexible blocks and $protected protected commitments. Review conflicts and locked items before finalizing."
    }

    fun recommendDeepWorkWindows(blocks: List<TimeBlock>) = deepWorkWindowDetector.detect(blocks)

    fun explainPlan(blocks: List<TimeBlock>, review: DailyReviewSummary, timezone: String): String {
        val base = explainPlan(blocks, timezone)
        val strongestInsight = review.insights.maxByOrNull { it.severity.ordinal }?.title
        return buildString {
            append(base)
            append(" Review summary: actual ${review.actualMinutes}m vs planned ${review.plannedMinutes}m, missed ${review.missedMinutes}m, drift ${review.driftMinutes}m.")
            strongestInsight?.let { append(" Primary review signal: $it.") }
        }
    }

    fun blocksToDomainTimeBlocks(
        sourceBlocks: List<ProposedSuggestionBlock>,
        date: LocalDate
    ): List<TimeBlock> = LocalPlanningHeuristics.blocksToDomainTimeBlocks(sourceBlocks, date)

    private suspend fun generateWithNanoOrFallback(
        prompt: String,
        timezone: String,
        date: LocalDate,
        userPreferences: String,
        review: DailyReviewSummary?,
        existingBlocks: List<TimeBlock>
    ): StructuredDayPlanSuggestion {
        return generateFromAssistText(
            prompt = prompt,
            timezone = timezone,
            date = date,
            userPreferences = userPreferences,
            review = review,
            existingBlocks = existingBlocks,
            privacyMode = PrivacyMode.ON_DEVICE_ONLY
        )
    }

    private suspend fun generateWithCloudOrFallback(
        prompt: String,
        timezone: String,
        date: LocalDate,
        userPreferences: String,
        review: DailyReviewSummary?,
        existingBlocks: List<TimeBlock>
    ): StructuredDayPlanSuggestion {
        return generateFromAssistText(
            prompt = prompt,
            timezone = timezone,
            date = date,
            userPreferences = userPreferences,
            review = review,
            existingBlocks = existingBlocks,
            privacyMode = PrivacyMode.CLOUD_ALLOWED
        )
    }

    private suspend fun generateFromAssistText(
        prompt: String,
        timezone: String,
        date: LocalDate,
        userPreferences: String,
        review: DailyReviewSummary?,
        existingBlocks: List<TimeBlock>,
        privacyMode: PrivacyMode
    ): StructuredDayPlanSuggestion {
        val generation = genAiAssistCoordinator.generateAssistText(prompt, privacyMode)
        generation.text?.let { raw ->
            val parsed = DayPlanResponseParser.parse(
                raw = raw,
                timezone = timezone,
                fallbackExplanation = when (generation.source) {
                    AssistGenAiSource.CLOUD_GEMINI -> "Generated with cloud Gemini. Review before applying."
                    AssistGenAiSource.GEMINI_NANO -> "Generated with Gemini Nano on-device via AICore. Review before applying."
                    AssistGenAiSource.LOCAL -> "Generated with local heuristics."
                }
            )
            if (parsed != null) {
                return parsed.copy(
                    reason = dayPlanReason(date, generation.source),
                    explanation = dayPlanExplanation(parsed.explanation, generation.source),
                    requireConfirmation = generation.source != AssistGenAiSource.LOCAL,
                    explanationSource = generation.source
                )
            }
        }

        val heuristic = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName = context.packageName,
            userPreferences = userPreferences,
            date = date,
            currentTimeZone = timezone
        )
        val fallbackExplanation = when (generation.source) {
            AssistGenAiSource.CLOUD_GEMINI ->
                "Cloud Gemini was unavailable, so ChronosFlow fell back to Gemini Nano when supported on this device and otherwise used local heuristics."
            AssistGenAiSource.GEMINI_NANO ->
                "Gemini Nano was unavailable or not ready on this device, so ChronosFlow used local heuristics."
            AssistGenAiSource.LOCAL -> heuristic.explanation
        }
        val resolvedSource = if (generation.text == null) AssistGenAiSource.LOCAL else generation.source
        return if (review != null) {
            LocalPlanningHeuristics.generateReviewBackedDayPlan(
                packageName = context.packageName,
                userPreferences = userPreferences,
                review = review,
                existingBlocks = existingBlocks,
                currentTimeZone = timezone,
                baseline = heuristic.copy(explanation = fallbackExplanation)
            ).copy(explanationSource = resolvedSource)
        } else {
            heuristic.copy(explanation = fallbackExplanation, explanationSource = resolvedSource)
        }
    }

    private fun dayPlanReason(date: LocalDate, source: AssistGenAiSource): String {
        val label = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        return when (source) {
            AssistGenAiSource.CLOUD_GEMINI -> "Cloud Gemini day plan for $label."
            AssistGenAiSource.GEMINI_NANO -> "Gemini Nano day plan for $label."
            AssistGenAiSource.LOCAL -> "Local day plan for $label."
        }
    }

    private fun dayPlanExplanation(base: String, source: AssistGenAiSource): String = when (source) {
        AssistGenAiSource.CLOUD_GEMINI ->
            "ChronosFlow used cloud Gemini (${CloudGeminiModels.CLOUD_MODEL}). $base"
        AssistGenAiSource.GEMINI_NANO ->
            "ChronosFlow used Gemini Nano on-device (AICore). $base"
        AssistGenAiSource.LOCAL -> base
    }

    private fun disabledSuggestion(todayLabel: String) = StructuredDayPlanSuggestion(
        proposedBlocks = emptyList(),
        reason = "Planning suggestions are disabled for $todayLabel.",
        conflictsResolved = emptyList(),
        requireConfirmation = true,
        explanation = "Turn on on-device planning or cloud-allowed planning to generate suggestions."
    )
}
