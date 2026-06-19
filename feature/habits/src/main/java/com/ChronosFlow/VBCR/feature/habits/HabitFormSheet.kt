package com.ChronosFlow.VBCR.feature.habits

import com.ChronosFlow.VBCR.core.ui.components.ChronosFilledTonalButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ai.HabitAssistRequest
import com.ChronosFlow.VBCR.core.ai.HabitAssistSuggestion
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCopy
import com.ChronosFlow.VBCR.core.domain.model.AppLaunchTarget
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitRecurrencePeriodUnit
import com.ChronosFlow.VBCR.core.domain.model.HabitSchedule
import com.ChronosFlow.VBCR.core.domain.model.normalizeAppLaunchTarget
import com.ChronosFlow.VBCR.core.ui.components.ChronosAssistSuggestionChips
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilterChip
import com.ChronosFlow.VBCR.core.ui.components.GenAiAssistBanner
import com.ChronosFlow.VBCR.core.ui.components.withSelectedOption
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiBooleanSetting
import com.ChronosFlow.VBCR.core.ui.components.ChronosSpeechInputButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosFormBottomSheet
import com.ChronosFlow.VBCR.core.ui.components.ChronosModalActionLabels
import com.ChronosFlow.VBCR.core.ui.components.ChronosFormPreviewCard
import com.ChronosFlow.VBCR.core.ui.components.commandPaletteSpeechQuery
import com.ChronosFlow.VBCR.core.ui.components.ChronosCollapsibleSection
import com.ChronosFlow.VBCR.core.ui.components.ChronosLinkOption
import com.ChronosFlow.VBCR.core.ui.components.ChronosLinkPickerField
import com.ChronosFlow.VBCR.core.ui.components.ChronosFormSection
import com.ChronosFlow.VBCR.core.ui.components.formatDurationLabel
import com.ChronosFlow.VBCR.core.ui.components.ChronosFormSwitchRow
import com.ChronosFlow.VBCR.core.ui.components.ChronosLauncherAppPicker
import com.ChronosFlow.VBCR.core.ui.components.ChronosOptionChips
import com.ChronosFlow.VBCR.core.ui.components.ChronosTimeWindowControls
import com.ChronosFlow.VBCR.core.ui.components.applyDurationToWindow
import com.ChronosFlow.VBCR.core.ui.components.formatDisplayMinute
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.nudgeMinuteText
import com.ChronosFlow.VBCR.core.ui.components.parseFlexibleMinute
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.delay

internal sealed class HabitSheetTarget {
    data class Add(val prefillTitle: String? = null) : HabitSheetTarget()
    data class Edit(val habit: Habit) : HabitSheetTarget()
}

