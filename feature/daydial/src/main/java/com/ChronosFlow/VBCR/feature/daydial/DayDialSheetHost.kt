package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.ChronosFlow.VBCR.core.ui.shell.ChronosModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.GenAiRuntimeStatus
import com.ChronosFlow.VBCR.core.domain.diagnostics.AppEventLogEntry
import com.ChronosFlow.VBCR.core.ui.motion.ChronosMotionDefaults
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionDirection
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionFactory
import com.ChronosFlow.VBCR.core.ui.theme.liquidGlass
import com.ChronosFlow.VBCR.feature.daydial.model.AppearanceMode
import com.ChronosFlow.VBCR.feature.daydial.model.SheetTarget

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DayDialSheetHost(
    activeSheet: SheetTarget?,
    sheetState: SheetState,
    selectedBlock: TimeBlockUiModel?,
    selectedDate: java.time.LocalDate,
    sortedBlocks: List<TimeBlockUiModel>,
    missedBlocks: List<TimeBlockUiModel>,
    manualMissedBlockIds: Set<String>,
    suggestedBlocks: List<TimeBlockUiModel>,
    review: DailyReview,
    privacyMode: PrivacyMode,
    isGenerating: Boolean,
    genAiRuntimeStatus: GenAiRuntimeStatus,
    aiPlanResult: String?,
    explainPlan: String?,
    explainPlanSource: AssistGenAiSource?,
    repairPlanResult: String? = null,
    aiPlanGoalPrefill: String? = null,
    aiPlanSuggestedGoals: List<String> = emptyList(),
    focusElapsedSeconds: Long,
    focusRemainingSeconds: Long,
    focusSessionActive: Boolean,
    currentMinuteOfDay: Int,
    syncStatus: String,
    protectFocusBlocks: Boolean,
    onProtectFocusChanged: (Boolean) -> Unit,
    addBreaksAutomatically: Boolean,
    onAddBreaksAutomaticallyChanged: (Boolean) -> Unit,
    keepScreenOnDuringFocus: Boolean,
    onKeepScreenOnDuringFocusChanged: (Boolean) -> Unit,
    dailyFocusGoalMinutes: Int,
    onDailyFocusGoalMinutesChanged: (Int) -> Unit,
    defaultFocusBreakPreset: Int,
    onDefaultFocusBreakPresetChanged: (Int) -> Unit,
    blockStartReminders: Boolean,
    onBlockStartRemindersChanged: (Boolean) -> Unit,
    breakReminders: Boolean,
    onBreakRemindersChanged: (Boolean) -> Unit,
    missedAlerts: Boolean,
    onMissedAlertsChanged: (Boolean) -> Unit,
    endDayReviewReminder: Boolean,
    onEndDayReviewReminderChanged: (Boolean) -> Unit,
    notificationsReady: Boolean,
    onRequestNotificationPermission: () -> Unit,
    reminderScheduleStatus: String,
    medicationReliabilityStatus: String,
    dynamicColorEnabled: Boolean,
    glassSurfacesEnabled: Boolean,
    appearanceMode: AppearanceMode,
    reduceMotionEnabled: Boolean,
    highContrastEnabled: Boolean,
    appEventLog: List<AppEventLogEntry>,
    onClearLogs: () -> Unit,
    calendarPermissionStatus: CalendarPermissionStatus,
    showCalendarPermissionRationale: Boolean,
    calendarConnectionState: CalendarConnectionState,
    onDismiss: () -> Unit,
    onDismissCalendarPermissionRationale: () -> Unit,
    onDeleteSelectedBlock: () -> Unit,
    onStartFocus: (String) -> Unit,
    onAdjustFocus: (Int) -> Unit,
    onCreateBlock: (title: String, startMinute: Int, durationMinutes: Int, category: String) -> Unit,
    onGeneratePlan: (List<String>) -> Unit,
    onApplyAiSuggestions: () -> Unit,
    onDismissAiSuggestions: () -> Unit,
    onAcceptAiSuggestion: (String) -> Unit,
    onRejectAiSuggestion: (String) -> Unit,
    onModifyAiSuggestion: (suggestionId: String, title: String, startMinute: Int, durationMinutes: Int) -> Unit,
    onMarkComplete: (String) -> Unit,
    onMarkMissed: (String) -> Unit,
    onUndoMissed: (String) -> Unit,
    onDuplicateBlock: (String) -> Unit,
    onUpdateBlockDetails: (
        blockId: String,
        title: String,
        startMinute: Int,
        durationMinutes: Int,
        category: String,
        isLocked: Boolean,
        isProtected: Boolean
    ) -> Unit,
    onExportBlockToCalendar: (String) -> Unit,
    onRefreshCalendarExport: (String) -> Unit,
    onRemoveCalendarExport: (String) -> Unit,
    onOpenCalendarSettings: () -> Unit,
    dataExportState: DataExportState,
    onCreateDataExport: () -> Unit,
    onImportBackup: (String) -> Unit,
    onFinishFocus: (String) -> Unit,
    onEndDay: () -> Unit,
    showMessage: (String) -> Unit,
    sleepTrack: com.ChronosFlow.VBCR.core.domain.model.SleepTrack? = null,
    onSaveSleep: (
        date: java.time.LocalDate,
        quality: Int,
        startMinute: Int?,
        endMinute: Int?,
        interruptions: Int,
        notes: String?,
        refreshed: Int?
    ) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    val resolvedActiveSheet = resolveDayDialSheetTarget(
        activeSheet = activeSheet,
        selectedBlockId = selectedBlock?.id
    )

    LaunchedEffect(activeSheet, resolvedActiveSheet) {
        if (activeSheet != null && resolvedActiveSheet == null) {
            onDismiss()
        }
    }

    if (resolvedActiveSheet == null) return

    LaunchedEffect(resolvedActiveSheet) {
        sheetState.expand()
    }

    ChronosModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        chromeTag = "day-sheet-modal",
        containerColor = Color.Transparent,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        val sheetContentTransition = if (reduceMotionEnabled) {
            ChronosTransitionFactory.fadeScale(
                durationMillis = ChronosMotionDefaults.ReducedDurationMillis,
                easing = ChronosMotionDefaults.MaterialStandardEasing,
                direction = ChronosTransitionDirection.Neutral,
                enterScale = 1f,
                exitScale = 1f
            )
        } else {
            ChronosTransitionFactory.materialSharedAxis(
                durationMillis = ChronosMotionDefaults.DefaultDurationMillis,
                easing = ChronosMotionDefaults.MaterialStandardEasing,
                direction = ChronosTransitionDirection.Forward,
                slideFraction = ChronosMotionDefaults.SharedAxisSlideFraction,
                enterScale = ChronosMotionDefaults.SharedAxisEnterScale,
                exitScale = ChronosMotionDefaults.SharedAxisExitScale
            )
        }
        AnimatedContent(
            targetState = resolvedActiveSheet,
            transitionSpec = { sheetContentTransition.asContentTransform() },
            label = "dayDialSheetContent"
        ) { sheetTarget ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .liquidGlass(cornerRadius = 32.dp)
            ) {
                SheetContent(
                    target = sheetTarget,
                    selectedBlock = selectedBlock,
                    selectedDate = selectedDate,
                    timeBlocks = sortedBlocks,
                    missedBlocks = missedBlocks,
                    manualMissedBlockIds = manualMissedBlockIds,
                    suggestedBlocks = suggestedBlocks,
                    review = review,
                    privacyMode = privacyMode,
                    isGenerating = isGenerating,
                    genAiRuntimeStatus = genAiRuntimeStatus,
                    aiPlanResult = aiPlanResult,
                    explainPlan = explainPlan,
                    explainPlanSource = explainPlanSource,
                    repairPlanResult = repairPlanResult,
                    aiPlanGoalPrefill = aiPlanGoalPrefill,
                    aiPlanSuggestedGoals = aiPlanSuggestedGoals,
                    focusElapsedSeconds = focusElapsedSeconds,
                    focusRemainingSeconds = focusRemainingSeconds,
                    focusSessionActive = focusSessionActive,
                    currentMinuteOfDay = currentMinuteOfDay,
                    syncStatus = syncStatus,
                    protectFocusBlocks = protectFocusBlocks,
                    onProtectFocusChanged = onProtectFocusChanged,
                    addBreaksAutomatically = addBreaksAutomatically,
                    onAddBreaksAutomaticallyChanged = onAddBreaksAutomaticallyChanged,
                    keepScreenOnDuringFocus = keepScreenOnDuringFocus,
                    onKeepScreenOnDuringFocusChanged = onKeepScreenOnDuringFocusChanged,
                    dailyFocusGoalMinutes = dailyFocusGoalMinutes,
                    onDailyFocusGoalMinutesChanged = onDailyFocusGoalMinutesChanged,
                    defaultFocusBreakPreset = defaultFocusBreakPreset,
                    onDefaultFocusBreakPresetChanged = onDefaultFocusBreakPresetChanged,
                    blockStartReminders = blockStartReminders,
                    onBlockStartRemindersChanged = onBlockStartRemindersChanged,
                    breakReminders = breakReminders,
                    onBreakRemindersChanged = onBreakRemindersChanged,
                    missedAlerts = missedAlerts,
                    onMissedAlertsChanged = onMissedAlertsChanged,
                    endDayReviewReminder = endDayReviewReminder,
                    onEndDayReviewReminderChanged = onEndDayReviewReminderChanged,
                    notificationsReady = notificationsReady,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    reminderScheduleStatus = reminderScheduleStatus,
                    medicationReliabilityStatus = medicationReliabilityStatus,
                    dynamicColorEnabled = dynamicColorEnabled,
                    glassSurfacesEnabled = glassSurfacesEnabled,
                    appearanceMode = appearanceMode,
                    reduceMotionEnabled = reduceMotionEnabled,
                    highContrastEnabled = highContrastEnabled,
                    appEventLog = appEventLog,
                    onClearLogs = onClearLogs,
                    calendarPermissionStatus = calendarPermissionStatus,
                    showCalendarPermissionRationale = showCalendarPermissionRationale,
                    calendarConnectionState = calendarConnectionState,
                    onDismiss = onDismiss,
                    onDismissCalendarPermissionRationale = onDismissCalendarPermissionRationale,
                    onDeleteBlock = onDeleteSelectedBlock,
                    onStartFocus = onStartFocus,
                    onAdjustFocus = onAdjustFocus,
                    onCreateBlock = onCreateBlock,
                    onGeneratePlan = onGeneratePlan,
                    onApplyAiSuggestions = onApplyAiSuggestions,
                    onDismissAiSuggestions = onDismissAiSuggestions,
                    onAcceptAiSuggestion = onAcceptAiSuggestion,
                    onRejectAiSuggestion = onRejectAiSuggestion,
                    onModifyAiSuggestion = onModifyAiSuggestion,
                    onMarkComplete = onMarkComplete,
                    onMarkMissed = onMarkMissed,
                    onUndoMissed = onUndoMissed,
                    onDuplicateBlock = onDuplicateBlock,
                    onUpdateBlockDetails = onUpdateBlockDetails,
                    onExportBlockToCalendar = onExportBlockToCalendar,
                    onRefreshCalendarExport = onRefreshCalendarExport,
                    onRemoveCalendarExport = onRemoveCalendarExport,
                    onOpenCalendarSettings = onOpenCalendarSettings,
                    dataExportState = dataExportState,
                    onCreateDataExport = onCreateDataExport,
                    onImportBackup = onImportBackup,
                    onFinishFocus = onFinishFocus,
                    onEndDay = onEndDay,
                    showMessage = showMessage,
                    sleepTrack = sleepTrack,
                    onSaveSleep = onSaveSleep
                )
            }
        }
    }
}

internal fun resolveDayDialSheetTarget(
    activeSheet: SheetTarget?,
    selectedBlockId: String?
): SheetTarget? {
    return when (activeSheet) {
        is SheetTarget.BlockEditor -> activeSheet.takeIf { it.blockId == selectedBlockId }
        else -> activeSheet
    }
}
