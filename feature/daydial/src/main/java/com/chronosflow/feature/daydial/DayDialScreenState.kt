package com.chronosflow.feature.daydial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronosflow.core.ai.AssistNarrative
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.GenAiRuntimeStatus
import com.chronosflow.core.domain.model.MoodEnergyCheckIn
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.ui.settings.ChronosBackdropTheme
import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import com.chronosflow.core.ui.settings.ChronosUiSettingsKeys
import com.chronosflow.feature.daydial.model.AppearanceMode
import com.chronosflow.feature.daydial.model.InsightsTabUiState
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.SheetTarget
import com.chronosflow.feature.daydial.model.SidebarPage
import java.time.LocalDate

internal data class DayDialViewModelState(
    val timeBlocks: List<TimeBlockUiModel>,
    val freeTime: List<TimeRangeUi>,
    val currentMinute: Int,
    val selectedDate: LocalDate,
    val selectedBlock: TimeBlockUiModel?,
    val selectedBlockId: String?,
    val compactMode: Boolean,
    val compactWindowStart: Int,
    val hapticCue: PlannerHapticCue,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val review: DailyReview,
    val privacyMode: PrivacyMode,
    val previewOnDeviceModel: Boolean,
    val suggestedBlocks: List<TimeBlockUiModel>,
    val aiPlanResult: String?,
    val explainPlan: String?,
    val explainPlanSource: AssistGenAiSource?,
    val repairPlanResult: String?,
    val aiPlanGoalPrefill: String?,
    val aiPlanSuggestedGoals: List<String>,
    val isGenerating: Boolean,
    val genAiRuntimeStatus: GenAiRuntimeStatus,
    val manualMissedIds: Set<String>,
    val focusSession: FocusExecutionState,
    val focusedBlock: TimeBlockUiModel?,
    val reminderScheduleStatus: String,
    val medicationReliabilityStatus: String,
    val calendarConnectionState: CalendarConnectionState,
    val dataExportState: DataExportState,
    val focusRestoredMessage: String?,
    val missedFromFocusMessage: String?,
    val moodEnergyCheckIns: List<MoodEnergyCheckIn>,
    val moodCheckInCoaching: AssistNarrative?,
    val insightsTabState: InsightsTabUiState
)