private val habitTitleSuggestions = listOf(
    "Exercise",
    "Meditation",
    "Read",
    "Stretch",
    "Journal",
    "Hydrate",
    "Brush and floss",
    "Study",
    "Sleep routine",
    "Meal prep"
)
private val habitRecurrenceQuickPresets = listOf(
    "Daily",
    "Weekdays",
    "Weekends",
    "Mon/Wed/Fri",
    "Tue/Thu",
    "Weekly",
    "Every 2 days",
    "2x / week",
    "3x / week",
    "4x / week",
    "5x / week",
    "6x / week",
    "1x / month",
    "2x / month",
    "5x / month",
    "Custom"
)
private val habitWeekdayShortcuts = mapOf(
    "Mon/Wed/Fri" to setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    "Tue/Thu" to setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
    "Weekdays" to setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    "Weekends" to setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
)
private val habitQuotaPresetLabels = listOf(
    "1x / week",
    "2x / week",
    "3x / week",
    "4x / week",
    "5x / week",
    "6x / week",
    "1x / month",
    "2x / month",
    "5x / month"
)
private val habitWindowPresets = mapOf(
    "Morning" to (6 * 60 to 10 * 60),
    "Midday" to (11 * 60 to 14 * 60),
    "Afternoon" to (14 * 60 to 18 * 60),
    "Evening" to (18 * 60 to 22 * 60),
    "All day" to (8 * 60 to 22 * 60),
    "Custom" to null
)
private val habitTemplates = listOf(
    HabitTemplate("Morning reset", "Morning walk", "Daily", 6 * 60, 9 * 60, 2, true),
    HabitTemplate("Workout", "Workout", "Weekdays", 6 * 60, 8 * 60, 4, true),
    HabitTemplate("Reading", "Read 20 min", "Daily", 19 * 60, 22 * 60, 2, false),
    HabitTemplate("Hydration", "Hydrate", "Daily", 8 * 60, 20 * 60, 1, true),
    HabitTemplate("Dental", "Brush and floss", "Daily", 20 * 60, 22 * 60, 1, true),
    HabitTemplate("Study", "Study 25 min", "Weekdays", 18 * 60, 21 * 60, 3, true),
    HabitTemplate("Sleep routine", "Wind down for sleep", "Daily", 20 * 60, 22 * 60, 2, true),
    HabitTemplate("Nutrition", "Prep healthy meal", "Daily", 11 * 60, 14 * 60, 2, false),
    HabitTemplate("Wind down", "Journal", "Daily", 20 * 60, 22 * 60, 2, false)
)
private val habitDifficultyOptions = (1..5).associateWith(::difficultyLabel)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HabitFormSheet(
    target: HabitSheetTarget?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, Int, Boolean, HabitSchedule, AppLaunchTarget?, String?) -> Unit,
    goalOptions: List<ChronosLinkOption> = emptyList(),
    initialGoalId: String? = null,
    historyTemplates: List<HabitHistoryTemplate> = emptyList(),
    recentHistoryIds: List<String> = emptyList(),
    onHistoryTemplateSelected: (String) -> Unit = {},
    onCompleteToday: ((Habit) -> Unit)? = null,
    onArchive: ((Habit) -> Unit)? = null,
    onDuplicate: ((Habit) -> Unit)? = null,
    assistState: HabitAssistUiState = HabitAssistUiState(),
    onRequestAssist: ((HabitAssistRequest) -> Unit)? = null,
    onClearAssist: (() -> Unit)? = null
) {
    if (target == null) return
    val initialHabit = (target as? HabitSheetTarget.Edit)?.habit
    val prefillTitle = (target as? HabitSheetTarget.Add)?.prefillTitle
    val prefillDraft = remember(prefillTitle) {
        habitTranscriptDraft(prefillTitle.orEmpty())
    }
    val prefillLaunchSuggestion = remember(prefillTitle) {
        habitLaunchCaptureSuggestion(prefillTitle.orEmpty())
    }
    val habitKey = initialHabit?.id ?: "new-${prefillTitle.orEmpty()}"

    var habitTitle by rememberSaveable(habitKey) {
        mutableStateOf(initialHabit?.title ?: prefillDraft.title ?: prefillTitle.orEmpty())
    }
    var nameEverFilled by rememberSaveable(habitKey) {
        mutableStateOf((initialHabit?.title ?: prefillDraft.title ?: prefillTitle.orEmpty()).isNotBlank())
    }
    var habitCaptureContext by rememberSaveable(habitKey) {
        mutableStateOf(prefillTitle.orEmpty())
    }
    val initialRecurrenceState = buildHabitRecurrenceEditorState(
        cadence = initialHabit?.cadence ?: prefillDraft.cadence ?: "Daily",
        schedule = initialHabit?.schedule
    )
    var recurrenceState by remember(habitKey) { mutableStateOf(initialRecurrenceState) }
    var recurrenceCustomExpanded by rememberSaveable(habitKey) {
        mutableStateOf(resolveHabitRecurrenceQuickPreset(initialRecurrenceState) == "Custom")
    }
    // Blank/zero = no explicit choice yet. A fresh Add form's window then follows the
    // capture context ("morning run" → Morning window) instead of the all-day default;
    // Edit forms and prefills that carry a window always pin to that window.
    val windowFollowsContext = initialHabit == null &&
        prefillDraft.windowPreset == null &&
        prefillDraft.startMinute == null &&
        prefillDraft.endMinute == null
    var windowPresetOverride by rememberSaveable(habitKey) {
        mutableStateOf(
            if (windowFollowsContext) {
                ""
            } else {
                initialHabit?.let { resolveHabitWindowPreset(it.windowStartMinute, it.windowEndMinute) } ?: "All day"
                    .let { prefillDraft.windowPreset ?: it }
            }
        )
    }
    var startOverride by rememberSaveable(habitKey) {
        mutableStateOf(
            if (windowFollowsContext) {
                ""
            } else {
                formatDisplayMinute(initialHabit?.windowStartMinute ?: prefillDraft.startMinute ?: 8 * 60)
            }
        )
    }
    var endOverride by rememberSaveable(habitKey) {
        mutableStateOf(
            if (windowFollowsContext) {
                ""
            } else {
                formatDisplayMinute(initialHabit?.windowEndMinute ?: prefillDraft.endMinute ?: 20 * 60)
            }
        )
    }
    var windowDurationOverride by rememberSaveable(habitKey) {
        mutableIntStateOf(
            if (windowFollowsContext) {
                0
            } else {
                (
                    (initialHabit?.windowEndMinute ?: prefillDraft.endMinute ?: 20 * 60) -
                        (initialHabit?.windowStartMinute ?: prefillDraft.startMinute ?: 8 * 60)
                    )
                    .coerceIn(15, 240)
            }
        )
    }
    val contextualWindowDefault = if (windowFollowsContext) {
        contextualHabitDefaultWindow(
            title = listOf(habitTitle, habitCaptureContext).joinToString(" "),
            suggestions = assistState.suggestions
        )
    } else {
        null
    }
    val windowPreset = windowPresetOverride.ifBlank {
        contextualWindowDefault
            ?.let { resolveHabitWindowPreset(it.first, it.second) }
            ?: "All day"
    }
    val start = startOverride.ifBlank {
        formatDisplayMinute(contextualWindowDefault?.first ?: 8 * 60)
    }
    val end = endOverride.ifBlank {
        formatDisplayMinute(contextualWindowDefault?.second ?: 20 * 60)
    }
    val windowDuration = if (windowDurationOverride > 0) {
        windowDurationOverride
    } else {
        (contextualWindowDefault?.let { (windowStart, windowEnd) -> windowEnd - windowStart } ?: (12 * 60))
            .coerceIn(15, 240)
    }
    var difficulty by rememberSaveable(habitKey) {
        mutableStateOf((initialHabit?.difficulty ?: prefillDraft.difficulty ?: 2).toFloat())
    }
    var isBundled by rememberSaveable(habitKey) {
        mutableStateOf(initialHabit?.isBundled ?: prefillDraft.isBundled ?: false)
    }
    var historyQuery by rememberSaveable(habitKey) { mutableStateOf("") }
    var templatesExpanded by rememberSaveable(habitKey) { mutableStateOf(false) }
    var historyExpanded by rememberSaveable(habitKey) { mutableStateOf(false) }
    var scheduleExpanded by rememberSaveable(habitKey) {
        mutableStateOf(
            resolveHabitRecurrenceQuickPreset(initialRecurrenceState) == "Custom" ||
                prefillDraft.cadence != null
        )
    }
    var windowExpanded by rememberSaveable(habitKey) {
        mutableStateOf(
            initialHabit?.let { resolveHabitWindowPreset(it.windowStartMinute, it.windowEndMinute) } == "Custom" ||
                prefillDraft.windowPreset != null
        )
    }
    var effortExpanded by rememberSaveable(habitKey) {
        mutableStateOf(
            (initialHabit?.difficulty ?: prefillDraft.difficulty ?: 2) != 2 ||
                initialHabit?.isBundled == true ||
                prefillDraft.isBundled == true
        )
    }
    var launchAppExpanded by rememberSaveable(habitKey) {
        mutableStateOf(initialHabit?.launchTarget != null || prefillLaunchSuggestion != null)
    }
    var launchAppEnabled by rememberSaveable(habitKey) {
        mutableStateOf(initialHabit?.launchTarget != null || prefillLaunchSuggestion != null)
    }
    var launchAppLabel by rememberSaveable(habitKey) {
        mutableStateOf(initialHabit?.launchTarget?.label ?: prefillLaunchSuggestion?.label.orEmpty())
    }
    var launchAppValue by rememberSaveable(habitKey) {
        mutableStateOf(initialHabit?.launchTarget?.value ?: prefillLaunchSuggestion?.value.orEmpty())
    }
    var lastAutoAssistCapture by rememberSaveable(habitKey) { mutableStateOf("") }
    var selectedGoalId by rememberSaveable(habitKey) {
        mutableStateOf(initialHabit?.goalId ?: initialGoalId)
    }
    var goalExpanded by rememberSaveable(habitKey) {
        mutableStateOf((initialHabit?.goalId ?: initialGoalId) != null)
    }

    val parsedStart = parseFlexibleMinute(start)
    val parsedEnd = parseFlexibleMinute(end)
    val difficultyInt = difficulty.toInt().coerceIn(1, 5)
    val cadence = recurrenceState.cadenceLabel()
    val recurrenceSummary = recurrenceState.summary()
    val selectedRecurrencePreset = resolveHabitRecurrenceQuickPreset(recurrenceState)
    val selectedCustomPattern = recurrenceState.customPattern()
    val customRecurrenceVisible = recurrenceCustomExpanded
    val endAfterStart = parsedStart != null && parsedEnd != null && parsedEnd > parsedStart
    val normalizedLaunchTarget = if (launchAppEnabled) {
        normalizeAppLaunchTarget(launchAppLabel, launchAppValue)
    } else {
        null
    }
    val appliedSuggestionIds = remember(habitKey, assistState.suggestions) {
        mutableStateListOf<String>()
    }
    val redundantAssistSuggestionIds = redundantHabitAssistSuggestionIds(
        suggestions = assistState.suggestions,
        currentTitle = habitTitle,
        currentRecurrencePreset = selectedRecurrencePreset,
        currentStartMinute = parsedStart,
        currentEndMinute = parsedEnd,
        currentDifficulty = difficultyInt,
        currentIsBundled = isBundled
    )
    val visibleAssistSuggestions = assistState.suggestions.filterNot {
        it.id in appliedSuggestionIds || it.id in redundantAssistSuggestionIds
    }
    val contextualAssistSuggestions = assistState.suggestions
    val habitContextQuery = listOf(habitTitle, habitCaptureContext)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")

    val adaptiveHabitHints = habitModalAdaptiveHints(
        title = habitContextQuery,
        suggestions = contextualAssistSuggestions
    )
    val showHabitLaunchDetails = launchAppExpanded || adaptiveHabitHints.showLaunch
    val showHabitRecurrenceDetails = scheduleExpanded || adaptiveHabitHints.showRecurrence
    val showHabitWindowDetails = windowExpanded || adaptiveHabitHints.showWindow
    val showHabitEffortDetails = effortExpanded || adaptiveHabitHints.showEffort
    val autoAssistCapture = habitContextQuery.trim()
    LaunchedEffect(autoAssistCapture, assistState.isLoading, onRequestAssist) {
        val requestAssist = onRequestAssist ?: return@LaunchedEffect
        if (assistState.isLoading) return@LaunchedEffect
        if (!shouldAutoRequestHabitAssistForCapture(autoAssistCapture) || autoAssistCapture == lastAutoAssistCapture) {
            return@LaunchedEffect
        }
        delay(AUTO_ASSIST_DEBOUNCE_MS)
        val currentAssistCapture = listOf(habitTitle, habitCaptureContext)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        if (currentAssistCapture != autoAssistCapture) return@LaunchedEffect
        lastAutoAssistCapture = autoAssistCapture
        requestAssist(
            HabitAssistRequest(
                title = autoAssistCapture,
                cadence = cadence,
                startMinute = parsedStart ?: 8 * 60,
                endMinute = parsedEnd ?: 20 * 60,
                difficulty = difficultyInt,
                isBundled = isBundled
            )
        )
    }
    LaunchedEffect(habitTitle) {
        if (!launchAppEnabled && launchAppValue.isBlank()) {
            habitLaunchCaptureSuggestion(habitTitle)?.let { suggestion ->
                launchAppEnabled = true
                launchAppExpanded = true
                launchAppLabel = suggestion.label
                launchAppValue = suggestion.value
            }
        }
    }

    val launchTargetInvalid = launchAppEnabled && normalizedLaunchTarget == null
    val isValid = habitTitle.isNotBlank() && endAfterStart && !launchTargetInvalid
    val nowMinute = LocalTime.now().hour * 60 + LocalTime.now().minute
    val validationHint = when {
        habitTitle.isBlank() -> "Enter a habit name to continue."
        parsedStart == null || parsedEnd == null -> "Use a valid time like 9:00 AM or 09:00."
        !endAfterStart -> "End time must be after the start time."
        launchTargetInvalid -> "Enter an app package name or app deep link."
        else -> null
    }

    val contextualHabitTitleOptions = contextualHabitTitleSuggestions(
        habitTitle,
        visibleAssistSuggestions
    )
    val dynamicHabitTitle = habitTitle.ifBlank { initialHabit?.title.orEmpty() }
    val editHabitSubtitle = habitEditModalDynamicSubtitle(
        habitTitle = dynamicHabitTitle,
        recurrenceSummary = recurrenceSummary,
        difficultyLabel = difficultyLabel(difficultyInt),
        isBundled = isBundled,
        startMinute = parsedStart,
        endMinute = parsedEnd,
        launchLabel = normalizedLaunchTarget?.label
    )
    val editHabitTitle = habitEditModalDynamicTitle(dynamicHabitTitle)
    val title = habitModalDynamicTitle(
        isAddMode = target is HabitSheetTarget.Add,
        habitTitle = habitTitle,
        hints = adaptiveHabitHints,
        suggestions = contextualAssistSuggestions,
        editTitle = editHabitTitle
    )
    val subtitle = habitModalDynamicSubtitle(
        isAddMode = target is HabitSheetTarget.Add,
        habitTitle = habitTitle,
        hints = adaptiveHabitHints,
        cadence = cadence,
        start = start,
        end = end,
        suggestions = contextualAssistSuggestions,
        editSubtitle = editHabitSubtitle
    )
    val actionLabels = habitModalActionLabels(target is HabitSheetTarget.Add)

    fun applyHabitAssistSuggestion(suggestion: HabitAssistSuggestion) {
        when (suggestion) {
            is HabitAssistSuggestion.Title -> {
                habitTitle = suggestion.title
            }
            is HabitAssistSuggestion.Recurrence -> {
                recurrenceState = buildHabitRecurrenceEditorState(suggestion.cadence, null)
                recurrenceCustomExpanded = resolveHabitRecurrenceQuickPreset(recurrenceState) == "Custom"
                scheduleExpanded = true
            }
            is HabitAssistSuggestion.Window -> {
                startOverride = formatDisplayMinute(suggestion.startMinute)
                endOverride = formatDisplayMinute(suggestion.endMinute)
                windowDurationOverride = (suggestion.endMinute - suggestion.startMinute).coerceIn(15, 240)
                windowPresetOverride = resolveHabitWindowPreset(suggestion.startMinute, suggestion.endMinute)
                windowExpanded = true
            }
            is HabitAssistSuggestion.Difficulty -> {
                difficulty = suggestion.difficulty.toFloat()
                effortExpanded = true
            }
            is HabitAssistSuggestion.DayPlan -> {
                isBundled = suggestion.isBundled
                effortExpanded = true
            }
        }
        supersededHabitAssistSuggestionIds(suggestion, assistState.suggestions).forEach { id ->
            if (id !in appliedSuggestionIds) {
                appliedSuggestionIds.add(id)
            }
        }
        if (assistState.suggestions.all { it.id in appliedSuggestionIds }) {
            onClearAssist?.invoke()
        }
    }

    fun applyAllAssistSuggestions() {
        assistState.suggestions.forEach { suggestion ->
            if (suggestion.id !in appliedSuggestionIds) {
                applyHabitAssistSuggestion(suggestion)
            }
        }
    }

    val openingRecurrencePreset = remember(habitKey) { selectedRecurrencePreset }
    val openingDifficulty = remember(habitKey) { difficultyInt }
    val openingIsBundled = remember(habitKey) { isBundled }
    val autoApplyAssistEnabled = rememberChronosUiBooleanSetting(
        ChronosUiSettingsKeys.KEY_ASSIST_AUTO_APPLY,
        false
    )
    LaunchedEffect(assistState.suggestions, autoApplyAssistEnabled) {
        if (!autoApplyAssistEnabled || target !is HabitSheetTarget.Add) return@LaunchedEffect
        val autoApplicableIds = autoApplicableHabitAssistSuggestionIds(
            suggestions = visibleAssistSuggestions,
            titleBlank = habitTitle.isBlank(),
            recurrenceUntouched = selectedRecurrencePreset == openingRecurrencePreset,
            windowUntouched = startOverride.isBlank() && endOverride.isBlank(),
            difficultyUntouched = difficultyInt == openingDifficulty,
            dayPlanUntouched = isBundled == openingIsBundled
        )
        assistState.suggestions
            .filter { it.id in autoApplicableIds }
            .forEach(::applyHabitAssistSuggestion)
    }

    ChronosFormBottomSheet(
        visible = true,
        title = title,
        subtitle = subtitle,
        confirmLabel = actionLabels.confirm,
        validationHint = validationHint,
        onDismiss = onDismiss,
        onConfirm = {
            val schedule = recurrenceState.toHabitSchedule(
                existingSchedule = initialHabit?.schedule,
                habitId = initialHabit?.id.orEmpty(),
                targetStartMinute = parsedStart ?: 8 * 60,
                targetEndMinute = parsedEnd ?: 20 * 60,
                plannerVisible = isBundled
            )
            onConfirm(
                habitTitle,
                cadence,
                parsedStart ?: 8 * 60,
                parsedEnd ?: 20 * 60,
                difficultyInt,
                isBundled,
                schedule,
                normalizedLaunchTarget,
                selectedGoalId
            )
        },
        enabled = isValid,
        onArchive = if (initialHabit != null && onArchive != null) {
            { onArchive(initialHabit); onDismiss() }
        } else {
            null
        },
        onDuplicate = if (initialHabit != null && onDuplicate != null) {
            { onDuplicate(initialHabit); onDismiss() }
        } else {
            null
        },
        duplicateLabel = actionLabels.duplicate,
        archiveLabel = actionLabels.archive
    ) {
        val draftHabit = Habit(
            id = initialHabit?.id ?: "draft",
            title = habitTitle.ifBlank { "Untitled habit" },
            cadence = cadence,
            windowStartMinute = parsedStart ?: 8 * 60,
            windowEndMinute = parsedEnd ?: 20 * 60,
            difficulty = difficultyInt,
            isBundled = isBundled,
            streakCount = initialHabit?.streakCount ?: 0,
            lastCompletedDate = initialHabit?.lastCompletedDate,
            isActive = true,
            launchTarget = normalizedLaunchTarget
        )
        val selectedTemplateLabel = habitTemplates.firstOrNull { template ->
            template.title.equals(habitTitle, ignoreCase = true) &&
                template.cadence == cadence &&
                template.startMinute == (parsedStart ?: 8 * 60) &&
                template.endMinute == (parsedEnd ?: 20 * 60)
        }?.label.orEmpty()
        val windowLabel = if (parsedStart != null && parsedEnd != null) {
            "${formatDisplayMinute(parsedStart)} – ${formatDisplayMinute(parsedEnd)} (${formatDurationLabel(parsedEnd - parsedStart)})"
        } else {
            null
        }
        ChronosFormPreviewCard(
            title = habitTitle,
            subtitle = buildString {
                append("$recurrenceSummary · ${difficultyLabel(difficultyInt)}")
                append(if (isBundled) " · On day plan" else " · Flexible")
                normalizedLaunchTarget?.let { target -> append(" · Opens ${target.label}") }
            },
            detail = windowLabel
        )
        ChronosFormSection(
            title = "Essentials",
            subtitle = "Name the habit first; the rest stays contextual."
        ) {
            OutlinedTextField(
                value = habitTitle,
                onValueChange = {
                    habitTitle = it
                    if (it.isNotBlank()) nameEverFilled = true
                },
                label = { Text("Habit name") },
                placeholder = { Text("e.g. Morning walk") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = nameEverFilled && habitTitle.isBlank(),
                supportingText = if (habitTitle.isBlank()) {
                    { Text("Required") }
                } else {
                    null
                }
            )
            ChronosOptionChips(
                label = "Context titles",
                options = contextualHabitTitleOptions,
                selected = habitTitle,
                onSelected = { habitTitle = it }
            )
            ChronosSpeechInputButton(
                prompt = "Describe the habit, including cadence and completion window.",
                label = "Dictate habit",
                onTranscript = { transcript ->
                    val capture = commandPaletteSpeechQuery(transcript)
                    if (capture.isBlank()) return@ChronosSpeechInputButton
                    habitCaptureContext = capture
                    val transcriptDraft = habitTranscriptDraft(capture)
                    val launchSuggestion = habitLaunchCaptureSuggestion(capture)
                    val requestTitle = mergeHabitTranscriptTitle(habitTitle, capture)
                    habitTitle = mergeHabitTranscriptTitle(
                        title = habitTitle,
                        transcript = transcriptDraft.title ?: capture
                    )
                    transcriptDraft.cadence?.let { cadenceLabel ->
                        recurrenceState = buildHabitRecurrenceEditorState(
                            cadence = cadenceLabel,
                            schedule = null
                        )
                        recurrenceCustomExpanded = resolveHabitRecurrenceQuickPreset(recurrenceState) == "Custom"
                        scheduleExpanded = true
                    }
                    if (
                        transcriptDraft.windowPreset != null &&
                            transcriptDraft.startMinute != null &&
                            transcriptDraft.endMinute != null
                    ) {
                        windowPresetOverride = transcriptDraft.windowPreset
                        startOverride = formatDisplayMinute(transcriptDraft.startMinute)
                        endOverride = formatDisplayMinute(transcriptDraft.endMinute)
                        windowDurationOverride = (transcriptDraft.endMinute - transcriptDraft.startMinute)
                            .coerceIn(15, 240)
                        windowExpanded = true
                    }
                    transcriptDraft.difficulty?.let { detectedDifficulty ->
                        difficulty = detectedDifficulty.toFloat()
                        effortExpanded = true
                    }
                    transcriptDraft.isBundled?.let { detectedBundled ->
                        isBundled = detectedBundled
                        effortExpanded = true
                    }
                    launchSuggestion?.let { suggestion ->
                        launchAppEnabled = true
                        launchAppExpanded = true
                        launchAppLabel = suggestion.label
                        launchAppValue = suggestion.value
                    }
                    onClearAssist?.invoke()
                    lastAutoAssistCapture = listOf(requestTitle, capture)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" ")
                    onRequestAssist?.invoke(
                        HabitAssistRequest(
                            title = listOf(requestTitle, capture)
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString(" "),
                            cadence = transcriptDraft.cadence ?: cadence,
                            startMinute = transcriptDraft.startMinute ?: parsedStart ?: 8 * 60,
                            endMinute = transcriptDraft.endMinute ?: parsedEnd ?: 20 * 60,
                            difficulty = transcriptDraft.difficulty ?: difficultyInt,
                            isBundled = transcriptDraft.isBundled ?: isBundled
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (onRequestAssist != null) {
                ChronosFilledTonalButton(
                    onClick = {
                        onRequestAssist(
                            HabitAssistRequest(
                                title = habitContextQuery,
                                cadence = cadence,
                                startMinute = parsedStart ?: 8 * 60,
                                endMinute = parsedEnd ?: 20 * 60,
                                difficulty = difficultyInt,
                                isBundled = isBundled
                            )
                        )
                    },
                    enabled = !assistState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            assistState.isLoading -> "Drafting suggestions…"
                            visibleAssistSuggestions.isNotEmpty() -> "Refresh suggestions"
                            else -> "Suggest habit setup with AI"
                        }
                    )
                }
            }
            assistState.assistSnapshot?.let { snapshot ->
                GenAiAssistBanner(
                    title = snapshot.bannerTitle,
                    message = snapshot.bannerMessage +
                        " Type or dictate the habit and AI drafts editable suggestions — name, cadence, time window, effort, and starter plan. Nothing changes until you tap a suggestion.",
                    ready = snapshot.isReady
                )
            }
            assistState.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (visibleAssistSuggestions.isNotEmpty() || assistState.isLoading) {
                ChronosAssistSuggestionChips(
                    suggestions = visibleAssistSuggestions,
                    isLoading = assistState.isLoading,
                    onApply = ::applyHabitAssistSuggestion,
                    label = { it.label },
                    reason = { it.reason },
                    sourceLabel = { GenAiAssistCopy.routineAssistSourceLabel(it.source) },
                    loadingLabel = "Drafting AI suggestions…",
                    onApplyAll = ::applyAllAssistSuggestions
                )
            }
        }
        val contextualHabitTemplateLabels = contextualHabitTemplateOptions(
            title = habitContextQuery,
            suggestions = contextualAssistSuggestions,
            templates = habitTemplates
        )
        val suggestedHabitTemplateLabel = contextualHabitTemplateLabels
            .firstOrNull()
            ?.takeIf { hasHabitTemplateContext(habitContextQuery, contextualAssistSuggestions) }
        if (goalOptions.isNotEmpty() || selectedGoalId != null) {
            ChronosCollapsibleSection(
                title = "Goal",
                summary = goalOptions.firstOrNull { it.id == selectedGoalId }?.label
                    ?: "Link this habit to a goal",
                expanded = goalExpanded,
                onExpandedChange = { goalExpanded = it }
            ) {
                ChronosLinkPickerField(
                    label = "Link to goal",
                    options = goalOptions,
                    selectedId = selectedGoalId,
                    onSelected = { selectedGoalId = it }
                )
            }
        }
        ChronosCollapsibleSection(
            title = "Templates",
            summary = selectedTemplateLabel.ifBlank {
                suggestedHabitTemplateLabel?.let { "Suggested: $it" } ?: "No template applied"
            },
            expanded = templatesExpanded || selectedTemplateLabel.isNotBlank(),
            onExpandedChange = { templatesExpanded = it }
        ) {
            ChronosOptionChips(
                label = "Templates",
                options = contextualHabitTemplateLabels,
                selected = selectedTemplateLabel,
                onSelected = { label ->
                    habitTemplates.firstOrNull { it.label == label }?.let { template ->
                        habitTitle = template.title
                        recurrenceState = buildHabitRecurrenceEditorState(template.cadence, null)
                        recurrenceCustomExpanded = false
                        startOverride = formatDisplayMinute(template.startMinute)
                        endOverride = formatDisplayMinute(template.endMinute)
                        windowDurationOverride = (template.endMinute - template.startMinute).coerceIn(15, 240)
                        difficulty = template.difficulty.toFloat()
                        isBundled = template.isBundled
                        windowPresetOverride = resolveHabitWindowPreset(template.startMinute, template.endMinute)
                    }
                }
            )
        }
        if (historyTemplates.isNotEmpty()) {
            val filteredHistoryTemplates = prioritizeContextualHabitHistoryTemplates(
                templates = filterHabitHistoryTemplates(historyTemplates, historyQuery),
                recentIds = recentHistoryIds,
                title = habitContextQuery,
                suggestions = contextualAssistSuggestions
            )
            val suggestedHistoryTemplateLabel = filteredHistoryTemplates
                .firstOrNull()
                ?.displayLabel
                ?.takeIf {
                    historyQuery.isBlank() &&
                        hasContextualHabitHistoryMatch(
                            templates = filteredHistoryTemplates,
                            title = habitContextQuery,
                            suggestions = contextualAssistSuggestions
                        )
                }
            val archivedTemplates = filteredHistoryTemplates.filter(HabitHistoryTemplate::isArchived)
            val savedTemplates = filteredHistoryTemplates.filterNot(HabitHistoryTemplate::isArchived)
            ChronosCollapsibleSection(
                title = "From history",
                summary = suggestedHistoryTemplateLabel?.let { "Suggested: $it" }
                    ?: "${historyTemplates.size} saved setups available",
                expanded = historyExpanded,
                onExpandedChange = { historyExpanded = it }
            ) {
                OutlinedTextField(
                    value = historyQuery,
                    onValueChange = { historyQuery = it },
                    label = { Text("Search history") },
                    placeholder = { Text("Filter by title, recurrence, or archived") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (filteredHistoryTemplates.isEmpty()) {
                    Text(
                        text = "No saved or archived habits match that search yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (archivedTemplates.isNotEmpty()) {
                    ChronosOptionChips(
                        label = "Archived templates",
                        options = archivedTemplates.map(HabitHistoryTemplate::id),
                        selected = "",
                        onSelected = { selectedId ->
                            archivedTemplates.firstOrNull { it.id == selectedId }?.let { template ->
                                onHistoryTemplateSelected(selectedId)
                                applyHabitHistoryTemplate(template) { history ->
                                    habitTitle = history.title
                                    recurrenceState = buildHabitRecurrenceEditorState(history.cadence, history.schedule)
                                    recurrenceCustomExpanded = resolveHabitRecurrenceQuickPreset(recurrenceState) == "Custom"
                                    startOverride = formatDisplayMinute(history.startMinute)
                                    endOverride = formatDisplayMinute(history.endMinute)
                                    difficulty = history.difficulty.toFloat()
                                    isBundled = history.isBundled
                                    windowDurationOverride = (history.endMinute - history.startMinute).coerceIn(15, 240)
                                    windowPresetOverride = resolveHabitWindowPreset(history.startMinute, history.endMinute)
                                }
                            }
                        },
                        optionLabel = { selectedId ->
                            archivedTemplates.first { it.id == selectedId }.displayLabel
                        }
                    )
                }
                if (savedTemplates.isNotEmpty()) {
                    ChronosOptionChips(
                        label = "Saved habits",
                        options = savedTemplates.map(HabitHistoryTemplate::id),
                        selected = "",
                        onSelected = { selectedId ->
                            savedTemplates.firstOrNull { it.id == selectedId }?.let { template ->
                                onHistoryTemplateSelected(selectedId)
                                applyHabitHistoryTemplate(template) { history ->
                                    habitTitle = history.title
                                    recurrenceState = buildHabitRecurrenceEditorState(history.cadence, history.schedule)
                                    recurrenceCustomExpanded = resolveHabitRecurrenceQuickPreset(recurrenceState) == "Custom"
                                    startOverride = formatDisplayMinute(history.startMinute)
                                    endOverride = formatDisplayMinute(history.endMinute)
                                    difficulty = history.difficulty.toFloat()
                                    isBundled = history.isBundled
                                    windowDurationOverride = (history.endMinute - history.startMinute).coerceIn(15, 240)
                                    windowPresetOverride = resolveHabitWindowPreset(history.startMinute, history.endMinute)
                                }
                            }
                        },
                        optionLabel = { selectedId ->
                            savedTemplates.first { it.id == selectedId }.displayLabel
                        }
                    )
                }
            }
        }
        ChronosFormSection(
            title = "Today snapshot",
            subtitle = "Keep the habit aligned with your real day, not just its saved defaults."
        ) {
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = draftHabit.statusLabel(nowMinute = nowMinute) ?: "Ready to build momentum",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildString {
                            append("Streak ${draftHabit.streakCount}")
                            append(" · ${habitDifficultyOptions.getValue(difficultyInt)}")
                            append(if (draftHabit.isBundled) " · Visible on Today" else " · Lives as a standalone habit")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    windowLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (initialHabit != null && onCompleteToday != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChronosFilledTonalButton(
                        onClick = {
                            onCompleteToday(initialHabit)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = habitFormCompleteTodayActionLabel(initialHabit.title)
                            }
                    ) {
                        Text("Complete today")
                    }
                }
            }
        }
        ChronosCollapsibleSection(
            title = "Launch app",
            summary = normalizedLaunchTarget?.let { "${it.label} · ${it.value}" }
                ?: "Reminders open ChronosFlow",
            expanded = showHabitLaunchDetails,
            onExpandedChange = { launchAppExpanded = it }
        ) {
            ChronosFormSwitchRow(
                title = "Open app from reminders",
                subtitle = "Use the linked package or deep link when this habit reminder fires.",
                checked = launchAppEnabled,
                onCheckedChange = { enabled ->
                    launchAppEnabled = enabled
                    if (enabled) launchAppExpanded = true
                }
            )
            if (launchAppEnabled) {
                ChronosLauncherAppPicker(
                    selectedLaunchValue = launchAppValue,
                    onAppSelected = { option ->
                        launchAppLabel = "Open ${option.label}"
                        launchAppValue = option.launchValue
                    }
                )
                OutlinedTextField(
                    value = launchAppLabel,
                    onValueChange = { launchAppLabel = it },
                    label = { Text("Button label") },
                    placeholder = { Text("Open Journal") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = launchAppValue,
                    onValueChange = { launchAppValue = it },
                    label = { Text("Package, launcher activity, or deep link") },
                    placeholder = { Text("com.example.journal or journal://new") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = launchTargetInvalid
                )
            }
        }
        ChronosCollapsibleSection(
            title = "Recurrence",
            summary = recurrenceSummary,
            expanded = showHabitRecurrenceDetails,
            onExpandedChange = { scheduleExpanded = it }
        ) {
            ChronosOptionChips(
                label = "Quick picks",
                options = contextualHabitRecurrenceOptions(
                    selectedPreset = selectedRecurrencePreset,
                    title = habitContextQuery,
                    suggestions = contextualAssistSuggestions
                ).withSelectedOption(selectedRecurrencePreset),
                selected = selectedRecurrencePreset,
                onSelected = { preset ->
                    if (preset == "Custom") {
                        recurrenceCustomExpanded = true
                    } else {
                        recurrenceState = recurrenceStateForQuickPreset(preset)
                        recurrenceCustomExpanded = false
                    }
                }
            )
            ChronosFilledTonalButton(
                onClick = { recurrenceCustomExpanded = !customRecurrenceVisible },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (customRecurrenceVisible) "Hide custom controls" else "Customize recurrence")
            }
            if (customRecurrenceVisible) {
                ChronosOptionChips(
                    label = "Custom pattern",
                    options = HabitRecurrenceCustomPattern.entries.map(HabitRecurrenceCustomPattern::label),
                    selected = selectedCustomPattern.label,
                    onSelected = { label ->
                        val pattern = HabitRecurrenceCustomPattern.entries.first { it.label == label }
                        recurrenceState = pattern.applyTo(recurrenceState)
                    }
                )
                when (selectedCustomPattern) {
                    HabitRecurrenceCustomPattern.SELECTED_WEEKDAYS -> {
                        val selectedShortcut = habitWeekdayShortcutLabel(recurrenceState.weekdays)
                        ChronosOptionChips(
                            label = "Shortcuts",
                            options = habitWeekdayShortcuts.keys.toList(),
                            selected = selectedShortcut.orEmpty(),
                            onSelected = { label ->
                                recurrenceState = recurrenceState.copy(
                                    kind = HabitRecurrenceEditorKind.SCHEDULED,
                                    scheduleMode = HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS,
                                    weekdays = habitWeekdayShortcuts.getValue(label)
                                )
                            }
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DayOfWeek.values().forEach { day ->
                                val selected = day in recurrenceState.weekdays
                                ChronosFilterChip(
                                    selected = selected,
                                    onClick = {
                                        recurrenceState = recurrenceState.copy(
                                            kind = HabitRecurrenceEditorKind.SCHEDULED,
                                            scheduleMode = HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS,
                                            weekdays = if (selected) {
                                                recurrenceState.weekdays - day
                                            } else {
                                                recurrenceState.weekdays + day
                                            }
                                        )
                                    },
                                    label = { Text(day.name.take(3)) }
                                )
                            }
                        }
                    }
                    HabitRecurrenceCustomPattern.EVERY_N_DAYS -> {
                        OutlinedTextField(
                            value = recurrenceState.interval.toString(),
                            onValueChange = { value ->
                                recurrenceState = recurrenceState.copy(
                                    kind = HabitRecurrenceEditorKind.SCHEDULED,
                                    scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_DAYS,
                                    interval = value.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                )
                            },
                            label = { Text("Repeat every N days") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    HabitRecurrenceCustomPattern.EVERY_N_WEEKS -> {
                        OutlinedTextField(
                            value = recurrenceState.interval.toString(),
                            onValueChange = { value ->
                                recurrenceState = recurrenceState.copy(
                                    kind = HabitRecurrenceEditorKind.SCHEDULED,
                                    scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_WEEKS,
                                    interval = value.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                )
                            },
                            label = { Text("Repeat every N weeks") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DayOfWeek.values().forEach { day ->
                                val selected = day in recurrenceState.weekdays
                                ChronosFilterChip(
                                    selected = selected,
                                    onClick = {
                                        recurrenceState = recurrenceState.copy(
                                            kind = HabitRecurrenceEditorKind.SCHEDULED,
                                            scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_WEEKS,
                                            weekdays = if (selected) {
                                                recurrenceState.weekdays - day
                                            } else {
                                                recurrenceState.weekdays + day
                                            }
                                        )
                                    },
                                    label = { Text(day.name.take(3)) }
                                )
                            }
                        }
                        Text(
                            text = "Leave weekdays empty to keep a simple weekly interval.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HabitRecurrenceCustomPattern.QUOTA -> {
                        ChronosOptionChips(
                            label = "Quota presets",
                            options = habitQuotaPresetLabels,
                            selected = quotaPresetLabel(recurrenceState).orEmpty(),
                            onSelected = { label ->
                                recurrenceState = recurrenceState.applyQuotaPreset(label)
                            }
                        )
                        OutlinedTextField(
                            value = recurrenceState.quotaCompletions.toString(),
                            onValueChange = { value ->
                                recurrenceState = recurrenceState.copy(
                                    kind = HabitRecurrenceEditorKind.QUOTA,
                                    quotaCompletions = value.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                )
                            },
                            label = { Text("Times to complete") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        ChronosOptionChips(
                            label = "Per",
                            options = HabitRecurrencePeriodUnit.entries.map(HabitRecurrencePeriodUnit::name),
                            selected = recurrenceState.quotaPeriodUnit.name,
                            onSelected = { raw ->
                                recurrenceState = recurrenceState.copy(
                                    kind = HabitRecurrenceEditorKind.QUOTA,
                                    quotaPeriodUnit = HabitRecurrencePeriodUnit.valueOf(raw)
                                )
                            },
                            optionLabel = { raw -> raw.lowercase().replaceFirstChar(Char::uppercase) }
                        )
                        OutlinedTextField(
                            value = recurrenceState.quotaInterval.toString(),
                            onValueChange = { value ->
                                recurrenceState = recurrenceState.copy(
                                    kind = HabitRecurrenceEditorKind.QUOTA,
                                    quotaInterval = value.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                )
                            },
                            label = { Text("Every N periods") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        }

        ChronosCollapsibleSection(
            title = "Completion window",
            summary = windowLabel ?: "Set a valid start and end time",
            expanded = showHabitWindowDetails,
            onExpandedChange = { windowExpanded = it }
        ) {
            ChronosOptionChips(
                label = "Preset",
                options = contextualHabitWindowOptions(
                    selectedPreset = windowPreset,
                    title = habitContextQuery,
                    suggestions = contextualAssistSuggestions
                ).withSelectedOption(windowPreset),
                selected = windowPreset,
                onSelected = { preset ->
                    windowPresetOverride = preset
                    habitWindowPresets[preset]?.let { (presetStart, presetEnd) ->
                        startOverride = formatDisplayMinute(presetStart)
                        endOverride = formatDisplayMinute(presetEnd)
                        windowDurationOverride = (presetEnd - presetStart).coerceIn(15, 240)
                    }
                }
            )
            ChronosTimeWindowControls(
                startText = start,
                endText = end,
                onStartChange = {
                    startOverride = it
                    windowPresetOverride = "Custom"
                },
                onEndChange = {
                    endOverride = it
                    windowPresetOverride = "Custom"
                },
                parsedStart = parsedStart,
                parsedEnd = parsedEnd,
                showFineTuneFields = windowPreset == "Custom",
                onNudgeStart = { delta ->
                    val nudgedStart = nudgeMinuteText(start, delta, parsedStart ?: 8 * 60)
                    startOverride = nudgedStart
                    windowPresetOverride = "Custom"
                    parseFlexibleMinute(nudgedStart)?.let { s ->
                        endOverride = formatDisplayMinute(applyDurationToWindow(s, windowDuration))
                    }
                },
                onNudgeEnd = { delta ->
                    endOverride = nudgeMinuteText(end, delta, parsedEnd ?: 20 * 60)
                    windowPresetOverride = "Custom"
                },
                durationMinutes = windowDuration,
                onDurationChange = { minutes ->
                    val clampedDuration = minutes.coerceIn(15, 240)
                    windowDurationOverride = clampedDuration
                    windowPresetOverride = "Custom"
                    parsedStart?.let { s ->
                        endOverride = formatDisplayMinute(applyDurationToWindow(s, clampedDuration))
                    }
                }
            )
        }

        ChronosCollapsibleSection(
            title = "Effort",
            summary = "${difficultyLabel(difficultyInt)} · " +
                if (isBundled) "Visible on Today" else "Flexible habit",
            expanded = showHabitEffortDetails,
            onExpandedChange = { effortExpanded = it }
        ) {
            ChronosOptionChips(
                label = "Quick effort",
                options = contextualHabitDifficultyOptions(habitContextQuery, contextualAssistSuggestions)
                    .withSelectedOption(habitDifficultyOptions.getValue(difficultyInt)),
                selected = habitDifficultyOptions.getValue(difficultyInt),
                onSelected = { label ->
                    difficulty = habitDifficultyOptions.entries.first { it.value == label }.key.toFloat()
                }
            )
            ChronosOptionChips(
                label = "Day plan",
                options = contextualHabitDayPlanOptions(
                    title = habitContextQuery,
                    isBundled = isBundled,
                    suggestions = contextualAssistSuggestions
                ).withSelectedOption(habitDayPlanOptionLabel(isBundled)),
                selected = habitDayPlanOptionLabel(isBundled),
                onSelected = { label ->
                    isBundled = habitDayPlanOptionValue(label)
                }
            )
            Text(
                text = "Difficulty · ${difficultyLabel(difficultyInt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = difficulty,
                onValueChange = { difficulty = it },
                valueRange = 1f..5f,
                steps = 3
            )
            ChronosFormSwitchRow(
                title = "Bundle with day plan",
                subtitle = "Shows this habit alongside scheduled blocks on Today.",
                checked = isBundled,
                onCheckedChange = { isBundled = it }
            )
        }
    }
}

private data class HabitModalAdaptiveHints(
    val showRecurrence: Boolean,
    val showWindow: Boolean,
    val showEffort: Boolean,
    val showLaunch: Boolean,
    val contextKind: HabitContextKind?
)

private enum class HabitContextKind {
    WORKOUT,
    HYDRATION,
    MINDFULNESS,
    READING,
    WALKING,
    SLEEP,
    DENTAL,
    LEARNING,
    NUTRITION
}

private fun habitRecurrenceSectionSubtitle(
    title: String,
    cadence: String,
    selectedPreset: String,
    suggestions: List<HabitAssistSuggestion>
): String {
    val context = habitTemplateContextText(title, suggestions)
    val suggestedCadence = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Recurrence)?.cadence?.trim()?.takeIf(String::isNotBlank)
    }
    return when {
        suggestedCadence != null ->
            "AI suggests $suggestedCadence. Confirm the rhythm before saving."
        context.hasWorkoutTemplateContext() &&
            context.containsAnyTemplateWord("3x", "three times", "3 times", "week", "weekly") ->
            "Workout cadence detected. 3x/week keeps this flexible without pretending it is daily."
        context.containsAnyTemplateWord("every day", "daily", "everyday") ->
            "Daily habit wording detected. Keep this rhythm only if it is realistic every day."
        context.containsAnyTemplateWord("weekday", "weekdays", "workday", "workdays") ->
            "Weekday habit wording detected. Use weekdays if weekends should stay free."
        context.containsAnyTemplateWord("weekend", "weekends") ->
            "Weekend habit wording detected. Use weekends if this belongs outside workdays."
        selectedPreset != "Custom" ->
            "$selectedPreset rhythm selected from your context. Expand custom controls only if needed."
        cadence.isNotBlank() ->
            "$cadence rhythm selected. Adjust custom controls only if the quick rhythm is not enough."
        else -> "Start with a quick rhythm, then expand custom controls only when you need more range."
    }
}

private fun habitWindowSectionSubtitle(
    title: String,
    start: String,
    end: String,
    selectedPreset: String,
    suggestions: List<HabitAssistSuggestion>
): String {
    val context = habitTemplateContextText(title, suggestions)
    val suggestedWindow = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Window)?.let { window ->
            "${formatDisplayMinute(window.startMinute)} to ${formatDisplayMinute(window.endMinute)}"
        }
    }
    return when {
        suggestedWindow != null ->
            "AI suggests $suggestedWindow. Adjust only if that window does not fit your day."
        context.hasWorkoutTemplateContext() || context.containsAnyTemplateWord("evening", "night", "pm") ->
            "Workout/evening context detected. Keep the window realistic enough to protect the session."
        context.containsAnyTemplateWord("morning", "wake", "walk", "stretch", "am") ->
            "Morning routine context detected. Use a window you can complete before the day gets busy."
        context.containsAnyTemplateWord("water", "hydrate", "hydration", "all day") ->
            "All-day habit context detected. Use a broad window instead of a narrow reminder."
        selectedPreset != "Custom" ->
            "$selectedPreset window selected from your context. Fine-tune only if needed."
        start.isNotBlank() && end.isNotBlank() ->
            "Complete this habit any time from $start to $end."
        else -> "You can complete the habit any time inside this range."
    }
}

private fun habitEffortSectionSubtitle(
    title: String,
    difficulty: Int,
    isBundled: Boolean,
    suggestions: List<HabitAssistSuggestion>
): String {
    val context = habitTemplateContextText(title, suggestions)
    val suggestedDifficulty = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Difficulty)?.difficulty?.coerceIn(1, 5)
    }
    return when {
        context.hasWorkoutTemplateContext() ->
            "Workout context detected. Use higher effort or Today visibility if this should be planned like a real session."
        context.containsAnyTemplateWord("water", "hydrate", "tiny", "simple") ->
            "Light habit context detected. Keep effort low so it stays easy to complete."
        context.hasMindfulnessTemplateContext() || context.containsAnyTemplateWord("walk", "stretch", "mobility") ->
            "Routine context detected. Tune effort and Today visibility to match how much planning support it needs."
        suggestedDifficulty != null ->
            "AI suggests ${difficultyLabel(suggestedDifficulty)}. Adjust effort only if that does not match the habit."
        isBundled ->
            "Visible on Today. Adjust effort if this habit should reserve more attention."
        difficulty >= 4 ->
            "High-effort habit. Consider showing it on Today so it gets planned."
        else -> "Tune difficulty and whether this habit appears on Today."
    }
}

private fun habitModalAdaptiveHints(
    title: String,
    suggestions: List<HabitAssistSuggestion>
): HabitModalAdaptiveHints {
    val normalized = habitTemplateContextText(title, suggestions)
    val contextKind = contextualHabitContextKind(normalized)

    return HabitModalAdaptiveHints(
        showRecurrence = suggestions.any { it is HabitAssistSuggestion.Recurrence } ||
            HABIT_RECURRENCE_WORDS.any { normalized.contains(it) } ||
            contextKind == HabitContextKind.DENTAL ||
            contextKind == HabitContextKind.NUTRITION,
        showWindow = suggestions.any { it is HabitAssistSuggestion.Window } ||
            HABIT_WINDOW_WORDS.any { normalized.contains(it) } ||
            contextKind == HabitContextKind.DENTAL ||
            contextKind == HabitContextKind.LEARNING ||
            contextKind == HabitContextKind.NUTRITION,
        showEffort = suggestions.any {
            it is HabitAssistSuggestion.Difficulty || it is HabitAssistSuggestion.DayPlan
        } || HABIT_EFFORT_WORDS.any { normalized.contains(it) } ||
            contextKind == HabitContextKind.WORKOUT ||
            contextKind == HabitContextKind.DENTAL ||
            contextKind == HabitContextKind.LEARNING ||
            contextKind == HabitContextKind.NUTRITION,
        showLaunch = HABIT_LAUNCH_WORDS.any { normalized.contains(it) },
        contextKind = contextKind
    )
}

private fun contextualHabitContextKind(normalizedContext: String): HabitContextKind? {
    return when {
        normalizedContext.hasWorkoutTemplateContext() ->
            HabitContextKind.WORKOUT
        normalizedContext.containsAnyTemplateWord("water", "hydrate", "hydration") ->
            HabitContextKind.HYDRATION
        normalizedContext.hasMindfulnessTemplateContext() ->
            HabitContextKind.MINDFULNESS
        normalizedContext.containsAnyTemplateWord("read", "reading", "book") ->
            HabitContextKind.READING
        normalizedContext.containsAnyTemplateWord("walk", "steps", "mobility", "stretch") ->
            HabitContextKind.WALKING
        normalizedContext.hasDentalTemplateContext() ->
            HabitContextKind.DENTAL
        normalizedContext.hasLearningTemplateContext() ->
            HabitContextKind.LEARNING
        normalizedContext.hasNutritionTemplateContext() ->
            HabitContextKind.NUTRITION
        normalizedContext.containsAnyTemplateWord("sleep", "bed", "bedtime", "wind down") ->
            HabitContextKind.SLEEP
        else -> null
    }
}

private fun habitAddModalDynamicTitle(
    habitTitle: String,
    hints: HabitModalAdaptiveHints,
    suggestions: List<HabitAssistSuggestion>
): String {
    val aiTitleSuggestion = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Title)?.title?.trim()?.takeIf(String::isNotBlank)
    }
    val normalized = habitTitle.lowercase()
    return when {
        habitTitle.isBlank() && aiTitleSuggestion != null ->
            "New ${aiTitleSuggestion.trim().replaceFirstChar { it.uppercaseChar() }} habit"
        hints.contextKind == HabitContextKind.WORKOUT -> "New workout habit"
        hints.contextKind == HabitContextKind.HYDRATION -> "New hydration habit"
        hints.contextKind == HabitContextKind.MINDFULNESS -> "New mindfulness habit"
        hints.contextKind == HabitContextKind.READING -> "New reading habit"
        hints.contextKind == HabitContextKind.WALKING -> "New walking habit"
        hints.contextKind == HabitContextKind.SLEEP -> "New sleep habit"
        hints.contextKind == HabitContextKind.DENTAL -> "New dental habit"
        hints.contextKind == HabitContextKind.LEARNING -> "New learning habit"
        hints.contextKind == HabitContextKind.NUTRITION -> "New nutrition habit"
        hints.showRecurrence && hints.showWindow -> "New recurring habit window"
        hints.showRecurrence -> "New recurring habit"
        hints.showWindow -> "New scheduled habit"
        hints.showEffort -> "New effort-based habit"
        hints.showLaunch -> "New habit with launch action"
        "walk" in normalized || "read" in normalized || "journal" in normalized || "workout" in normalized ->
            "New ${habitTitle.trim().takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercaseChar() }.orEmpty()} habit".trim()
        else -> "New habit"
    }
}

private fun habitModalDynamicTitle(
    isAddMode: Boolean,
    habitTitle: String,
    hints: HabitModalAdaptiveHints,
    suggestions: List<HabitAssistSuggestion>,
    editTitle: String
): String {
    return if (isAddMode) {
        habitAddModalDynamicTitle(
            habitTitle = habitTitle,
            hints = hints,
            suggestions = suggestions
        )
    } else {
        editTitle
    }
}

private fun habitAddModalDynamicSubtitle(
    habitTitle: String,
    hints: HabitModalAdaptiveHints,
    cadence: String,
    start: String,
    end: String,
    suggestions: List<HabitAssistSuggestion>
): String {
    val suggestedCadence = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Recurrence)?.cadence?.trim()?.takeIf(String::isNotBlank)
    }
    return when {
        hints.contextKind == HabitContextKind.WORKOUT ->
            "Workout context detected. Confirm cadence, evening window, and Today visibility before saving."
        hints.contextKind == HabitContextKind.HYDRATION ->
            "Hydration context detected. Use a broad daily window instead of over-scheduling it."
        hints.contextKind == HabitContextKind.MINDFULNESS ->
            "Mindfulness context detected. Keep the window small enough to make it easy to start."
        hints.contextKind == HabitContextKind.READING ->
            "Reading context detected. Pick a repeatable window and effort that matches the session length."
        hints.contextKind == HabitContextKind.WALKING ->
            "Walking context detected. Confirm the best daily window and whether it should appear on Today."
        hints.contextKind == HabitContextKind.SLEEP ->
            "Sleep routine context detected. Use an evening window that supports wind-down."
        hints.contextKind == HabitContextKind.DENTAL ->
            "Dental routine detected. Keep it easy, daily, and tied to morning or bedtime."
        hints.contextKind == HabitContextKind.LEARNING ->
            "Learning context detected. Pick a realistic study window and moderate effort."
        hints.contextKind == HabitContextKind.NUTRITION ->
            "Nutrition context detected. Use meal-time windows and keep the habit flexible if it spans the day."
        hints.showRecurrence && hints.showWindow ->
            "Cadence and window are set to $cadence from your context."
        hints.showRecurrence ->
            "Habit cadence is suggested as $cadence from your input."
        hints.showWindow ->
            "Use the detected window ($start to $end) to schedule this habit."
        hints.showEffort ->
            "Effort and bundling details are available for this habit."
        hints.showLaunch ->
            "Add a quick launch target to jump to this app or routine."
        suggestedCadence != null && habitTitle.isBlank() ->
            "AI suggests this cadence: $suggestedCadence."
        habitTitle.isNotBlank() ->
            "Set when you want to complete ${habitTitle.lowercase()} each day."
        else -> "Set when you want to complete this habit each day."
    }
}

private fun habitModalDynamicSubtitle(
    isAddMode: Boolean,
    habitTitle: String,
    hints: HabitModalAdaptiveHints,
    cadence: String,
    start: String,
    end: String,
    suggestions: List<HabitAssistSuggestion>,
    editSubtitle: String
): String {
    return if (isAddMode) {
        habitAddModalDynamicSubtitle(
            habitTitle = habitTitle,
            hints = hints,
            cadence = cadence,
            start = start,
            end = end,
            suggestions = suggestions
        )
    } else {
        editSubtitle
    }
}

private fun habitEditModalDynamicTitle(habitTitle: String): String {
    return if (habitTitle.isNotBlank()) "Edit habit: ${habitTitle.trim()}" else "Edit habit"
}

private fun habitEditModalDynamicSubtitle(
    habitTitle: String,
    recurrenceSummary: String,
    difficultyLabel: String,
    isBundled: Boolean,
    startMinute: Int?,
    endMinute: Int?,
    launchLabel: String?
): String {
    if (habitTitle.isBlank()) return "Editing habit details"
    val details = buildList {
        if (recurrenceSummary.isNotBlank()) add(recurrenceSummary)
        add(difficultyLabel)
        add(if (isBundled) "day plan" else "flexible")
        if (startMinute != null && endMinute != null) {
            add("${formatDisplayMinute(startMinute)}-${formatDisplayMinute(endMinute)}")
        }
        launchLabel?.takeIf(String::isNotBlank)?.let { add("opens $it") }
    }.take(3)
    return if (details.isEmpty()) {
        "Editing ${habitTitle.trim()}"
    } else {
        "Editing ${habitTitle.trim()} · ${details.joinToString(" · ")}"
    }
}

private data class HabitTranscriptDraft(
    val title: String? = null,
    val cadence: String? = null,
    val windowPreset: String? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val difficulty: Int? = null,
    val isBundled: Boolean? = null
)

private fun habitTranscriptDraft(capture: String): HabitTranscriptDraft {
    val normalized = capture.lowercase()
    val window = habitTranscriptWindow(normalized)
    return HabitTranscriptDraft(
        title = habitTranscriptTitle(normalized),
        cadence = habitTranscriptCadence(normalized),
        windowPreset = window?.preset,
        startMinute = window?.startMinute,
        endMinute = window?.endMinute,
        difficulty = habitTranscriptDifficulty(normalized),
        isBundled = true.takeIf {
            Regex("""\b(day plan|plan my day|schedule|focus block|protect time)\b""")
                .containsMatchIn(normalized)
        }
    )
}

private data class HabitTranscriptWindow(
    val preset: String,
    val startMinute: Int,
    val endMinute: Int
)

private fun habitTranscriptTitle(normalized: String): String? = when {
    normalized.hasHydrationTemplateContext() -> hydrationHabitTitleOption(normalized)
    normalized.hasDentalTemplateContext() -> if ("floss" in normalized) "Floss" else "Brush and floss"
    "duolingo" in normalized || normalized.hasLearningTemplateContext() ->
        if ("spanish" in normalized) "Study Spanish" else "Language practice"
    "myfitnesspal" in normalized || "my fitness pal" in normalized ||
        "calorie" in normalized || "macro" in normalized -> "Log calories"
    normalized.hasNutritionTemplateContext() -> when {
        "caffeine" in normalized || "coffee" in normalized -> "Limit caffeine"
        "sugar" in normalized -> "Reduce sugar"
        else -> "Track meals"
    }
    "strava" in normalized || "run" in normalized -> "Run"
    normalized.containsAnyTemplateWord("gym", "lift", "strength") -> "Gym"
    normalized.hasWorkoutTemplateContext() -> "Workout"
    normalized.hasMindfulnessTemplateContext() || "headspace" in normalized || "calm" in normalized -> "Meditate"
    "read" in normalized || "book" in normalized -> "Read 20 min"
    "journal" in normalized || "reflect" in normalized -> "Journal"
    else -> null
}

private fun habitTranscriptCadence(normalized: String): String? = when {
    "every other day" in normalized -> "Every 2 days"
    "weekday" in normalized || "workday" in normalized -> "Weekdays"
    "weekend" in normalized -> "Weekends"
    "mon" in normalized && "wed" in normalized && "fri" in normalized -> "Mon/Wed/Fri"
    "tue" in normalized && "thu" in normalized -> "Tue/Thu"
    Regex("""\b(daily|every day|every morning|every night|nightly)\b""")
        .containsMatchIn(normalized) -> "Daily"
    Regex("""\b(weekly|once a week)\b""").containsMatchIn(normalized) -> "Weekly"
    else -> null
}

private fun habitTranscriptWindow(normalized: String): HabitTranscriptWindow? = when {
    Regex("""\b(all day|anytime|flexible)\b""").containsMatchIn(normalized) ||
        normalized.hasHydrationTemplateContext() -> HabitTranscriptWindow("All day", 8 * 60, 20 * 60)
    Regex("""\b(morning|am|wake|breakfast)\b""").containsMatchIn(normalized) ->
        HabitTranscriptWindow("Morning", 8 * 60, 10 * 60)
    Regex("""\b(lunch|noon|midday)\b""").containsMatchIn(normalized) ||
        normalized.hasNutritionTemplateContext() -> HabitTranscriptWindow("Midday", 12 * 60, 13 * 60)
    "afternoon" in normalized -> HabitTranscriptWindow("Afternoon", 15 * 60, 16 * 60)
    Regex("""\b(evening|night|pm|dinner|bedtime|sleep|wind down)\b""").containsMatchIn(normalized) ||
        normalized.hasWorkoutTemplateContext() ||
        normalized.hasDentalTemplateContext() ||
        normalized.hasLearningTemplateContext() -> HabitTranscriptWindow("Evening", 18 * 60, 19 * 60)
    normalized.hasMindfulnessTemplateContext() -> HabitTranscriptWindow("Morning", 8 * 60, 9 * 60)
    else -> null
}

private fun habitTranscriptDifficulty(normalized: String): Int? = when {
    Regex("""\b(tiny|simple|easy|hydrate|water)\b""").containsMatchIn(normalized) -> 1
    Regex("""\b(intense|hard|heavy|long)\b""").containsMatchIn(normalized) -> 5
    normalized.hasWorkoutTemplateContext() ||
        normalized.hasLearningTemplateContext() ||
        "study" in normalized -> 3
    normalized.hasDentalTemplateContext() ||
        normalized.hasNutritionTemplateContext() ||
        normalized.hasMindfulnessTemplateContext() ||
        "stretch" in normalized ||
        "read" in normalized -> 2
    else -> null
}

private fun mergeHabitTranscriptTitle(title: String, transcript: String): String {
    val normalizedTitle = title.trim()
    val normalizedTranscript = transcript.trim()
    return when {
        normalizedTranscript.isBlank() -> normalizedTitle
        normalizedTitle.isBlank() -> normalizedTranscript
        normalizedTitle.contains(normalizedTranscript, ignoreCase = true) -> normalizedTitle
        else -> "$normalizedTitle $normalizedTranscript"
    }
}

private fun habitModalActionLabels(isAddMode: Boolean): ChronosModalActionLabels {
    return ChronosModalActionLabels(
        confirm = if (isAddMode) "Add habit" else "Save habit",
        duplicate = "Duplicate habit",
        archive = if (isAddMode) "Archive habit" else "Delete habit"
    )
}

private val HABIT_RECURRENCE_WORDS = listOf(
    "daily",
    "weekday",
    "weekend",
    "weekly",
    "every",
    "2x",
    "3x",
    "twice",
    "times a week"
)

private val HABIT_WINDOW_WORDS = listOf(
    "morning",
    "midday",
    "lunch",
    "afternoon",
    "evening",
    "night",
    "bedtime",
    "gym",
    "workout",
    "training",
    "strength",
    "cardio",
    "exercise",
    "fitness",
    "weights",
    "lift",
    "yoga",
    "pilates",
    "swim",
    "bike",
    "cycle",
    "walk",
    "run",
    "brush",
    "floss",
    "teeth",
    "dental",
    "sleep",
    "wind down",
    "study",
    "learn",
    "practice",
    "spanish",
    "language",
    "meal",
    "nutrition",
    "caffeine",
    "sugar"
)

private val HABIT_EFFORT_WORDS = listOf(
    "easy",
    "hard",
    "intense",
    "gym",
    "workout",
    "training",
    "cardio",
    "exercise",
    "fitness",
    "weights",
    "lift",
    "yoga",
    "pilates",
    "swim",
    "bike",
    "cycle",
    "run",
    "tiny",
    "simple",
    "stretch",
    "strength",
    "study",
    "learn",
    "practice",
    "caffeine",
    "sugar"
)

private val HABIT_LAUNCH_WORDS = listOf(
    "open app",
    "launch app",
    "app:",
    "package:",
    "intent:",
    "deep link"
)

private val HYDRATION_TARGET_PATTERN = Regex(
    """\b\d+(?:\.\d+)?\s*(?:cups?|glasses?|oz|ounces?|liters?|litres?|l|ml|milliliters?)\b""",
    RegexOption.IGNORE_CASE
)

private val HABIT_WORKOUT_TEMPLATE_WORDS = arrayOf(
    "gym",
    "workout",
    "workouts",
    "work out",
    "working out",
    "exercise",
    "fitness",
    "strength",
    "training",
    "cardio",
    "weights",
    "weightlifting",
    "lift",
    "lifting",
    "run",
    "running",
    "yoga",
    "pilates",
    "swim",
    "swimming",
    "bike",
    "biking",
    "cycle",
    "cycling"
)

private val HABIT_MINDFULNESS_TEMPLATE_WORDS = arrayOf(
    "meditate",
    "meditation",
    "mindful",
    "mindfulness",
    "breathing",
    "breath",
    "journal",
    "journaling",
    "reflect",
    "reflection"
)

private val HABIT_DENTAL_TEMPLATE_WORDS = arrayOf(
    "brush",
    "brushing",
    "floss",
    "flossing",
    "teeth",
    "dental"
)

private val HABIT_LEARNING_TEMPLATE_WORDS = arrayOf(
    "study",
    "studying",
    "learn",
    "learning",
    "practice",
    "spanish",
    "language",
    "course",
    "lesson",
    "flashcards"
)

private val HABIT_NUTRITION_TEMPLATE_WORDS = arrayOf(
    "meal",
    "meals",
    "nutrition",
    "protein",
    "vegetable",
    "veggies",
    "cook",
    "cooking",
    "meal prep",
    "caffeine",
    "coffee",
    "sugar"
)

private const val AUTO_ASSIST_DEBOUNCE_MS = 650L
internal fun shouldAutoRequestHabitAssistForCapture(capture: String): Boolean {
    val normalized = capture.trim().lowercase()
    if (normalized.length < 6) return false
    return normalized.hasHydrationTemplateContext() ||
        normalized.hasMindfulnessTemplateContext() ||
        normalized.hasDentalTemplateContext() ||
        normalized.hasLearningTemplateContext() ||
        normalized.hasNutritionTemplateContext() ||
        HABIT_RECURRENCE_WORDS.any { normalized.contains(it) } ||
        HABIT_WINDOW_WORDS.any { normalized.contains(it) } ||
        HABIT_EFFORT_WORDS.any { normalized.contains(it) } ||
        HABIT_LAUNCH_WORDS.any { normalized.contains(it) }
}

private fun contextualHabitTitleSuggestions(
    title: String,
    suggestions: List<HabitAssistSuggestion>
): List<String> {
    val normalized = title.lowercase()
    val suggestedTitles = suggestions.mapNotNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Title)?.title?.trim()?.takeIf(String::isNotBlank)
    }
    val contextOptions = buildList {
        if ("walk" in normalized || "steps" in normalized) add("Morning walk")
        if (normalized.containsAnyTemplateWord("yoga")) add("Yoga")
        if (normalized.containsAnyTemplateWord("pilates")) add("Pilates")
        if (normalized.hasWorkoutTemplateContext()) add("Workout")
        if (normalized.containsAnyTemplateWord("meditate", "meditation")) add("Meditate")
        if (normalized.containsAnyTemplateWord("breathing", "breath")) add("Breathing exercise")
        if ("read" in normalized || "book" in normalized) add("Read 20 min")
        if ("journal" in normalized || "reflect" in normalized) add("Journal")
        if (normalized.hasHydrationTemplateContext()) add(hydrationHabitTitleOption(normalized))
        if (normalized.hasDentalTemplateContext()) add(if ("floss" in normalized) "Floss" else "Brush and floss")
        if (normalized.hasLearningTemplateContext()) add(if ("spanish" in normalized) "Study Spanish" else "Study 25 min")
        if (normalized.hasNutritionTemplateContext()) {
            add(
                when {
                    "caffeine" in normalized || "coffee" in normalized -> "Limit caffeine"
                    "sugar" in normalized -> "Reduce sugar"
                    else -> "Prep healthy meal"
                }
            )
        }
        HABIT_LAUNCH_APP_HINTS.firstOrNull { hint ->
            hint.terms.any { term ->
                Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(normalized)
            }
        }?.let { hint ->
            add(
                when (hint.label) {
                    "Duolingo" -> if ("spanish" in normalized) "Study Spanish" else "Language practice"
                    "Strava" -> if ("run" in normalized) "Track run" else "Outdoor workout"
                    "Headspace", "Calm" -> "Meditate"
                    "Spotify" -> if (normalized.hasWorkoutTemplateContext()) "Workout playlist" else "Listen intentionally"
                    "YouTube" -> when {
                        normalized.containsAnyTemplateWord("yoga", "stretch", "mobility") -> "Follow movement video"
                        normalized.hasWorkoutTemplateContext() -> "Follow workout video"
                        else -> "Watch lesson"
                    }
                    "Google Fit", "Fitbit" -> "Track activity"
                    "MyFitnessPal" -> if ("calorie" in normalized || "macro" in normalized) "Log calories" else "Track meals"
                    else -> "Open ${hint.label}"
                }
            )
        }
        if ("stretch" in normalized || "mobility" in normalized) add("Stretch")
    }
    return (suggestedTitles + contextOptions + habitTitleSuggestions)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .take(6)
}

internal fun contextualHabitRecurrenceOptions(
    selectedPreset: String,
    title: String,
    suggestions: List<HabitAssistSuggestion>
): List<String> {
    val normalizedTitle = title.lowercase()
    val capturedCadences = buildList {
        when {
            normalizedTitle.contains("every other day") -> add("Every 2 days")
            normalizedTitle.contains("weekday") || normalizedTitle.contains("workday") -> add("Weekdays")
            normalizedTitle.contains("weekend") -> add("Weekends")
            normalizedTitle.contains("mon") && normalizedTitle.contains("wed") && normalizedTitle.contains("fri") -> add("Mon/Wed/Fri")
            normalizedTitle.contains("tue") && normalizedTitle.contains("thu") -> add("Tue/Thu")
            Regex("""\b(daily|every day|every morning|every night)\b""").containsMatchIn(normalizedTitle) -> add("Daily")
            Regex("""\b(weekly|once a week)\b""").containsMatchIn(normalizedTitle) -> add("Weekly")
        }
        if (isEmpty() && normalizedTitle.hasHydrationTemplateContext()) add("Daily")
        if (isEmpty() && (normalizedTitle.hasDentalTemplateContext() || normalizedTitle.hasNutritionTemplateContext())) add("Daily")
        if (isEmpty() && normalizedTitle.hasLearningTemplateContext()) add("Weekdays")
        when {
            Regex("""\b(6x|six times|six)\b""").containsMatchIn(normalizedTitle) -> add("6x / week")
            Regex("""\b(5x|five times|five)\b""").containsMatchIn(normalizedTitle) -> add("5x / week")
            Regex("""\b(4x|four times|four)\b""").containsMatchIn(normalizedTitle) -> add("4x / week")
            Regex("""\b(3x|three times|three)\b""").containsMatchIn(normalizedTitle) -> add("3x / week")
            Regex("""\b(2x|twice|two times)\b""").containsMatchIn(normalizedTitle) -> add("2x / week")
            isEmpty() && normalizedTitle.hasWorkoutTemplateContext() -> add("3x / week")
        }
    }
    val suggestedCadences = suggestions.mapNotNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Recurrence)?.cadence?.takeIf { it in habitRecurrenceQuickPresets }
    }
    val contextualCadences = suggestedCadences + capturedCadences
    val optionLimit = if (contextualCadences.isNotEmpty()) 5 else 6
    return (contextualCadences + selectedPreset.takeIf { it in habitRecurrenceQuickPresets } + habitRecurrenceQuickPresets)
        .filterNotNull()
        .distinct()
        .take(optionLimit)
}

internal fun contextualHabitWindowSignal(title: String): String? {
    val normalizedTitle = title.lowercase()
    return when {
        Regex("""\b(morning|am|wake|walk)\b""").containsMatchIn(normalizedTitle) -> "Morning"
        Regex("""\b(lunch|noon|midday)\b""").containsMatchIn(normalizedTitle) || normalizedTitle.hasNutritionTemplateContext() -> "Midday"
        normalizedTitle.contains("afternoon") -> "Afternoon"
        Regex("""\b(evening|night|pm|journal|read|bedtime|sleep|floss)\b""").containsMatchIn(normalizedTitle) ||
            normalizedTitle.hasWorkoutTemplateContext() ||
            normalizedTitle.hasDentalTemplateContext() ||
            normalizedTitle.hasLearningTemplateContext() -> "Evening"
        Regex("""\b(meditate|meditation|mindful)\b""").containsMatchIn(normalizedTitle) -> "Morning"
        Regex("""\b(all day)\b""").containsMatchIn(normalizedTitle) || normalizedTitle.hasHydrationTemplateContext() -> "All day"
        else -> null
    }
}

// While the window is untouched in a fresh Add form, it follows the capture context
// ("morning run" → Morning window) instead of the all-day default — an AI Window
// suggestion wins over keyword cues; null means no signal (keep the default).
internal fun contextualHabitDefaultWindow(
    title: String,
    suggestions: List<HabitAssistSuggestion>
): Pair<Int, Int>? {
    suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Window)?.let { it.startMinute to it.endMinute }
    }?.let { return it }
    return contextualHabitWindowSignal(title)?.let { habitWindowPresets[it] }
}

internal fun contextualHabitWindowOptions(
    selectedPreset: String,
    title: String,
    suggestions: List<HabitAssistSuggestion>
): List<String> {
    val capturedWindows = listOfNotNull(contextualHabitWindowSignal(title))
    val suggestedWindows = suggestions.mapNotNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Window)?.let { window ->
            resolveHabitWindowPreset(window.startMinute, window.endMinute)
        }?.takeIf { it in habitWindowPresets }
    }
    val contextualWindows = suggestedWindows + capturedWindows
    val optionLimit = if (contextualWindows.isNotEmpty()) 4 else 5
    return (contextualWindows + selectedPreset.takeIf { it in habitWindowPresets } + habitWindowPresets.keys)
        .filterNotNull()
        .distinct()
        .take(optionLimit)
}

