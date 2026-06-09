package com.chronosflow.feature.daydial.delegate

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.ai.ChronosAIPlanner
import com.chronosflow.core.ai.PlanExplainAssistPlanner
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.ProposedSuggestionBlock
import com.chronosflow.core.ai.RecommendationPlanIntent
import com.chronosflow.core.ai.RecommendationPlanInterpreter
import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.data.privacy.AssistantPreferences
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.ApplyAiPlanUseCase
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.feature.daydial.TimeBlockUiModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DayDialAiDelegate @Inject constructor(
    private val repository: TimeBlockRepository,
    private val sleepScheduleRepository: SleepScheduleRepository,
    private val aiPlanner: ChronosAIPlanner,
    private val planExplainAssistPlanner: PlanExplainAssistPlanner,
    private val recommendationPlanInterpreter: RecommendationPlanInterpreter,
    private val applyAiPlanUseCase: ApplyAiPlanUseCase,
    private val assistantPreferences: AssistantPreferences
) {
    private val _aiPlanResult = MutableStateFlow<String?>(null)
    val aiPlanResult = _aiPlanResult.asStateFlow()

    private val _privacyMode = MutableStateFlow(initialPrivacyMode())
    val privacyMode = _privacyMode.asStateFlow()

    private val _previewOnDeviceModel = MutableStateFlow(assistantPreferences.preferPreviewNanoModel())
    val previewOnDeviceModel = _previewOnDeviceModel.asStateFlow()

    val genAiRuntimeStatus = aiPlanner.genAiRuntimeStatus

    private val _suggestedBlocks = MutableStateFlow<List<TimeBlockUiModel>>(emptyList())
    val suggestedBlocks = _suggestedBlocks.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _explainPlan = MutableStateFlow<String?>(null)
    val explainPlan = _explainPlan.asStateFlow()

    private val _explainPlanSource = MutableStateFlow<AssistGenAiSource?>(null)
    val explainPlanSource = _explainPlanSource.asStateFlow()

    private val _aiPlanGoalPrefill = MutableStateFlow<String?>(null)
    val aiPlanGoalPrefill = _aiPlanGoalPrefill.asStateFlow()

    private val _aiPlanSuggestedGoals = MutableStateFlow<List<String>>(emptyList())
    val aiPlanSuggestedGoals = _aiPlanSuggestedGoals.asStateFlow()

    fun setAiPlanPrefillFromIntent(intent: RecommendationPlanIntent) {
        _aiPlanGoalPrefill.value = intent.focusSummary
        _aiPlanSuggestedGoals.value = intent.planGoals
    }

    fun clearAiPlanPrefill() {
        _aiPlanGoalPrefill.value = null
        _aiPlanSuggestedGoals.value = emptyList()
    }

    fun setPrivacyMode(mode: PrivacyMode) {
        _privacyMode.value = mode
        assistantPreferences.setAssistantPrivacyModeValue(mode.name)
    }

    fun setPreviewOnDeviceModel(enabled: Boolean) {
        _previewOnDeviceModel.value = enabled
        assistantPreferences.setPreferPreviewNanoModel(enabled)
    }

    fun refreshGenAiStatus(scope: CoroutineScope) {
        scope.launch {
            aiPlanner.refreshGenAiStatus()
        }
    }

    fun requestPlan(
        scope: CoroutineScope,
        date: LocalDate,
        goals: List<String>,
        reviewProvider: suspend (List<TimeBlock>) -> DailyReviewSummary
    ) {
        scope.launch {
            _aiPlanResult.value = null
            _explainPlan.value = null
            _explainPlanSource.value = null
            _suggestedBlocks.value = emptyList()
            _isGenerating.value = true
            try {
                aiPlanner.refreshGenAiStatus()
                val blocks = repository.getTimeBlocksByDate(date).first()
                val sleepSchedule = sleepScheduleRepository.getSleepSchedule()
                val review = reviewProvider(blocks)
                val result = aiPlanner.generateReviewBackedDayPlan(
                    userPreferences = goals.withSleepWindow(sleepSchedule),
                    review = review,
                    existingBlocks = blocks,
                    currentTimeZone = ZoneId.systemDefault().id,
                    privacyMode = _privacyMode.value
                )
                val filteredSuggestions = result.proposedBlocks
                    .filterNot { sleepSchedule.intersects(it.startMinuteOfDay, it.durationMinutes) }
                val filteredCount = result.proposedBlocks.size - filteredSuggestions.size
                _aiPlanResult.value = if (filteredCount > 0) {
                    "${result.reason} Filtered $filteredCount suggestion(s) that overlapped sleep hours."
                } else {
                    result.reason
                }
                _explainPlan.value = result.explanation
                _explainPlanSource.value = result.explanationSource
                _suggestedBlocks.value = filteredSuggestions.map { it.toUiSuggestion() }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun explainCurrentPlan(
        scope: CoroutineScope,
        date: LocalDate,
        reviewProvider: suspend (List<TimeBlock>) -> DailyReviewSummary
    ) {
        scope.launch {
            val blocks = repository.getTimeBlocksByDate(date).first()
            val review = reviewProvider(blocks)
            val explanation = planExplainAssistPlanner.explainPlan(
                blocks = blocks,
                review = review,
                timezone = ZoneId.systemDefault().id,
                privacyMode = _privacyMode.value
            )
            _explainPlan.value = explanation.text
            _explainPlanSource.value = explanation.source
        }
    }

    suspend fun interpretRecommendation(
        recommendation: String,
        date: LocalDate,
        blocks: List<TimeBlock>,
        reviewProvider: suspend (List<TimeBlock>) -> DailyReviewSummary
    ): RecommendationPlanIntent {
        val review = reviewProvider(blocks)
        return recommendationPlanInterpreter.interpret(
            recommendation = recommendation,
            review = review,
            blocks = blocks
        )
    }

    fun applyAiSuggestions(
        scope: CoroutineScope,
        date: LocalDate,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val sleepSchedule = sleepScheduleRepository.getSleepSchedule()
            val createdBlocks = _suggestedBlocks.value.map { it.toAiTimeBlock(date) }
            val validBlocks = createdBlocks.filterNot {
                sleepSchedule.intersects(it.startMinuteOfDay, it.durationMinutes)
            }
            val skippedCount = createdBlocks.size - validBlocks.size
            val result = if (validBlocks.isEmpty()) {
                PlannerOperationResult.Rejected("AI suggestions overlap your sleep schedule", "")
            } else {
                val accepted = applyAiPlanUseCase(validBlocks)
                PlannerOperationResult.Applied(
                    message = if (skippedCount > 0) {
                        "Applied $accepted AI suggestions; skipped $skippedCount in sleep hours"
                    } else {
                        "Applied $accepted AI suggestions"
                    },
                    blockId = "",
                    snappedToMinute = null,
                    affectedBlockIds = validBlocks.take(accepted).map { it.id }
                )
            }
            onResult(result, false)
            _suggestedBlocks.value = emptyList()
        }
    }

    fun acceptSuggestion(
        scope: CoroutineScope,
        date: LocalDate,
        suggestionId: String,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val suggestion = _suggestedBlocks.value.firstOrNull { it.id == suggestionId } ?: return@launch
            val createdBlock = suggestion.toAiTimeBlock(date)
            val sleepSchedule = sleepScheduleRepository.getSleepSchedule()
            if (sleepSchedule.intersects(createdBlock.startMinuteOfDay, createdBlock.durationMinutes)) {
                onResult(PlannerOperationResult.Rejected("Suggestion overlaps your sleep schedule", createdBlock.id), false)
                return@launch
            }
            val accepted = applyAiPlanUseCase(listOf(createdBlock))
            val result = PlannerOperationResult.Applied(
                message = if (accepted > 0) "Accepted ${suggestion.title}" else "No AI suggestions accepted",
                blockId = createdBlock.id,
                snappedToMinute = null,
                affectedBlockIds = if (accepted > 0) listOf(createdBlock.id) else emptyList()
            )
            onResult(result, false)
            if (accepted > 0) {
                _suggestedBlocks.update { suggestions -> suggestions.filterNot { it.id == suggestionId } }
            }
        }
    }

    fun rejectSuggestion(suggestionId: String) {
        _suggestedBlocks.update { suggestions -> suggestions.filterNot { it.id == suggestionId } }
    }

    fun rejectAllSuggestions() {
        _suggestedBlocks.value = emptyList()
    }

    fun modifySuggestion(
        suggestionId: String,
        title: String,
        startMinuteOfDay: Int,
        durationMinutes: Int
    ) {
        _suggestedBlocks.update { suggestions ->
            suggestions.map { suggestion ->
                if (suggestion.id == suggestionId) {
                    suggestion.copy(
                        title = title.ifBlank { suggestion.title },
                        startMinuteOfDay = startMinuteOfDay.coerceIn(0, 1439),
                        durationMinutes = durationMinutes.coerceIn(5, 1440)
                    )
                } else {
                    suggestion
                }
            }
        }
    }

    private fun ProposedSuggestionBlock.toUiSuggestion(): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = id,
            title = title,
            startMinuteOfDay = startMinuteOfDay,
            durationMinutes = durationMinutes,
            color = Color(0xFF64B5F6),
            provenance = provenance.name,
            flexibility = flexibility.name,
            isLocked = isLocked,
            isProtected = isProtected,
            calendarEventId = null,
            taskId = null,
            habitId = null,
            medicationPlanId = null
        )
    }

    private fun TimeBlockUiModel.toAiTimeBlock(date: LocalDate): TimeBlock {
        return TimeBlock(
            id = id,
            date = date,
            title = title,
            category = "AI",
            startMinuteOfDay = startMinuteOfDay,
            durationMinutes = durationMinutes,
            timezone = ZoneId.systemDefault().id,
            provenance = BlockProvenance.AI_SUGGESTED,
            flexibility = BlockFlexibility.OPTIONAL,
            energyLevel = EnergyIntensity.MODERATE,
            source = "AI",
            taskId = taskId,
            calendarEventId = calendarEventId,
            medicationPlanId = medicationPlanId,
            habitId = habitId,
            isLocked = isLocked,
            isProtected = isProtected,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun List<String>.withSleepWindow(sleepSchedule: SleepSchedule): String {
        val base = joinToString()
        if (!sleepSchedule.isActive) return base
        val sleepInstruction = "Avoid scheduling between ${formatDisplayMinute(sleepSchedule.startMinute)} and ${formatDisplayMinute(sleepSchedule.endMinute)}."
        return if (base.isBlank()) sleepInstruction else "$base. $sleepInstruction"
    }

    private fun initialPrivacyMode(): PrivacyMode {
        return runCatching {
            PrivacyMode.valueOf(assistantPreferences.assistantPrivacyModeValue())
        }.getOrDefault(PrivacyMode.ON_DEVICE_ONLY)
    }
}