@Composable
internal fun rememberDayDialViewModelState(viewModel: DayDialViewModel): DayDialViewModelState {
    val timeBlocks by viewModel.timeBlocks.collectAsStateWithLifecycle()
    val freeTime by viewModel.freeTime.collectAsStateWithLifecycle()
    val currentMinute by viewModel.currentMinute.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectedBlock by viewModel.selectedBlock.collectAsStateWithLifecycle()
    val selectedBlockId by viewModel.selectedBlockId.collectAsStateWithLifecycle()
    val compactMode by viewModel.compactMode.collectAsStateWithLifecycle()
    val compactWindowStart by viewModel.compactWindowStart.collectAsStateWithLifecycle()
    val hapticCue by viewModel.hapticCue.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val review by viewModel.dailyReview.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    val previewOnDeviceModel by viewModel.previewOnDeviceModel.collectAsStateWithLifecycle()
    val suggestedBlocks by viewModel.suggestedBlocks.collectAsStateWithLifecycle()
    val aiPlanResult by viewModel.aiPlanResult.collectAsStateWithLifecycle()
    val explainPlan by viewModel.explainPlan.collectAsStateWithLifecycle()
    val explainPlanSource by viewModel.explainPlanSource.collectAsStateWithLifecycle()
    val repairPlanResult by viewModel.repairPlanResult.collectAsStateWithLifecycle()
    val aiPlanGoalPrefill by viewModel.aiPlanGoalPrefill.collectAsStateWithLifecycle()
    val aiPlanSuggestedGoals by viewModel.aiPlanSuggestedGoals.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val genAiRuntimeStatus by viewModel.genAiRuntimeStatus.collectAsStateWithLifecycle()
    val manualMissedIds by viewModel.manualMissedBlockIds.collectAsStateWithLifecycle()
    val focusSession by viewModel.focusExecutionState.collectAsStateWithLifecycle()
    val focusedBlock by viewModel.selectedFocusBlock.collectAsStateWithLifecycle()
    val reminderScheduleStatus by viewModel.reminderScheduleStatus.collectAsStateWithLifecycle()
    val medicationReliabilityStatus by viewModel.medicationReliabilityStatus.collectAsStateWithLifecycle()
    val calendarConnectionState by viewModel.calendarConnectionState.collectAsStateWithLifecycle()
    val dataExportState by viewModel.dataExportState.collectAsStateWithLifecycle()
    val focusRestoredMessage by viewModel.focusRestoredMessage.collectAsStateWithLifecycle()
    val missedFromFocusMessage by viewModel.missedFromFocusMessage.collectAsStateWithLifecycle()
    val moodEnergyCheckIns by viewModel.moodEnergyCheckIns.collectAsStateWithLifecycle()
    val moodCheckInCoaching by viewModel.moodCheckInCoaching.collectAsStateWithLifecycle()
    val insightsTabState by viewModel.insightsTabState.collectAsStateWithLifecycle()

    return DayDialViewModelState(
        timeBlocks = timeBlocks,
        freeTime = freeTime,
        currentMinute = currentMinute,
        selectedDate = selectedDate,
        selectedBlock = selectedBlock,
        selectedBlockId = selectedBlockId,
        compactMode = compactMode,
        compactWindowStart = compactWindowStart,
        hapticCue = hapticCue,
        canUndo = canUndo,
        canRedo = canRedo,
        review = review,
        privacyMode = privacyMode,
        previewOnDeviceModel = previewOnDeviceModel,
        suggestedBlocks = suggestedBlocks,
        aiPlanResult = aiPlanResult,
        explainPlan = explainPlan,
        explainPlanSource = explainPlanSource,
        repairPlanResult = repairPlanResult,
        aiPlanGoalPrefill = aiPlanGoalPrefill,
        aiPlanSuggestedGoals = aiPlanSuggestedGoals,
        isGenerating = isGenerating,
        genAiRuntimeStatus = genAiRuntimeStatus,
        manualMissedIds = manualMissedIds,
        focusSession = focusSession,
        focusedBlock = focusedBlock,
        reminderScheduleStatus = reminderScheduleStatus,
        medicationReliabilityStatus = medicationReliabilityStatus,
        calendarConnectionState = calendarConnectionState,
        dataExportState = dataExportState,
        focusRestoredMessage = focusRestoredMessage,
        missedFromFocusMessage = missedFromFocusMessage,
        moodEnergyCheckIns = moodEnergyCheckIns,
        moodCheckInCoaching = moodCheckInCoaching,
        insightsTabState = insightsTabState
    )
}

@Stable
internal class DayDialScreenUiState(
    initialTab: DayDialTab = DayDialTab.TODAY
) {
    var currentTab by mutableStateOf(initialTab)
    var activeSidebarPage by mutableStateOf<SidebarPage?>(null)
    var activeSheet by mutableStateOf<SheetTarget?>(null)
    var snackbarMessage by mutableStateOf<String?>(null)
    var focusTick by mutableStateOf(0)
}

@Composable
internal fun rememberDayDialScreenUiState(): DayDialScreenUiState {
    var savedTab by rememberSaveable { mutableStateOf(DayDialTab.TODAY) }
    val initialTab = savedTab.takeIf { it in DayDialTab.primary || it == DayDialTab.INSIGHTS } ?: DayDialTab.TODAY
    val state = remember { DayDialScreenUiState(initialTab) }
    if (state.currentTab !in DayDialTab.primary && state.currentTab != DayDialTab.INSIGHTS) {
        state.currentTab = DayDialTab.TODAY
    }
    savedTab = state.currentTab
    return state
}