internal fun contextualHabitDifficultyOptions(
    title: String,
    suggestions: List<HabitAssistSuggestion>
): List<String> {
    val normalized = title.lowercase()
    val suggestedOptions = suggestions.mapNotNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.Difficulty)
            ?.difficulty
            ?.coerceIn(1, 5)
            ?.let(::difficultyLabel)
    }
    val contextOptions = buildList {
        if (normalized.hasWorkoutTemplateContext()) {
            add(difficultyLabel(4))
        }
        if ("walk" in normalized || "stretch" in normalized || "mobility" in normalized || normalized.hasMindfulnessTemplateContext()) {
            add(difficultyLabel(2))
        }
        if (normalized.hasLearningTemplateContext()) {
            add(difficultyLabel(3))
        }
        if (normalized.hasDentalTemplateContext() || normalized.hasNutritionTemplateContext()) {
            add(difficultyLabel(2))
        }
        if (normalized.hasHydrationTemplateContext() || "tiny" in normalized || "simple" in normalized) {
            add(difficultyLabel(1))
        }
        if ("intense" in normalized || "very hard" in normalized) {
            add(difficultyLabel(5))
        }
    }

    val contextualOptions = suggestedOptions + contextOptions
    val optionLimit = if (contextualOptions.isNotEmpty()) 3 else habitDifficultyOptions.size
    return (contextualOptions + habitDifficultyOptions.values)
        .distinct()
        .take(optionLimit)
}

