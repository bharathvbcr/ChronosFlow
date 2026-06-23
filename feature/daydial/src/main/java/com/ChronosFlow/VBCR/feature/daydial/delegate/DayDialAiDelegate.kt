package com.ChronosFlow.VBCR.feature.daydial.delegate

import androidx.compose.ui.graphics.Color
import com.ChronosFlow.VBCR.core.ai.ChronosAIPlanner
import com.ChronosFlow.VBCR.core.ai.PlanExplainAssistPlanner
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.ai.ProposedSuggestionBlock
import com.ChronosFlow.VBCR.core.ai.RecommendationPlanIntent
import com.ChronosFlow.VBCR.core.ai.RecommendationPlanInterpreter
import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.data.privacy.AssistantPreferences
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.SleepReadiness
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.model.deriveSleepReadiness
import com.ChronosFlow.VBCR.core.domain.planner.GapFillBlock
import com.ChronosFlow.VBCR.core.domain.planner.GapFillHabitCandidate
import com.ChronosFlow.VBCR.core.domain.planner.GapFillPlanner
import com.ChronosFlow.VBCR.core.domain.planner.GapFillProposal
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepTrackRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.domain.usecase.ApplyAiPlanUseCase
import com.ChronosFlow.VBCR.core.ui.components.formatDisplayMinute
import com.ChronosFlow.VBCR.core.ui.theme.ChronosColors
import com.ChronosFlow.VBCR.feature.daydial.TimeBlockUiModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
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
    private val assistantPreferences: AssistantPreferences,
    private val gapFillPlanner: GapFillPlanner,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val sleepTrackRepository: SleepTrackRepository
) {
    private val _aiPlanResult = MutableStateFlow<String?>(null)
    val aiPlanResult = _aiPlanResult.asStateFlow()

    private val _privacyMode = MutableStateFlow(initialPrivacyMode())
    val privacyMode = _privacyMode.asStateFlow()

    private val _previewOnDeviceModel = MutableStateFlow(assistantPreferences.preferPreviewNanoModel())
    val previewOnDeviceModel = _previewOnDeviceModel.asStateFlow()

    private val _cloudAiEnabled = MutableStateFlow(assistantPreferences.isCloudAiEnabled())
    val cloudAiEnabled = _cloudAiEnabled.asStateFlow()

    val genAiRuntimeStatus = aiPlanner.genAiRuntimeStatus

    private val _suggestedBlocks = MutableStateFlow<List<TimeBlockUiModel>>(emptyList())
    val suggestedBlocks = _suggestedBlocks.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _explainPlan = MutableStateFlow<String?>(null)
    val explainPlan = _explainPlan.asStateFlow()

    private val _explainPlanSource = MutableStateFlow<AssistGenAiSource?>(null)
    val explainPlanSource = _explainPlanSource.asStateFlow()

    private val _repairPlanResult = MutableStateFlow<String?>(null)
    val repairPlanResult = _repairPlanResult.asStateFlow()

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

    /**
     * Persists the user's cloud AI consent decision and updates the live state so the UI reflects
     * the change immediately. The coordinator's [GenAiAssistCoordinator.privacyMode] gate reads
     * the same preference at call time, so the effect is immediate for the next generation request.
     */
    fun setCloudAiEnabled(enabled: Boolean) {
        _cloudAiEnabled.value = enabled
        assistantPreferences.setCloudAiEnabled(enabled)
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
                val readiness = readinessFor(date)
                val review = reviewProvider(blocks)
                val pendingTasks = runCatching { taskRepository.getAllTasks().first() }
                    .getOrDefault(emptyList())
                    .filterNot { it.isCompleted }
                val dueHabitTitles = runCatching { habitRepository.observeHabits().first() }
                    .getOrDefault(emptyList())
                    .filter { it.isActive && isHabitReminderDueOnDate(it, date) }
                    .map { it.title.ifBlank { "Habit" } }
                val result = aiPlanner.generateReviewBackedDayPlan(
                    userPreferences = goals.withSleepWindow(sleepSchedule).withSleepReadiness(readiness),
                    review = review,
                    existingBlocks = blocks,
                    currentTimeZone = ZoneId.systemDefault().id,
                    privacyMode = _privacyMode.value,
                    pendingTasks = pendingTasks,
                    dueHabitTitles = dueHabitTitles
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

    /**
     * Fills every qualifying gap with pending tasks and due habits (breaks last)
     * and publishes the result through the standard AI-suggestion preview flow,
     * so the user reviews and applies the plan from the AI sheet.
     */
    fun proposeGapFill(
        scope: CoroutineScope,
        date: LocalDate,
        addBreaksAutomatically: Boolean,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            _isGenerating.value = true
            try {
                val blocks = repository.getTimeBlocksByDate(date).first()
                val tasks = taskRepository.getAllTasks().first()
                val habits = habitRepository.observeHabits().first()
                val sleepSchedule = sleepScheduleRepository.getSleepSchedule()
                val nowMinuteOfDay = if (date == LocalDate.now()) {
                    java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                } else {
                    null
                }
                // Today's gap-fill leans lighter when last night ran short; other days are unaffected.
                val readiness = readinessFor(date)
                val habitCandidates = habits
                    .filter { it.isActive }
                    .filter { isHabitReminderDueOnDate(it, date) }
                    .map { habit ->
                        GapFillHabitCandidate(
                            habitId = habit.id,
                            title = habit.title.ifBlank { "Habit" },
                            windowStartMinute = habit.windowStartMinute,
                            windowEndMinute = habit.windowEndMinute
                        )
                    }
                val proposal = gapFillPlanner.propose(
                    blocks = blocks,
                    tasks = tasks,
                    habitCandidates = habitCandidates,
                    sleepSchedule = sleepSchedule,
                    nowMinuteOfDay = nowMinuteOfDay,
                    addBreaksAutomatically = addBreaksAutomatically,
                    readiness = readiness
                )
                if (proposal.proposedBlocks.isEmpty()) {
                    _suggestedBlocks.value = emptyList()
                    onResult(
                        PlannerOperationResult.Rejected(gapFillEmptyMessage(proposal.gapCount), ""),
                        false
                    )
                } else {
                    val summary = gapFillSummaryMessage(proposal) +
                        if (readiness == SleepReadiness.DEPLETED) {
                            " · Lighter plan — last night's sleep ran short"
                        } else {
                            ""
                        }
                    _aiPlanResult.value = summary
                    _suggestedBlocks.value = proposal.proposedBlocks.map { it.toUiSuggestion() }
                    onResult(
                        PlannerOperationResult.Applied(
                            message = summary,
                            blockId = "",
                            snappedToMinute = null,
                            affectedBlockIds = emptyList()
                        ),
                        false
                    )
                }
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

    fun repairConflictingPlan(
        scope: CoroutineScope,
        date: LocalDate,
        conflictDescription: String
    ) {
        scope.launch {
            _repairPlanResult.value = null
            val blocks = repository.getTimeBlocksByDate(date).first()
            val planSummary = blocks
                .sortedBy { it.startMinuteOfDay }
                .joinToString("\n") { block ->
                    buildString {
                        append("- ${block.title} ")
                        append(formatDisplayMinute(block.startMinuteOfDay))
                        append("-")
                        append(formatDisplayMinute((block.startMinuteOfDay + block.durationMinutes) % 1440))
                        if (block.isLocked) append(" [locked]")
                        if (block.isProtected) append(" [protected]")
                    }
                }
            _repairPlanResult.value = runCatching {
                aiPlanner.repairDayPlan(
                    currentPlan = planSummary,
                    conflictDescription = conflictDescription,
                    privacyMode = _privacyMode.value
                )
            }.getOrElse { "Plan repair is unavailable right now. Try again in a moment." }
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

    private fun GapFillBlock.toUiSuggestion(): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = taskId ?: habitId ?: "gapfill_${category}_${startMinute}_${durationMinutes}",
            title = title,
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            color = ChronosColors.AssistSuggestion,
            provenance = BlockProvenance.AI_SUGGESTED.name,
            flexibility = BlockFlexibility.MOVABLE.name,
            category = category,
            taskId = taskId,
            habitId = habitId
        )
    }

    private fun ProposedSuggestionBlock.toUiSuggestion(): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = id,
            title = title,
            startMinuteOfDay = startMinuteOfDay,
            durationMinutes = durationMinutes,
            color = ChronosColors.AssistSuggestion,
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
            // Gap-fill suggestions carry real categories (TASK/HABIT/RECOVERY);
            // generated plan suggestions fall back to the legacy AI tag.
            category = category.ifBlank { "AI" },
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

    /**
     * Folds a recovery-oriented instruction into the AI plan preferences after a depleted night, so
     * the generated plan itself leans lighter. Only DEPLETED adds a hint; other readiness states (and
     * users who do not track sleep) leave the preferences untouched.
     */
    private fun String.withSleepReadiness(readiness: SleepReadiness): String {
        if (readiness != SleepReadiness.DEPLETED) return this
        val hint = "Last night's sleep ran short, so keep today lighter: fewer back-to-back focus " +
            "blocks, more recovery breaks, and place demanding work later rather than first thing."
        return if (isBlank()) hint else "$this $hint"
    }

    /** Readiness from last night, but only for today — last night does not bear on other dates. */
    private suspend fun readinessFor(date: LocalDate): SleepReadiness =
        if (date == LocalDate.now()) {
            deriveSleepReadiness(
                sleepTrackRepository.getForDateRange(date.minusDays(1), date).maxByOrNull { it.date }
            )
        } else {
            SleepReadiness.UNKNOWN
        }

    private fun initialPrivacyMode(): PrivacyMode {
        return runCatching {
            PrivacyMode.valueOf(assistantPreferences.assistantPrivacyModeValue())
        }.getOrDefault(PrivacyMode.ON_DEVICE_ONLY)
    }
}

internal fun gapFillSummaryMessage(proposal: GapFillProposal): String = buildString {
    append("${proposal.gapCount} gap${if (proposal.gapCount == 1) "" else "s"}")
    append(" · ${proposal.taskCount} task${if (proposal.taskCount == 1) "" else "s"} fit")
    if (proposal.habitCount > 0) {
        append(" · ${proposal.habitCount} habit${if (proposal.habitCount == 1) "" else "s"}")
    }
}

internal fun gapFillEmptyMessage(gapCount: Int): String = if (gapCount == 0) {
    "No gaps of 45 minutes or more to fill"
} else {
    "Nothing left to schedule: no pending tasks or due habits fit these gaps"
}