@Stable
internal class DayDialSettingsState(
    private val planningStyleState: androidx.compose.runtime.MutableState<String>,
    private val showRingGuideState: androidx.compose.runtime.MutableState<Boolean>,
    private val protectFocusBlocksState: androidx.compose.runtime.MutableState<Boolean>,
    private val addBreaksAutomaticallyState: androidx.compose.runtime.MutableState<Boolean>,
    private val preserveManualBlocksState: androidx.compose.runtime.MutableState<Boolean>,
    private val syncCloudState: androidx.compose.runtime.MutableState<Boolean>,
    private val blockStartRemindersState: androidx.compose.runtime.MutableState<Boolean>,
    private val breakRemindersState: androidx.compose.runtime.MutableState<Boolean>,
    private val missedAlertsState: androidx.compose.runtime.MutableState<Boolean>,
    private val endDayReviewReminderState: androidx.compose.runtime.MutableState<Boolean>,
    private val sleepScheduleEnabledState: androidx.compose.runtime.MutableState<Boolean>,
    private val sleepScheduleStartMinuteState: androidx.compose.runtime.MutableState<Int>,
    private val sleepScheduleEndMinuteState: androidx.compose.runtime.MutableState<Int>,
    private val dynamicColorEnabledState: androidx.compose.runtime.MutableState<Boolean>,
    private val glassSurfacesEnabledState: androidx.compose.runtime.MutableState<Boolean>,
    private val reduceMotionEnabledState: androidx.compose.runtime.MutableState<Boolean>,
    private val highContrastEnabledState: androidx.compose.runtime.MutableState<Boolean>,
    private val appearanceModeValueState: androidx.compose.runtime.MutableState<String>,
    private val backdropThemeValueState: androidx.compose.runtime.MutableState<String>,
    private val habitsFeatureEnabledState: androidx.compose.runtime.MutableState<Boolean>,
    private val medicationFeatureEnabledState: androidx.compose.runtime.MutableState<Boolean>,
    private val reviewFeatureEnabledState: androidx.compose.runtime.MutableState<Boolean>,
    private val aiAdvisorFeatureEnabledState: androidx.compose.runtime.MutableState<Boolean>,
    private val syncStatusState: androidx.compose.runtime.MutableState<String>
) {
    var planningStyle by planningStyleState
    var showRingGuide by showRingGuideState
    var protectFocusBlocks by protectFocusBlocksState
    var addBreaksAutomatically by addBreaksAutomaticallyState
    var preserveManualBlocks by preserveManualBlocksState
    var syncCloud by syncCloudState
    var blockStartReminders by blockStartRemindersState
    var breakReminders by breakRemindersState
    var missedAlerts by missedAlertsState
    var endDayReviewReminder by endDayReviewReminderState
    var sleepScheduleEnabled by sleepScheduleEnabledState
    var sleepScheduleStartMinute by sleepScheduleStartMinuteState
    var sleepScheduleEndMinute by sleepScheduleEndMinuteState
    var dynamicColorEnabled by dynamicColorEnabledState
    var glassSurfacesEnabled by glassSurfacesEnabledState
    var reduceMotionEnabled by reduceMotionEnabledState
    var highContrastEnabled by highContrastEnabledState
    var appearanceModeValue by appearanceModeValueState
    var backdropThemeValue by backdropThemeValueState
    var habitsFeatureEnabled by habitsFeatureEnabledState
    var medicationFeatureEnabled by medicationFeatureEnabledState
    var reviewFeatureEnabled by reviewFeatureEnabledState
    var aiAdvisorFeatureEnabled by aiAdvisorFeatureEnabledState
    var syncStatus by syncStatusState

    val appearanceMode: AppearanceMode
        get() = AppearanceMode.entries.firstOrNull { it.name == appearanceModeValue } ?: AppearanceMode.SYSTEM

    val backdropTheme: ChronosBackdropTheme
        get() = ChronosBackdropTheme.fromStorage(backdropThemeValue)

    val featureFlags: ChronosFeatureFlags
        get() = ChronosFeatureFlags(
            habitsEnabled = habitsFeatureEnabled,
            medicationEnabled = medicationFeatureEnabled,
            reviewEnabled = reviewFeatureEnabled,
            aiAdvisorEnabled = aiAdvisorFeatureEnabled
        )
}