internal fun contextualHabitDayPlanOptions(
    title: String,
    isBundled: Boolean,
    suggestions: List<HabitAssistSuggestion>
): List<String> {
    val normalized = title.lowercase()
    val suggestedOptions = suggestions.mapNotNull { suggestion ->
        (suggestion as? HabitAssistSuggestion.DayPlan)?.isBundled?.let(::habitDayPlanOptionLabel)
    }
    val contextOptions = buildList {
        if (
            Regex("""\b(morning|evening|walk|stretch|focus|practice)\b""")
                .containsMatchIn(normalized) ||
                normalized.hasMindfulnessTemplateContext() ||
                normalized.hasWorkoutTemplateContext() ||
                normalized.hasDentalTemplateContext() ||
                normalized.hasLearningTemplateContext()
        ) {
            add(habitDayPlanOptionLabel(true))
        }
        if (
            Regex("""\b(all day|read|journal|flexible|anytime)\b""")
                .containsMatchIn(normalized) ||
                normalized.hasHydrationTemplateContext() ||
                normalized.hasNutritionTemplateContext()
        ) {
            add(habitDayPlanOptionLabel(false))
        }
    }

    val contextualOptions = suggestedOptions + contextOptions
    val optionLimit = if (contextualOptions.isNotEmpty()) 1 else 2
    return (
        contextualOptions +
            habitDayPlanOptionLabel(isBundled) +
            listOf(habitDayPlanOptionLabel(true), habitDayPlanOptionLabel(false))
        )
        .distinct()
        .take(optionLimit)
}

internal fun habitDayPlanOptionLabel(isBundled: Boolean): String {
    return if (isBundled) "Show on day plan" else "Keep flexible"
}

internal fun habitDayPlanOptionValue(label: String): Boolean {
    return label == habitDayPlanOptionLabel(true)
}

internal data class HabitLaunchCaptureSuggestion(
    val label: String,
    val value: String
)

internal fun habitLaunchCaptureSuggestion(capture: String): HabitLaunchCaptureSuggestion? {
    val match = HABIT_LAUNCH_VALUE_PATTERN.find(capture)
        ?: return inferredHabitLaunchCaptureSuggestion(capture)
    val value = match.value.trim().trimEnd('.', ',', ';', ')', ']')
    val labelSeed = capture
        .replace(HABIT_LAUNCH_VALUE_PATTERN, " ")
        .replace(Regex("""(?i)\b(open app|launch app|with app|using app|app)\b"""), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', ',', ';', '-', ':')
        .take(32)
        .trim()
    val label = if (labelSeed.isBlank()) {
        "Open habit app"
    } else {
        "Open ${labelSeed.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }}"
    }
    return HabitLaunchCaptureSuggestion(label = label, value = value)
}

private data class HabitLaunchAppHint(
    val label: String,
    val value: String,
    val terms: List<String>
)

private val HABIT_LAUNCH_APP_HINTS = listOf(
    HabitLaunchAppHint("Duolingo", "duolingo://", listOf("duolingo")),
    HabitLaunchAppHint("Strava", "strava://", listOf("strava")),
    HabitLaunchAppHint("Headspace", "headspace://", listOf("headspace")),
    HabitLaunchAppHint("Calm", "calm://", listOf("calm")),
    HabitLaunchAppHint("Spotify", "spotify://", listOf("spotify")),
    HabitLaunchAppHint("YouTube", "youtube://", listOf("youtube", "you tube")),
    HabitLaunchAppHint("Google Fit", "com.google.android.apps.fitness", listOf("google fit")),
    HabitLaunchAppHint("Fitbit", "com.fitbit.FitbitMobile", listOf("fitbit")),
    HabitLaunchAppHint("MyFitnessPal", "com.myfitnesspal.android", listOf("myfitnesspal", "my fitness pal"))
)

private fun inferredHabitLaunchCaptureSuggestion(capture: String): HabitLaunchCaptureSuggestion? {
    val normalized = capture.lowercase()
    val appHint = HABIT_LAUNCH_APP_HINTS.firstOrNull { hint ->
        hint.terms.any { term ->
            Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(normalized)
        }
    } ?: return null
    return HabitLaunchCaptureSuggestion(
        label = "Open ${appHint.label}",
        value = appHint.value
    )
}

private val HABIT_LAUNCH_VALUE_PATTERN = Regex(
    """(?<!\w)(?:package:[^\s<>()\[\]]+|component:[^\s<>()\[\]]+|intent:[^\s<>()\[\]]+|[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+|[a-z][a-z0-9+.-]*://[^\s<>()\[\]]+)(?!\w)"""
)

// Every habit suggestion kind replaces the value of the field(s) it targets, so
// applying one dismisses the remaining same-kind alternatives as stale.
internal fun supersededHabitAssistSuggestionIds(
    applied: HabitAssistSuggestion,
    suggestions: List<HabitAssistSuggestion>
): Set<String> = suggestions
    .filter { it::class == applied::class }
    .map { it.id }
    .toSet() + applied.id

// With the auto-apply setting on, a fresh Add form fills itself from the first
// suggestion of each kind — but only while the targeted fields are untouched, so
// manual input always wins.
internal fun autoApplicableHabitAssistSuggestionIds(
    suggestions: List<HabitAssistSuggestion>,
    titleBlank: Boolean,
    recurrenceUntouched: Boolean,
    windowUntouched: Boolean,
    difficultyUntouched: Boolean,
    dayPlanUntouched: Boolean
): Set<String> {
    var titleOpen = titleBlank
    var recurrenceOpen = recurrenceUntouched
    var windowOpen = windowUntouched
    var difficultyOpen = difficultyUntouched
    var dayPlanOpen = dayPlanUntouched
    val ids = mutableSetOf<String>()
    suggestions.forEach { suggestion ->
        when (suggestion) {
            is HabitAssistSuggestion.Title -> if (titleOpen) {
                ids += suggestion.id
                titleOpen = false
            }
            is HabitAssistSuggestion.Recurrence -> if (recurrenceOpen) {
                ids += suggestion.id
                recurrenceOpen = false
            }
            is HabitAssistSuggestion.Window -> if (windowOpen) {
                ids += suggestion.id
                windowOpen = false
            }
            is HabitAssistSuggestion.Difficulty -> if (difficultyOpen) {
                ids += suggestion.id
                difficultyOpen = false
            }
            is HabitAssistSuggestion.DayPlan -> if (dayPlanOpen) {
                ids += suggestion.id
                dayPlanOpen = false
            }
        }
    }
    return ids
}

// A suggestion the form already satisfies is a no-op pill — hide it so the row only
// offers changes. Keeps pills honest after a sibling is applied or a field is edited
// by hand, without an extra Gemini round-trip.
internal fun redundantHabitAssistSuggestionIds(
    suggestions: List<HabitAssistSuggestion>,
    currentTitle: String,
    currentRecurrencePreset: String,
    currentStartMinute: Int?,
    currentEndMinute: Int?,
    currentDifficulty: Int,
    currentIsBundled: Boolean
): Set<String> {
    fun String.normalized() = trim().lowercase()
    return suggestions.filter { suggestion ->
        when (suggestion) {
            is HabitAssistSuggestion.Title ->
                suggestion.title.normalized() == currentTitle.normalized()
            is HabitAssistSuggestion.Recurrence ->
                suggestion.cadence.normalized() == currentRecurrencePreset.normalized()
            is HabitAssistSuggestion.Window ->
                suggestion.startMinute == currentStartMinute && suggestion.endMinute == currentEndMinute
            is HabitAssistSuggestion.Difficulty ->
                suggestion.difficulty == currentDifficulty
            is HabitAssistSuggestion.DayPlan ->
                suggestion.isBundled == currentIsBundled
        }
    }.map { it.id }.toSet()
}

private fun contextualHabitTemplateOptions(
    title: String,
    suggestions: List<HabitAssistSuggestion>,
    templates: List<HabitTemplate>
): List<String> {
    val context = habitTemplateContextText(title, suggestions)
    return templates
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<HabitTemplate>> {
                contextualHabitTemplateScore(it.value, context)
            }.thenBy { it.index }
        )
        .map { it.value.label }
}