@Composable
internal fun rememberDayDialSettingsState(): DayDialSettingsState {
    return DayDialSettingsState(
        planningStyleState = rememberPersistentString("planning_style", "Balanced"),
        showRingGuideState = rememberPersistentBoolean("show_ring_guide", true),
        protectFocusBlocksState = rememberPersistentBoolean("protect_focus_blocks", true),
        addBreaksAutomaticallyState = rememberPersistentBoolean("add_breaks_automatically", true),
        preserveManualBlocksState = rememberPersistentBoolean("preserve_manual_blocks", true),
        syncCloudState = rememberPersistentBoolean("sync_checkpoints", true),
        blockStartRemindersState = rememberPersistentBoolean(
            DayDialReminderSettingsKeys.BLOCK_START_REMINDERS,
            DayDialReminderSettingsKeys.DEFAULT_BLOCK_START_REMINDERS
        ),
        breakRemindersState = rememberPersistentBoolean(
            DayDialReminderSettingsKeys.BREAK_REMINDERS,
            DayDialReminderSettingsKeys.DEFAULT_BREAK_REMINDERS
        ),
        missedAlertsState = rememberPersistentBoolean(
            DayDialReminderSettingsKeys.MISSED_ALERTS,
            DayDialReminderSettingsKeys.DEFAULT_MISSED_ALERTS
        ),
        endDayReviewReminderState = rememberPersistentBoolean(
            DayDialReminderSettingsKeys.END_DAY_REVIEW_REMINDER,
            DayDialReminderSettingsKeys.DEFAULT_END_DAY_REVIEW_REMINDER
        ),
        sleepScheduleEnabledState = rememberPersistentBoolean(SleepSchedule.KEY_ENABLED, false),
        sleepScheduleStartMinuteState = rememberPersistentInt(
            SleepSchedule.KEY_START_MINUTE,
            SleepSchedule.DEFAULT_START_MINUTE
        ),
        sleepScheduleEndMinuteState = rememberPersistentInt(
            SleepSchedule.KEY_END_MINUTE,
            SleepSchedule.DEFAULT_END_MINUTE
        ),
        dynamicColorEnabledState = rememberPersistentBoolean(ChronosUiSettingsKeys.KEY_DYNAMIC_COLOR, true),
        glassSurfacesEnabledState = rememberPersistentBoolean("glass_surfaces", true),
        reduceMotionEnabledState = rememberPersistentBoolean("reduce_motion", false),
        highContrastEnabledState = rememberPersistentBoolean("high_contrast", false),
        appearanceModeValueState = rememberPersistentString("appearance_mode", AppearanceMode.SYSTEM.name),
        backdropThemeValueState = rememberPersistentString(
            ChronosUiSettingsKeys.KEY_BACKDROP_THEME,
            ChronosBackdropTheme.Default.name
        ),
        habitsFeatureEnabledState = rememberPersistentBoolean(
            ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED,
            true
        ),
        medicationFeatureEnabledState = rememberPersistentBoolean(
            ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED,
            true
        ),
        reviewFeatureEnabledState = rememberPersistentBoolean(
            ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED,
            false
        ),
        aiAdvisorFeatureEnabledState = rememberPersistentBoolean(
            ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED,
            false
        ),
        syncStatusState = rememberPersistentString("sync_status", "No checkpoint this session")
    )
}