private fun hasHabitTemplateContext(
    title: String,
    suggestions: List<HabitAssistSuggestion>
): Boolean = habitTemplateContextText(title, suggestions).isNotBlank()

private fun contextualHabitTemplateScore(template: HabitTemplate, context: String): Int {
    if (context.isBlank()) return 0
    val templateText = "${template.label} ${template.title} ${template.cadence}".lowercase()
    val templateWords = templateText.contextTemplateWords()
    var score = templateWords.count { word -> word in context } * 4
    score += habitTemplateWindowScore(template.startMinute, context)
    if (context.containsAnyTemplateWord("daily", "everyday") && "daily" in templateText) score += 5
    if (context.containsAnyTemplateWord("week", "weekly", "weekday", "weekend") && "week" in templateText) score += 5
    if (context.hasWorkoutTemplateContext() &&
        templateText.containsAnyTemplateWord(*HABIT_WORKOUT_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord("meditate", "mindful", "breathing") &&
        templateText.containsAnyTemplateWord("meditate", "mindful", "breathing")
    ) {
        score += 8
    }
    if (context.hasHydrationTemplateContext() &&
        templateText.containsAnyTemplateWord("water", "hydrate", "hydration")
    ) {
        score += 10
    }
    if (context.hasDentalTemplateContext() &&
        templateText.containsAnyTemplateWord(*HABIT_DENTAL_TEMPLATE_WORDS)
    ) {
        score += 10
    }
    if (context.hasLearningTemplateContext() &&
        templateText.containsAnyTemplateWord(*HABIT_LEARNING_TEMPLATE_WORDS)
    ) {
        score += 10
    }
    if (context.hasNutritionTemplateContext() &&
        templateText.containsAnyTemplateWord(*HABIT_NUTRITION_TEMPLATE_WORDS)
    ) {
        score += 10
    }
    return score
}

private fun habitTemplateWindowScore(startMinute: Int, context: String): Int = when {
    context.containsAnyTemplateWord("morning", "breakfast", "am") && startMinute in 4 * 60 until 12 * 60 -> 5
    context.containsAnyTemplateWord("afternoon", "lunch") && startMinute in 12 * 60 until 17 * 60 -> 5
    context.containsAnyTemplateWord("evening", "dinner", "night", "pm") && startMinute in 17 * 60 until 24 * 60 -> 5
    else -> 0
}

private fun habitTemplateContextText(
    title: String,
    suggestions: List<HabitAssistSuggestion>
): String = buildString {
    append(title)
    suggestions.forEach { suggestion ->
        append(' ')
        append(suggestion.label)
        append(' ')
        append(suggestion.reason)
    }
}.lowercase()

private fun habitLaunchSectionSubtitle(
    title: String,
    launchTarget: AppLaunchTarget?,
    suggestions: List<HabitAssistSuggestion>
): String {
    if (launchTarget != null) {
        return "Launch context detected: ${launchTarget.label}. Keep it or edit the app/deep link before saving."
    }
    val context = habitTemplateContextText(title, suggestions)
    return if (context.containsAnyTemplateWord("app", "open", "launch", "package", "deeplink", "deep link")) {
        "This habit mentions an app or link. Add a launch target so reminders open the right place."
    } else {
        "Connect this habit to the app where the work actually happens."
    }
}

private fun String.contextTemplateWords(): Set<String> {
    return lowercase()
        .split(Regex("""[^a-z0-9]+"""))
        .mapNotNull { word -> word.takeIf { it.length >= 3 } }
        .toSet()
}

private fun String.containsAnyTemplateWord(vararg words: String): Boolean {
    return words.any { word ->
        val normalized = word.lowercase()
        if (normalized.any { !it.isLetterOrDigit() }) {
            normalized in this
        } else {
            Regex("""\b${Regex.escape(normalized)}\b""").containsMatchIn(this)
        }
    }
}

private fun String.hasWorkoutTemplateContext(): Boolean {
    return containsAnyTemplateWord(*HABIT_WORKOUT_TEMPLATE_WORDS)
}

private fun String.hasMindfulnessTemplateContext(): Boolean {
    return containsAnyTemplateWord(*HABIT_MINDFULNESS_TEMPLATE_WORDS)
}

private fun String.hasHydrationTemplateContext(): Boolean {
    return containsAnyTemplateWord(
        "water",
        "hydrate",
        "hydration",
        "cup",
        "cups",
        "glass",
        "glasses",
        "ounce",
        "ounces",
        "oz",
        "liter",
        "liters",
        "litre",
        "litres",
        "ml",
        "milliliter",
        "milliliters"
    ) || HYDRATION_TARGET_PATTERN.containsMatchIn(this)
}

private fun String.hasDentalTemplateContext(): Boolean {
    return containsAnyTemplateWord(*HABIT_DENTAL_TEMPLATE_WORDS)
}

private fun String.hasLearningTemplateContext(): Boolean {
    return containsAnyTemplateWord(*HABIT_LEARNING_TEMPLATE_WORDS)
}

private fun String.hasNutritionTemplateContext(): Boolean {
    return containsAnyTemplateWord(*HABIT_NUTRITION_TEMPLATE_WORDS)
}

private fun hydrationHabitTitleOption(context: String): String {
    val amount = HYDRATION_TARGET_PATTERN.find(context)
        ?.value
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
    return if (amount.isNullOrBlank()) "Hydrate" else "Drink $amount water"
}

internal fun habitEditableSummaryCardContentDescription(
    title: String,
    value: String,
    actionLabel: String,
    expanded: Boolean
): String {
    val currentValue = value.trim().ifBlank { "Not set" }
    val state = if (expanded) "Expanded" else "Collapsed"
    return "${actionLabel.trim()} for ${title.trim()}. Current value: $currentValue. $state"
}

internal fun habitFormCompleteTodayActionLabel(title: String): String {
    val habitName = title.trim().ifBlank { "this habit" }
    return "Complete $habitName today"
}

private data class HabitTemplate(
    val label: String,
    val title: String,
    val cadence: String,
    val startMinute: Int,
    val endMinute: Int,
    val difficulty: Int,
    val isBundled: Boolean
)

internal data class HabitHistoryTemplate(
    val id: String,
    val title: String,
    val cadence: String,
    val startMinute: Int,
    val endMinute: Int,
    val difficulty: Int,
    val isBundled: Boolean,
    val isArchived: Boolean,
    val schedule: HabitSchedule? = null
) {
    val sourceLabel: String
        get() = if (isArchived) "Archived" else "Saved"

    val recurrenceSummary: String
        get() = habitRecurrenceSummary(cadence = cadence, schedule = schedule)

    val displayLabel: String
        get() = "$title · $recurrenceSummary · $sourceLabel"
}

internal fun buildHabitHistoryTemplates(
    habits: List<Habit>,
    currentHabitId: String? = null
): List<HabitHistoryTemplate> = habits
    .asSequence()
    .filter { it.id != currentHabitId }
    .map { habit ->
        HabitHistoryTemplate(
            id = habit.id,
            title = habit.title,
            cadence = habit.cadence,
            schedule = habit.schedule,
            startMinute = habit.windowStartMinute,
            endMinute = habit.windowEndMinute,
            difficulty = habit.difficulty,
            isBundled = habit.isBundled,
            isArchived = !habit.isActive
        )
    }
    .sortedWith(
        compareBy<HabitHistoryTemplate>({ !it.isArchived }, { it.startMinute }, { it.title.lowercase() })
    )
    .distinctBy { template ->
        listOf(
            template.title.lowercase(),
            template.recurrenceSummary.lowercase(),
            template.startMinute.toString(),
            template.endMinute.toString(),
            template.difficulty.toString(),
            template.isBundled.toString()
        ).joinToString("|")
    }
    .take(8)
    .toList()

internal fun filterHabitHistoryTemplates(
    templates: List<HabitHistoryTemplate>,
    query: String
): List<HabitHistoryTemplate> {
    val terms = query.trim().split(habitHistoryQueryTokenSplit).filter { it.isNotBlank() }
    if (terms.isEmpty()) return templates
    return templates.filter { template ->
        terms.all(template::matchesHabitHistoryTerm)
    }
}

private val habitHistoryQueryTokenSplit = Regex("\\s+")

private fun HabitHistoryTemplate.matchesHabitHistoryTerm(term: String): Boolean =
    title.contains(term, ignoreCase = true) ||
        recurrenceSummary.contains(term, ignoreCase = true) ||
        sourceLabel.contains(term, ignoreCase = true)

internal fun prioritizeRecentHabitHistoryTemplates(
    templates: List<HabitHistoryTemplate>,
    recentIds: List<String>
): List<HabitHistoryTemplate> {
    if (templates.isEmpty() || recentIds.isEmpty()) return templates
    val positions = recentIds.withIndex().associate { it.value to it.index }
    return templates.sortedWith(
        compareBy<HabitHistoryTemplate>({ positions[it.id] == null }, { positions[it.id] ?: Int.MAX_VALUE })
    )
}

internal fun prioritizeContextualHabitHistoryTemplates(
    templates: List<HabitHistoryTemplate>,
    recentIds: List<String>,
    title: String,
    suggestions: List<HabitAssistSuggestion>
): List<HabitHistoryTemplate> {
    val recentRanked = prioritizeRecentHabitHistoryTemplates(templates, recentIds)
    val context = habitTemplateContextText(title, suggestions)
    if (context.isBlank()) return recentRanked
    return recentRanked
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<HabitHistoryTemplate>> {
                contextualHabitHistoryScore(it.value, context)
            }.thenBy { it.index }
        )
        .map { it.value }
}

private fun hasContextualHabitHistoryMatch(
    templates: List<HabitHistoryTemplate>,
    title: String,
    suggestions: List<HabitAssistSuggestion>
): Boolean {
    val context = habitTemplateContextText(title, suggestions)
    return context.isNotBlank() && templates.any { template ->
        contextualHabitHistoryScore(template, context) > 0
    }
}

private fun contextualHabitHistoryScore(
    template: HabitHistoryTemplate,
    context: String
): Int {
    val templateText = "${template.title} ${template.recurrenceSummary} ${template.sourceLabel}".lowercase()
    var score = templateText.contextTemplateWords().count { word -> word in context } * 4
    score += habitTemplateWindowScore(template.startMinute, context)
    if (context.containsAnyTemplateWord("gym", "workout", "exercise", "fitness", "strength") &&
        templateText.containsAnyTemplateWord("gym", "workout", "exercise", "fitness", "strength")
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord("meditate", "mindful", "breathing") &&
        templateText.containsAnyTemplateWord("meditate", "mindful", "breathing")
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord("walk", "run", "steps") &&
        templateText.containsAnyTemplateWord("walk", "run", "steps")
    ) {
        score += 8
    }
    return score
}

internal fun parseRecentHabitTemplateIds(raw: String): List<String> = raw
    .split('|')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .take(5)

internal fun encodeRecentHabitTemplateIds(ids: List<String>): String = ids
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .take(5)
    .joinToString("|")

private inline fun applyHabitHistoryTemplate(
    template: HabitHistoryTemplate,
    apply: (HabitHistoryTemplate) -> Unit
) {
    apply(template)
}

internal fun Habit.statusLabel(nowMinute: Int, today: LocalDate = LocalDate.now()): String? = when {
    lastCompletedDate == today -> withStreakSuffix("Done today")
    nowMinute in windowStartMinute..windowEndMinute -> withStreakSuffix("Due now")
    nowMinute < windowStartMinute -> withStreakSuffix("Starts ${formatDisplayMinute(windowStartMinute)}")
    else -> withStreakSuffix("Window ended")
}

private fun resolveHabitWindowPreset(startMinute: Int, endMinute: Int): String =
    habitWindowPresets.entries.firstOrNull { (_, range) ->
        range?.first == startMinute && range.second == endMinute
    }?.key ?: "Custom"

private fun difficultyLabel(level: Int): String = when (level) {
    1 -> "Very easy"
    2 -> "Easy"
    3 -> "Moderate"
    4 -> "Hard"
    else -> "Very hard"
}

private fun Habit.withStreakSuffix(base: String): String =
    if (streakCount > 0) "$base · $streakCount-day streak" else base

private fun resolveHabitRecurrenceQuickPreset(state: HabitRecurrenceEditorState): String = when {
    state.kind == HabitRecurrenceEditorKind.SCHEDULED &&
        state.scheduleMode == HabitRecurrenceScheduleMode.DAILY -> "Daily"
    state.kind == HabitRecurrenceEditorKind.SCHEDULED &&
        state.scheduleMode == HabitRecurrenceScheduleMode.WEEKDAYS -> "Weekdays"
    state.kind == HabitRecurrenceEditorKind.SCHEDULED &&
        state.scheduleMode == HabitRecurrenceScheduleMode.WEEKENDS -> "Weekends"
    state.kind == HabitRecurrenceEditorKind.SCHEDULED &&
        state.scheduleMode == HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS &&
        state.weekdays == habitWeekdayShortcuts["Mon/Wed/Fri"] -> "Mon/Wed/Fri"
    state.kind == HabitRecurrenceEditorKind.SCHEDULED &&
        state.scheduleMode == HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS &&
        state.weekdays == habitWeekdayShortcuts["Tue/Thu"] -> "Tue/Thu"
    state.kind == HabitRecurrenceEditorKind.SCHEDULED &&
        state.scheduleMode == HabitRecurrenceScheduleMode.EVERY_N_WEEKS &&
        state.interval == 1 &&
        state.weekdays.isEmpty() -> "Weekly"
    state.kind == HabitRecurrenceEditorKind.SCHEDULED &&
        state.scheduleMode == HabitRecurrenceScheduleMode.EVERY_N_DAYS &&
        state.interval == 2 -> "Every 2 days"
    state.kind == HabitRecurrenceEditorKind.QUOTA &&
        state.quotaCompletions == 2 &&
        state.quotaPeriodUnit == HabitRecurrencePeriodUnit.WEEK &&
        state.quotaInterval == 1 -> "2x / week"
    state.kind == HabitRecurrenceEditorKind.QUOTA &&
        state.quotaCompletions == 3 &&
        state.quotaPeriodUnit == HabitRecurrencePeriodUnit.WEEK &&
        state.quotaInterval == 1 -> "3x / week"
    state.kind == HabitRecurrenceEditorKind.QUOTA &&
        state.quotaCompletions == 4 &&
        state.quotaPeriodUnit == HabitRecurrencePeriodUnit.WEEK &&
        state.quotaInterval == 1 -> "4x / week"
    state.kind == HabitRecurrenceEditorKind.QUOTA &&
        state.quotaCompletions == 5 &&
        state.quotaPeriodUnit == HabitRecurrencePeriodUnit.WEEK &&
        state.quotaInterval == 1 -> "5x / week"
    state.kind == HabitRecurrenceEditorKind.QUOTA &&
        state.quotaCompletions == 6 &&
        state.quotaPeriodUnit == HabitRecurrencePeriodUnit.WEEK &&
        state.quotaInterval == 1 -> "6x / week"
    state.kind == HabitRecurrenceEditorKind.QUOTA &&
        state.quotaCompletions == 1 &&
        state.quotaPeriodUnit == HabitRecurrencePeriodUnit.MONTH &&
        state.quotaInterval == 1 -> "1x / month"
    state.kind == HabitRecurrenceEditorKind.QUOTA &&
        state.quotaCompletions == 2 &&
        state.quotaPeriodUnit == HabitRecurrencePeriodUnit.MONTH &&
        state.quotaInterval == 1 -> "2x / month"
    state.kind == HabitRecurrenceEditorKind.QUOTA &&
        state.quotaCompletions == 5 &&
        state.quotaPeriodUnit == HabitRecurrencePeriodUnit.MONTH &&
        state.quotaInterval == 1 -> "5x / month"
    else -> "Custom"
}

private fun recurrenceStateForQuickPreset(preset: String): HabitRecurrenceEditorState = when (preset) {
    "Daily" -> HabitRecurrenceEditorState(scheduleMode = HabitRecurrenceScheduleMode.DAILY)
    "Weekdays" -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.WEEKDAYS,
        weekdays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )
    )
    "Weekends" -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.WEEKENDS,
        weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    )
    "Mon/Wed/Fri" -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS,
        weekdays = habitWeekdayShortcuts.getValue("Mon/Wed/Fri")
    )
    "Tue/Thu" -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS,
        weekdays = habitWeekdayShortcuts.getValue("Tue/Thu")
    )
    "Weekly" -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_WEEKS,
        interval = 1
    )
    "Every 2 days" -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_DAYS,
        interval = 2
    )
    "2x / week" -> HabitRecurrenceEditorState(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = 2,
        quotaPeriodUnit = HabitRecurrencePeriodUnit.WEEK,
        quotaInterval = 1
    )
    "3x / week" -> HabitRecurrenceEditorState(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = 3,
        quotaPeriodUnit = HabitRecurrencePeriodUnit.WEEK,
        quotaInterval = 1
    )
    "4x / week" -> HabitRecurrenceEditorState(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = 4,
        quotaPeriodUnit = HabitRecurrencePeriodUnit.WEEK,
        quotaInterval = 1
    )
    "5x / week" -> HabitRecurrenceEditorState(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = 5,
        quotaPeriodUnit = HabitRecurrencePeriodUnit.WEEK,
        quotaInterval = 1
    )
    "6x / week" -> HabitRecurrenceEditorState(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = 6,
        quotaPeriodUnit = HabitRecurrencePeriodUnit.WEEK,
        quotaInterval = 1
    )
    "1x / month" -> HabitRecurrenceEditorState(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = 1,
        quotaPeriodUnit = HabitRecurrencePeriodUnit.MONTH,
        quotaInterval = 1
    )
    "2x / month" -> HabitRecurrenceEditorState(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = 2,
        quotaPeriodUnit = HabitRecurrencePeriodUnit.MONTH,
        quotaInterval = 1
    )
    "5x / month" -> HabitRecurrenceEditorState(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = 5,
        quotaPeriodUnit = HabitRecurrencePeriodUnit.MONTH,
        quotaInterval = 1
    )
    else -> HabitRecurrenceEditorState()
}

private fun habitWeekdayShortcutLabel(weekdays: Set<DayOfWeek>): String? =
    habitWeekdayShortcuts.entries.firstOrNull { it.value == weekdays }?.key

private fun quotaPresetLabel(state: HabitRecurrenceEditorState): String? {
    if (state.kind != HabitRecurrenceEditorKind.QUOTA || state.quotaInterval != 1) return null
    return when (state.quotaPeriodUnit) {
        HabitRecurrencePeriodUnit.WEEK -> "${state.quotaCompletions}x / week"
        HabitRecurrencePeriodUnit.MONTH -> "${state.quotaCompletions}x / month"
        HabitRecurrencePeriodUnit.DAY -> "${state.quotaCompletions}x / day"
    }.takeIf { it in habitQuotaPresetLabels }
}

private fun HabitRecurrenceEditorState.applyQuotaPreset(label: String): HabitRecurrenceEditorState {
    val completions = label.substringBefore('x').toIntOrNull()?.coerceAtLeast(1) ?: quotaCompletions
    val unit = if ("month" in label) HabitRecurrencePeriodUnit.MONTH else HabitRecurrencePeriodUnit.WEEK
    return copy(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = completions,
        quotaPeriodUnit = unit,
        quotaInterval = 1
    )
}
