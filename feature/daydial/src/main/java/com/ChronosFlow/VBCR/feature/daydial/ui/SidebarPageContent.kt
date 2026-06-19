package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.core.notifications.NotificationPermissions
import com.ChronosFlow.VBCR.core.ui.components.ChronosIconButton

import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilledTonalButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.ChronosFlow.VBCR.core.ui.motion.chronosHapticClick
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsSuggest
import com.ChronosFlow.VBCR.core.ui.components.ChronosCheckbox
import com.ChronosFlow.VBCR.core.ui.components.ChronosCollapsibleSection
import com.ChronosFlow.VBCR.core.ui.components.ChronosSegmentedButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosDropdownMenuItem
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilterChip
import com.ChronosFlow.VBCR.core.ui.components.ChronosFlowLogo
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosMetricTile
import com.ChronosFlow.VBCR.core.ui.components.ChronosSectionTitle
import com.ChronosFlow.VBCR.core.ui.components.ChronosWarningBanner
import com.ChronosFlow.VBCR.core.ui.components.formatLastSyncedLabel
import com.ChronosFlow.VBCR.core.domain.wear.WearLinkStatus
import kotlinx.coroutines.launch
import com.ChronosFlow.VBCR.core.ui.theme.ChronosColors
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCopy
import com.ChronosFlow.VBCR.core.ui.components.ChronosTimeWindowControls
import com.ChronosFlow.VBCR.core.ui.components.formatDisplayMinute
import com.ChronosFlow.VBCR.core.ui.components.formatDurationLabel
import com.ChronosFlow.VBCR.core.ui.components.nudgeMinuteText
import com.ChronosFlow.VBCR.core.ui.components.parseFlexibleMinute
import com.ChronosFlow.VBCR.core.ui.settings.ChronosBackdropTheme
import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.rememberPersistentUiBooleanSetting
import com.ChronosFlow.VBCR.feature.daydial.CalendarConnectionState
import com.ChronosFlow.VBCR.feature.daydial.CalendarPermissionStatus
import com.ChronosFlow.VBCR.feature.daydial.DataExportPanel
import com.ChronosFlow.VBCR.feature.daydial.DataExportState
import com.ChronosFlow.VBCR.feature.daydial.DailyReview
import com.ChronosFlow.VBCR.feature.daydial.RoutineCompletionSummary
import com.ChronosFlow.VBCR.feature.daydial.TimeBlockUiModel
import com.ChronosFlow.VBCR.feature.daydial.isAllDayCalendarImport
import com.ChronosFlow.VBCR.feature.daydial.model.DayQuickItemKind
import com.ChronosFlow.VBCR.feature.daydial.model.DayQuickItemUiModel
import com.ChronosFlow.VBCR.feature.daydial.model.DayQuickItemsUiState
import com.ChronosFlow.VBCR.feature.daydial.calendarConnectionStatusMessage
import com.ChronosFlow.VBCR.feature.daydial.model.AppearanceMode
import com.ChronosFlow.VBCR.feature.daydial.delegate.AppLockSettingsState
import com.ChronosFlow.VBCR.feature.daydial.model.SidebarPage
import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlueprint
import com.ChronosFlow.VBCR.feature.daydial.rememberPersistentBoolean
import com.ChronosFlow.VBCR.feature.daydial.rememberPersistentString
import com.ChronosFlow.VBCR.core.ui.theme.categoryColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal enum class DeveloperFeatureFlag {
    HABITS,
    GOALS,
    MEDICATION,
    REVIEW,
    JOURNAL,
    SLEEP,
    AI_ADVISOR
}

internal data class DeveloperFeatureFlagRow(
    val flag: DeveloperFeatureFlag,
    val label: String,
    val enabled: Boolean
)

internal fun developerFeatureFlagRows(featureFlags: ChronosFeatureFlags): List<DeveloperFeatureFlagRow> =
    listOf(
        DeveloperFeatureFlagRow(DeveloperFeatureFlag.HABITS, "Habits page", featureFlags.habitsEnabled),
        DeveloperFeatureFlagRow(DeveloperFeatureFlag.GOALS, "Goals page", featureFlags.goalsEnabled),
        DeveloperFeatureFlagRow(DeveloperFeatureFlag.MEDICATION, "Meds page", featureFlags.medicationEnabled),
        DeveloperFeatureFlagRow(DeveloperFeatureFlag.REVIEW, "Review page", featureFlags.reviewEnabled),
        DeveloperFeatureFlagRow(DeveloperFeatureFlag.JOURNAL, "Journal entry", featureFlags.journalEnabled),
        DeveloperFeatureFlagRow(DeveloperFeatureFlag.SLEEP, "Sleep log", featureFlags.sleepEnabled),
        DeveloperFeatureFlagRow(DeveloperFeatureFlag.AI_ADVISOR, "AI advisor", featureFlags.aiAdvisorEnabled)
    )

internal fun aiSettingsGeneratePlanActionLabel(aiAdvisorEnabled: Boolean): String =
    if (aiAdvisorEnabled) "Generate plan" else "AI advisor disabled"

internal val SidebarPageLayoutHorizontalPadding = 16.dp
private val SidebarPageLayoutVerticalSpacing = 12.dp

internal fun sidebarPageLayoutBottomPadding(contentBottomPadding: Dp): Dp =
    dayDialScrollableBottomPadding(
        contentBottomPadding = contentBottomPadding,
        pageBottomPadding = SidebarPageLayoutHorizontalPadding
    )

// Pages scroll full-bleed under the floating glass top bar, so the bar inset
// becomes scroll padding instead of a hard layout edge.
internal fun sidebarPageLayoutTopPadding(contentTopPadding: Dp): Dp =
    contentTopPadding + SidebarPageLayoutHorizontalPadding

// Each swatch echoes the theme's actual palette so the picker previews the
// gradient feel instead of a single flat color.
internal fun backdropThemeSwatchBrush(theme: ChronosBackdropTheme): Brush =
    Brush.horizontalGradient(backdropThemeSwatchColors(theme))

internal fun backdropThemeSwatchColors(theme: ChronosBackdropTheme): List<Color> =
    when (theme) {
        ChronosBackdropTheme.LIQUID -> listOf(ChronosColors.BackdropLiquidStart, ChronosColors.BackdropLiquidEnd)
        ChronosBackdropTheme.SMOKE -> listOf(ChronosColors.BackdropSmokeStart, ChronosColors.BackdropSmokeEnd)
        ChronosBackdropTheme.WATER_DROPS -> listOf(ChronosColors.BackdropWaterStart, ChronosColors.BackdropWaterEnd)
        ChronosBackdropTheme.AURORA -> listOf(ChronosColors.BackdropAuroraStart, ChronosColors.BackdropAuroraEnd)
        ChronosBackdropTheme.SUNSET_GLOW -> listOf(ChronosColors.BackdropSunsetStart, ChronosColors.BackdropSunsetEnd)
        ChronosBackdropTheme.NEBULA -> listOf(ChronosColors.BackdropNebulaStart, ChronosColors.BackdropNebulaEnd)
        ChronosBackdropTheme.MINIMAL -> listOf(ChronosColors.BackdropMinimalStart, ChronosColors.BackdropMinimalEnd)
    }

@Composable
internal fun SidebarPageContent(
    page: SidebarPage,
    selectedDate: LocalDate,
    timeBlocks: List<TimeBlockUiModel>,
    templates: List<TemplateBlueprint>,
    missedBlocks: List<TimeBlockUiModel>,
    review: DailyReview,
    privacyMode: PrivacyMode,
    previewOnDeviceModel: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onPrivacyModeSelected: (PrivacyMode) -> Unit,
    onPreviewOnDeviceModelChanged: (Boolean) -> Unit,
    planningStyle: String,
    onPlanningStyleSelected: (String) -> Unit,
    protectFocusBlocks: Boolean,
    onProtectFocusChanged: (Boolean) -> Unit,
    addBreaksAutomatically: Boolean,
    onAddBreaksAutomaticallyChanged: (Boolean) -> Unit,
    preserveManualBlocks: Boolean,
    onPreserveManualBlocksChanged: (Boolean) -> Unit,
    onGeneratePlan: () -> Unit,
    onRebalance: () -> Unit,
    onFillGaps: () -> Unit,
    onClearDay: () -> Unit,
    onRestorePrevious: () -> Unit,
    onCopyPlan: () -> Unit,
    onSaveTemplate: () -> Unit,
    onApplyTemplate: (TemplateBlueprint) -> Unit,
    onApplyTemplateToday: (TemplateBlueprint) -> Unit = {},
    onApplyTemplateTomorrow: (TemplateBlueprint) -> Unit = {},
    routineCompletionFor: (TemplateBlueprint) -> RoutineCompletionSummary? = { null },
    onEditTemplate: (TemplateBlueprint) -> Unit,
    onDuplicateTemplate: (TemplateBlueprint) -> Unit,
    onSaveCurrentAsTemplate: () -> Unit,
    onOpenPlannedBreakdown: () -> Unit,
    onOpenActualLog: () -> Unit,
    onOpenMissedRecovery: () -> Unit,
    onOpenMissed: () -> Unit,
    onOpenAiPlan: () -> Unit,
    syncCloud: Boolean,
    syncStatus: String,
    onSyncCloudChanged: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    blockStartReminders: Boolean,
    onBlockStartRemindersChanged: (Boolean) -> Unit,
    breakReminders: Boolean,
    onBreakRemindersChanged: (Boolean) -> Unit,
    missedAlerts: Boolean,
    onMissedAlertsChanged: (Boolean) -> Unit,
    endDayReviewReminder: Boolean,
    onEndDayReviewReminderChanged: (Boolean) -> Unit,
    sleepJournalLogReminder: Boolean,
    onSleepJournalLogReminderChanged: (Boolean) -> Unit,
    sleepScheduleEnabled: Boolean,
    onSleepScheduleEnabledChanged: (Boolean) -> Unit,
    sleepScheduleStartMinute: Int,
    onSleepScheduleStartMinuteChanged: (Int) -> Unit,
    sleepScheduleEndMinute: Int,
    onSleepScheduleEndMinuteChanged: (Int) -> Unit,
    reminderScheduleStatus: String,
    medicationReliabilityStatus: String,
    onRequestNotificationPermission: () -> Unit,
    calendarPermissionStatus: CalendarPermissionStatus,
    showCalendarPermissionRationale: Boolean,
    onDismissCalendarPermissionRationale: () -> Unit,
    onRequestCalendarSync: () -> Unit,
    onRequestCalendarExportAccess: () -> Unit,
    onOpenCalendarSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    calendarConnectionState: CalendarConnectionState,
    dynamicColorEnabled: Boolean,
    onDynamicColorChanged: (Boolean) -> Unit,
    glassSurfacesEnabled: Boolean,
    onGlassSurfacesChanged: (Boolean) -> Unit,
    appearanceMode: AppearanceMode,
    onAppearanceModeSelected: (AppearanceMode) -> Unit,
    backdropTheme: ChronosBackdropTheme,
    onBackdropThemeSelected: (ChronosBackdropTheme) -> Unit,
    reduceMotionEnabled: Boolean,
    onReduceMotionChanged: (Boolean) -> Unit,
    highContrastEnabled: Boolean,
    onHighContrastChanged: (Boolean) -> Unit,
    featureFlags: ChronosFeatureFlags,
    onHabitsFeatureEnabledChanged: (Boolean) -> Unit,
    onGoalsFeatureEnabledChanged: (Boolean) -> Unit,
    onMedicationFeatureEnabledChanged: (Boolean) -> Unit,
    onReviewFeatureEnabledChanged: (Boolean) -> Unit,
    onJournalFeatureEnabledChanged: (Boolean) -> Unit,
    onSleepFeatureEnabledChanged: (Boolean) -> Unit,
    onAiAdvisorFeatureEnabledChanged: (Boolean) -> Unit,
    onOpenImport: () -> Unit,
    onOpenWeeklySummary: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLogs: () -> Unit,
    selectedBlockId: String?,
    onOpenFocusScreen: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenMedication: () -> Unit,
    onOpenReview: () -> Unit,
    appLockSettings: AppLockSettingsState,
    appLockCanAuthenticate: Boolean,
    onAppLockEnabledChanged: (Boolean) -> Unit,
    onLockOnResumeChanged: (Boolean) -> Unit,
    onRequireAuthMedicationChanged: (Boolean) -> Unit,
    onRequireAuthReviewChanged: (Boolean) -> Unit,
    onRequireAuthDataExportChanged: (Boolean) -> Unit,
    dataExportRequiresAuth: Boolean,
    dataExportCanAuthenticate: Boolean,
    dataExportAuthError: String?,
    onUnlockDataExport: () -> Unit,
    dataExportState: DataExportState,
    onCreateDataExport: () -> Unit,
    onExportBlockToCalendar: (String) -> Unit,
    onRefreshCalendarExport: (String) -> Unit,
    onRemoveCalendarExport: (String) -> Unit,
    quickItems: DayQuickItemsUiState,
    onQuickTaskDone: (String) -> Unit,
    onQuickHabitDone: (String) -> Unit,
    onQuickMedicationTaken: (String, Int?) -> Unit,
    onQuickMedicationMissed: (String, Int?) -> Unit,
    onOpenBlock: (String?) -> Unit,
    contentTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    showMessage: (String) -> Unit
) {
    val backdropMutedText = MaterialTheme.colorScheme.onSurfaceVariant
    var sleepStartText by remember(sleepScheduleStartMinute) {
        mutableStateOf(formatDisplayMinute(sleepScheduleStartMinute))
    }
    var sleepEndText by remember(sleepScheduleEndMinute) {
        mutableStateOf(formatDisplayMinute(sleepScheduleEndMinute))
    }
    val parsedSleepStart = parseFlexibleMinute(sleepStartText)
    val parsedSleepEnd = parseFlexibleMinute(sleepEndText)
    val sleepWindowError = when {
        parsedSleepStart == null || parsedSleepEnd == null -> "Use valid sleep times like 9:00 PM or 07:00."
        parsedSleepStart == parsedSleepEnd -> "Sleep start and wake time must be different."
        else -> null
    }
    SidebarPageLayout(
        page = page,
        contentTopPadding = contentTopPadding,
        contentBottomPadding = contentBottomPadding
    ) {
            // Filter choices persist across sessions; "connected only" stays session
            // state because it tracks the live permission grant.
            var hideCompletedItems by rememberPersistentBoolean("calendar.hideCompleted", true)
            var connectedSourcesOnly by rememberSaveable { mutableStateOf(false) }
            var didAutoApplyConnectedFilter by rememberSaveable { mutableStateOf(false) }
            var visibleTimelineSourcesValue by rememberPersistentString(
                "calendar.timelineSources",
                CalendarTimelineSourceLabels.joinToString(",")
            )
            val visibleTimelineSources = remember(visibleTimelineSourcesValue) {
                parseTimelineSources(visibleTimelineSourcesValue)
            }
            val setVisibleTimelineSources: (Set<String>) -> Unit = { sources ->
                visibleTimelineSourcesValue =
                    CalendarTimelineSourceLabels.filter { it in sources }.joinToString(",")
            }
            LaunchedEffect(calendarPermissionStatus.readGranted) {
                if (!calendarPermissionStatus.readGranted && connectedSourcesOnly) {
                    connectedSourcesOnly = false
                }

                if (!didAutoApplyConnectedFilter && calendarPermissionStatus.readGranted) {
                    connectedSourcesOnly = true
                    didAutoApplyConnectedFilter = true
                }

                if (!calendarPermissionStatus.readGranted) {
                    didAutoApplyConnectedFilter = false
                }
            }
            when (page) {
                SidebarPage.DAY_TOOLS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChronosMetricTile("Blocks", timeBlocks.size.toString(), Modifier.width(140.dp))
                        ChronosMetricTile(
                            "Missed",
                            missedBlocks.size.toString(),
                            Modifier.width(140.dp),
                            accent = MaterialTheme.colorScheme.error,
                            onClick = onOpenMissed
                        )
                        ChronosMetricTile(
                            "Planned",
                            formatMinutesToLabel(review.plannedMinutes),
                            Modifier.width(140.dp),
                            onClick = onOpenReview
                        )
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChronosSectionTitle(title = "Plan", subtitle = "Shape today's schedule")
                            if (featureFlags.aiAdvisorEnabled) {
                                ChronosButton(onClick = onGeneratePlan, modifier = Modifier.fillMaxWidth()) {
                                    Text("Generate Plan")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChronosFilledTonalButton(onClick = onFillGaps, modifier = Modifier.weight(1f)) {
                                    Text("Fill gaps")
                                }
                                ChronosFilledTonalButton(onClick = onRebalance, modifier = Modifier.weight(1f)) {
                                    Text("Rebalance")
                                }
                            }
                        }
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChronosSectionTitle(title = "Manage", subtitle = "Templates, recovery, and review")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChronosOutlinedButton(onClick = onSaveTemplate, modifier = Modifier.weight(1f)) {
                                    Text("Save template")
                                }
                                ChronosOutlinedButton(onClick = onRestorePrevious, modifier = Modifier.weight(1f)) {
                                    Text("Restore previous")
                                }
                            }
                            ChronosOutlinedButton(onClick = onOpenMissed, modifier = Modifier.fillMaxWidth()) {
                                Text(dayToolsReviewMissedLabel(missedBlocks.size))
                            }
                        }
                    }
                    // Destructive action sits apart from the routine tools and asks first:
                    // clearCurrentDay bypasses undo history, so this genuinely can't be undone.
                    var showClearDayConfirm by rememberSaveable { mutableStateOf(false) }
                    ChronosOutlinedButton(
                        onClick = { showClearDayConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Clear Day") }
                    if (showClearDayConfirm) {
                        AlertDialog(
                            onDismissRequest = { showClearDayConfirm = false },
                            title = { Text("Clear this day?") },
                            text = { Text(clearDayConfirmationMessage(timeBlocks.size, selectedDate)) },
                            confirmButton = {
                                ChronosTextButton(
                                    onClick = {
                                        showClearDayConfirm = false
                                        onClearDay()
                                    }
                                ) {
                                    Text("Clear day", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                ChronosTextButton(onClick = { showClearDayConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
                SidebarPage.TASKS -> SidebarDirectLaunchEffect(page, onOpenTasks)
                SidebarPage.FOCUS_TIMER -> SidebarDirectLaunchEffect(page, onOpenFocusScreen)
                SidebarPage.HABITS -> SidebarDirectLaunchEffect(page, onOpenHabits)
                SidebarPage.GOALS -> SidebarDirectLaunchEffect(page, onOpenGoals)
                SidebarPage.MEDICATION -> SidebarDirectLaunchEffect(page, onOpenMedication)
                // Review has no in-shell page: the drawer routes it to the Review tab,
                // and no launch target maps here, so this branch is unreachable. Kept as a
                // no-op only to preserve the exhaustive when over SidebarPage.
                SidebarPage.REVIEW -> Unit
                // The full standalone Journal page: history, search, mood strip, AI insights, and the
                // unified composer sheet. Rendered inline in the More section's page surface.
                SidebarPage.JOURNAL -> JournalPageRoute(onMessage = showMessage)
                SidebarPage.TEMPLATES -> {
                    ChronosSectionTitle(title = "Routines", subtitle = "Apply, edit, or duplicate reusable day blueprints")
                    templates.forEach { template ->
                        val completion = routineCompletionFor(template)
                        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Apply today or tomorrow, or seed the current day",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (completion != null && completion.totalCount > 0) {
                                        Text(
                                            routineCompletionLabel(completion.doneCount, completion.totalCount),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChronosTextButton(
                                        onClick = { onApplyTemplateToday(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = routineApplyTodayActionLabel(template)
                                        }
                                    ) { Text("Apply today") }
                                    ChronosTextButton(
                                        onClick = { onApplyTemplateTomorrow(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = routineApplyTomorrowActionLabel(template)
                                        }
                                    ) { Text("Apply tomorrow") }
                                    ChronosTextButton(
                                        onClick = { onApplyTemplate(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = templateApplyActionLabel(template)
                                        }
                                    ) { Text("Seed day") }
                                    ChronosTextButton(
                                        onClick = { onEditTemplate(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = templateEditActionLabel(template)
                                        }
                                    ) { Text("Edit") }
                                    ChronosTextButton(
                                        onClick = { onDuplicateTemplate(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = templateCopyActionLabel(template)
                                        }
                                    ) { Text("Copy") }
                                }
                            }
                        }
                    }
                    ChronosButton(onClick = onSaveTemplate, modifier = Modifier.fillMaxWidth()) { Text("Save current day as routine") }
                }
                SidebarPage.CALENDARS -> {
                    val previousMonthLabel = calendarMonthNavigationLabel(selectedDate, monthOffset = -1)
                    val nextMonthLabel = calendarMonthNavigationLabel(selectedDate, monthOffset = 1)

                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChronosIconButton(onClick = { onSelectDate(selectedDate.minusMonths(1)) }) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = previousMonthLabel)
                                }
                                Text(selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
                                ChronosIconButton(onClick = { onSelectDate(selectedDate.plusMonths(1)) }) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = nextMonthLabel)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            val daysInMonth = selectedDate.lengthOfMonth()
                            val firstDayOfMonth = selectedDate.withDayOfMonth(1).dayOfWeek.value % 7
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                var dayCount = 1
                                for (row in 0..5) {
                                    if (dayCount > daysInMonth) break
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        for (col in 0..6) {
                                            if ((row == 0 && col < firstDayOfMonth) || dayCount > daysInMonth) {
                                                Box(Modifier.size(32.dp))
                                            } else {
                                                val day = dayCount
                                                val date = selectedDate.withDayOfMonth(day)
                                                val isSelected = selectedDate.dayOfMonth == day
                                                val dayLabel = calendarDaySelectionLabel(date, isSelected)
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                            RoundedCornerShape(16.dp)
                                                        )
                                                        .semantics {
                                                            contentDescription = dayLabel
                                                        }
                                                        .chronosHapticClick(
                                                            onClick = { onSelectDate(date) },
                                                            onClickLabel = calendarDaySelectionLabel(date, isSelected = false),
                                                            role = Role.Button
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        day.toString(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isSelected) {
                                                            MaterialTheme.colorScheme.onPrimary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )
                                                }
                                                dayCount++
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            DailyReviewHeader(
                                review = review,
                                onPlannedClick = onOpenPlannedBreakdown,
                                onActualClick = onOpenActualLog,
                                onMissedClick = onOpenMissedRecovery,
                                onOpenReview = onOpenReview,
                                showReviewAction = featureFlags.reviewEnabled
                            )
                        }
                    }
                    val exportAccessPermanentlyDenied =
                        calendarPermissionStatus.readGranted && calendarPermissionStatus.permanentlyDenied
                    val exportAccessNeedsRationale =
                        calendarPermissionStatus.readGranted &&
                            !calendarPermissionStatus.writeGranted &&
                            showCalendarPermissionRationale
                    val connectionLabel = when {
                        calendarPermissionStatus.allGranted -> "Imports and exports connected"
                        exportAccessPermanentlyDenied -> "Imports connected; exports off"
                        calendarPermissionStatus.readGranted -> "Imports connected; exports need write access"
                        else -> "Not connected"
                    }
                    // One status line by default; permission plumbing expands on demand
                    // (and automatically while a permission rationale is showing).
                    var calendarSyncExpanded by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(showCalendarPermissionRationale) {
                        if (showCalendarPermissionRationale) calendarSyncExpanded = true
                    }
                    ChronosCollapsibleSection(
                        title = "Calendar sync",
                        summary = connectionLabel,
                        expanded = calendarSyncExpanded,
                        onExpandedChange = { calendarSyncExpanded = it }
                    ) {
                            Text(
                                calendarConnectionStatusMessage(calendarPermissionStatus, calendarConnectionState),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (calendarConnectionState.isWorking) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    backdropMutedText
                                }
                            )
                            when {
                                calendarPermissionStatus.allGranted -> {
                                    Text(
                                        "Calendar connected. DayDial can refresh device events and keep exported blocks updated.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = backdropMutedText
                                    )
                                    ActionGrid(
                                        listOf(
                                            "Refresh Device Events" to onRequestCalendarSync,
                                            "Open Settings" to onOpenCalendarSettings
                                        )
                                    )
                                }
                                exportAccessPermanentlyDenied -> {
                                    Text(
                                        "Calendar imports are connected. Open app settings to re-enable linked exports.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = backdropMutedText
                                    )
                                    ActionGrid(
                                        listOf(
                                            "Refresh Device Events" to onRequestCalendarSync,
                                            "Open Settings" to onOpenCalendarSettings
                                        )
                                    )
                                }
                                exportAccessNeedsRationale -> {
                                    ChronosWarningBanner(
                                        title = "Calendar export access is needed",
                                        message = "Calendar imports are connected. ChronosFlow needs write access to keep exported DayDial blocks linked."
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ChronosButton(onClick = onRequestCalendarExportAccess) {
                                            Text("Continue")
                                        }
                                        ChronosTextButton(onClick = onDismissCalendarPermissionRationale) {
                                            Text("Not now")
                                        }
                                    }
                                }
                                calendarPermissionStatus.readGranted -> {
                                    Text(
                                        "Calendar imports are connected. Enable write access when you want exported DayDial blocks to stay linked.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = backdropMutedText
                                    )
                                    ActionGrid(
                                        listOf(
                                            "Refresh Device Events" to onRequestCalendarSync,
                                            "Enable Exports" to onRequestCalendarExportAccess
                                        )
                                    )
                                }
                                calendarPermissionStatus.permanentlyDenied -> {
                                    ChronosWarningBanner(
                                        title = "Calendar access is off",
                                        message = "Android won't let ChronosFlow import or export calendar items until you re-enable permission in app settings."
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ChronosButton(onClick = onOpenCalendarSettings) {
                                            Text("Open settings")
                                        }
                                    }
                                }
                                showCalendarPermissionRationale -> {
                                    ChronosWarningBanner(
                                        title = "Calendar access is needed",
                                        message = "ChronosFlow reads your device calendar to avoid duplicate plans and writes linked exports so block edits stay in sync."
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ChronosButton(onClick = onRequestCalendarSync) {
                                            Text("Continue")
                                        }
                                        ChronosTextButton(onClick = onDismissCalendarPermissionRationale) {
                                            Text("Not now")
                                        }
                                    }
                                }
                                else -> {
                                    Text(
                                        "Connect your Android calendar to import scheduled events and export linked DayDial blocks.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = backdropMutedText
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ChronosButton(onClick = onRequestCalendarSync) {
                                            Text("Connect calendar")
                                        }
                                        ChronosOutlinedButton(onClick = onOpenCalendarSettings) {
                                            Text("Open settings")
                                        }
                                    }
                                }
                            }
                    }
                    ChronosSectionTitle(
                        title = "Detailed calendar",
                        subtitle = "Calendar, tasks, habits, and medications for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
                    )
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                            val timelineItems = remember(
                                timeBlocks,
                                quickItems,
                                hideCompletedItems,
                                connectedSourcesOnly,
                                visibleTimelineSources,
                                calendarPermissionStatus.readGranted
                            ) {
                                buildSidebarTimelineItems(
                                    timeBlocks = timeBlocks,
                                    quickItems = quickItems,
                                hideCompletedItems = hideCompletedItems,
                                showConnectedCalendarOnly = connectedSourcesOnly,
                                calendarSourcesConnected = calendarPermissionStatus.readGranted,
                                visibleSourceLabels = visibleTimelineSources,
                                onOpenBlock = onOpenBlock,
                                onTaskDone = onQuickTaskDone,
                                onHabitDone = onQuickHabitDone,
                                onMedicationTaken = onQuickMedicationTaken,
                                onMedicationMissed = onQuickMedicationMissed
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                            // Three presets cover the common views; the filter menu keeps
                            // per-source toggles and modifiers for power users.
                            val currentPreset = presetForTimelineSources(visibleTimelineSources)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                    CalendarTimelinePreset.segments.forEachIndexed { index, preset ->
                                        ChronosSegmentedButton(
                                            selected = currentPreset == preset,
                                            onClick = {
                                                timelineSourcesForPreset(preset)?.let(setVisibleTimelineSources)
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = CalendarTimelinePreset.segments.size
                                            ),
                                            label = { Text(preset.label) }
                                        )
                                    }
                                }
                                Box {
                                    var filterMenuOpen by rememberSaveable { mutableStateOf(false) }
                                    ChronosIconButton(
                                        onClick = { filterMenuOpen = true },
                                        modifier = Modifier.semantics {
                                            contentDescription = "Timeline filters"
                                        }
                                    ) {
                                        Icon(Icons.Default.FilterList, contentDescription = null)
                                    }
                                    DropdownMenu(
                                        expanded = filterMenuOpen,
                                        onDismissRequest = { filterMenuOpen = false }
                                    ) {
                                        ChronosDropdownMenuItem(
                                            text = { Text("Hide completed") },
                                            leadingIcon = {
                                                ChronosCheckbox(checked = hideCompletedItems, onCheckedChange = null)
                                            },
                                            onClick = { hideCompletedItems = !hideCompletedItems }
                                        )
                                        ChronosDropdownMenuItem(
                                            text = { Text("Connected calendar only") },
                                            enabled = calendarPermissionStatus.readGranted,
                                            leadingIcon = {
                                                ChronosCheckbox(
                                                    checked = connectedSourcesOnly,
                                                    onCheckedChange = null,
                                                    enabled = calendarPermissionStatus.readGranted
                                                )
                                            },
                                            onClick = {
                                                if (calendarPermissionStatus.readGranted) {
                                                    didAutoApplyConnectedFilter = true
                                                    connectedSourcesOnly = !connectedSourcesOnly
                                                }
                                            }
                                        )
                                        HorizontalDivider()
                                        CalendarTimelineSourceLabels.forEach { source ->
                                            ChronosDropdownMenuItem(
                                                text = { Text(source) },
                                                leadingIcon = {
                                                    ChronosCheckbox(
                                                        checked = source in visibleTimelineSources,
                                                        onCheckedChange = null
                                                    )
                                                },
                                                onClick = {
                                                    setVisibleTimelineSources(
                                                        if (source in visibleTimelineSources) {
                                                            visibleTimelineSources - source
                                                        } else {
                                                            visibleTimelineSources + source
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (timelineItems.isEmpty()) {
                                Text(
                                    if (connectedSourcesOnly) {
                                        "No connected calendar items for this date."
                                    } else if (hideCompletedItems) {
                                        "No timeline items for this date with completed items hidden."
                                    } else if (visibleTimelineSources.isEmpty()) {
                                        "No sections enabled for this timeline."
                                    } else {
                                        "No timeline items for this date."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = backdropMutedText
                                )
                            } else {
                                val sourceCounts = remember(timelineItems) {
                                    timelineItems.groupingBy { it.sourceLabel }.eachCount()
                                }
                                var currentSource: String? = null
                                timelineItems.forEachIndexed { index, item ->
                                    if (currentSource != item.sourceLabel) {
                                        if (index > 0) {
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    currentSource = item.sourceLabel
                                    val count = sourceCounts[item.sourceLabel] ?: 0
                                    Text(
                                        "${
                                            item.sourceLabel
                                        } (${count})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                    if (item.isFirstInSource) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    if (index > 0 && !item.isFirstInSource) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                    SidebarTimelineItemRow(item = item)
                                }
                            }
                        }
                    }
                    Text("Selected Day Actions", style = MaterialTheme.typography.titleSmall)
                    ActionGrid(listOf("Jump to Today" to { onSelectDate(LocalDate.now()) }, "Copy Plan" to onCopyPlan))
                }
                SidebarPage.AI_SETTINGS -> {
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(title = "Privacy", subtitle = "Where AI runs and which model it uses")
                            PrivacyModeSelector(privacyMode, onPrivacyModeSelected)
                            CheckboxSetting(
                                "Use preview Gemini Nano model",
                                previewOnDeviceModel,
                                onPreviewOnDeviceModelChanged
                            )
                            Text(
                                "Requires the AICore Developer Preview and falls back to the stable Gemini Nano model when preview variants are unavailable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = backdropMutedText
                            )
                        }
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(title = "Planning", subtitle = "How AI builds and adjusts your plan")
                            var autoApplyAssist by rememberPersistentUiBooleanSetting(
                                ChronosUiSettingsKeys.KEY_ASSIST_AUTO_APPLY,
                                false
                            )
                            CheckboxSetting(
                                "Auto-apply form suggestions",
                                autoApplyAssist
                            ) { autoApplyAssist = it }
                            Text(
                                "New Task, Habit, and Medication forms fill empty fields from AI suggestions automatically. Fields you have already set are never overwritten.",
                                style = MaterialTheme.typography.bodySmall,
                                color = backdropMutedText
                            )
                            Text("Planning style", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("Strict", "Balanced", "Flexible").forEach { style ->
                                    ChronosFilterChip(
                                        selected = planningStyle == style,
                                        onClick = { onPlanningStyleSelected(style) },
                                        label = { Text(style) }
                                    )
                                }
                            }
                            CheckboxSetting("Protect focus blocks", protectFocusBlocks, onProtectFocusChanged)
                            CheckboxSetting("Add breaks automatically", addBreaksAutomatically, onAddBreaksAutomaticallyChanged)
                            CheckboxSetting("Preserve manual blocks", preserveManualBlocks, onPreserveManualBlocksChanged)
                            ChronosButton(
                                onClick = onOpenAiPlan,
                                enabled = featureFlags.aiAdvisorEnabled,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(aiSettingsGeneratePlanActionLabel(featureFlags.aiAdvisorEnabled))
                            }
                        }
                    }
                }
                SidebarPage.PRIVACY_SYNC -> {
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        AppLockSettingsSection(
                            settings = appLockSettings,
                            canAuthenticate = appLockCanAuthenticate,
                            onAppLockEnabledChanged = onAppLockEnabledChanged,
                            onLockOnResumeChanged = onLockOnResumeChanged,
                            onRequireAuthMedicationChanged = onRequireAuthMedicationChanged,
                            onRequireAuthReviewChanged = onRequireAuthReviewChanged,
                            onRequireAuthDataExportChanged = onRequireAuthDataExportChanged
                        )
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(
                                title = "App permissions",
                                subtitle = "System access ChronosFlow can use"
                            )
                            PrivacyPermissionsSection(
                                calendarPermissionStatus = calendarPermissionStatus,
                                showCalendarPermissionRationale = showCalendarPermissionRationale,
                                onDismissCalendarPermissionRationale = onDismissCalendarPermissionRationale,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onRequestCalendarSync = onRequestCalendarSync,
                                onRequestCalendarExportAccess = onRequestCalendarExportAccess,
                                onOpenExactAlarmSettings = onOpenExactAlarmSettings
                            )
                        }
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(
                                title = "Sensitive content",
                                subtitle = "What shows on your watch and in notifications"
                            )
                            val privacyPreferences = rememberPrivacyPreferences()
                            var hideSensitiveTitles by remember {
                                mutableStateOf(privacyPreferences.redactSensitiveNotifications())
                            }
                            CheckboxSetting("Hide sensitive titles", hideSensitiveTitles) {
                                hideSensitiveTitles = it
                                privacyPreferences.setRedactSensitiveNotifications(it)
                            }
                            Text(
                                "When on, task and event titles are replaced with a generic label on the " +
                                    "paired watch and in focus notifications, and per-item task, habit, and " +
                                    "medication lists are hidden. Off by default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = backdropMutedText
                            )
                        }
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(title = "Cloud sync", subtitle = "Encrypted checkpoints")
                            CheckboxSetting("Sync checkpoints", syncCloud, onSyncCloudChanged)
                            Text("Last checkpoint: $syncStatus", style = MaterialTheme.typography.bodySmall, color = backdropMutedText)
                            ActionGrid(
                                listOf(
                                    "Sync Now" to {
                                        if (syncCloud) onSyncNow() else showMessage("Enable sync checkpoints first")
                                    }
                                )
                            )
                        }
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        WearLinkStatusCard(backdropMutedText = backdropMutedText, showMessage = showMessage)
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(
                                title = "Connected apps",
                                subtitle = "Cross-app task & event sharing with DevTime"
                            )
                            CompanionAppStatusCard()
                        }
                    }
                }
                SidebarPage.NOTIFICATIONS -> {
                    val notificationsContext = LocalContext.current
                    if (blockStartReminders && !canScheduleExactAlarmsCompat(notificationsContext)) {
                        ChronosWarningBanner(
                            title = "Reminders may arrive late",
                            message = "Exact alarms are off, so block reminders can be delayed by up to " +
                                "10 minutes. Allow exact alarms below for on-time reminders."
                        )
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(title = "Notifications", subtitle = "Reminders and quiet hours")
                            CheckboxSetting("Block start reminders", blockStartReminders, onBlockStartRemindersChanged)
                            CheckboxSetting("Break reminders", breakReminders, onBreakRemindersChanged)
                            CheckboxSetting("Missed block alerts", missedAlerts, onMissedAlertsChanged)
                            CheckboxSetting("End-of-day review", endDayReviewReminder, onEndDayReviewReminderChanged)
                            CheckboxSetting(
                                "Log sleep & journal",
                                sleepJournalLogReminder,
                                onSleepJournalLogReminderChanged
                            )
                            var currentBlockLive by rememberPersistentBoolean(
                                "notifications.currentBlockLive",
                                false
                            )
                            CheckboxSetting(
                                "Current block notification",
                                currentBlockLive,
                                onCheckedChange = { currentBlockLive = it }
                            )
                            if (currentBlockLive && !canPostPromotedNotificationsCompat(notificationsContext)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Live updates won't show in the status bar — allow " +
                                            "promoted notifications so the current block appears on the " +
                                            "lock screen and always-on display.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    NotificationPermissions.promotionSettingsIntent(notificationsContext)
                                        ?.let { promotionIntent ->
                                            ChronosTextButton(
                                                onClick = {
                                                    runCatching { notificationsContext.startActivity(promotionIntent) }
                                                }
                                            ) { Text("Allow promoted notifications") }
                                        }
                                }
                            }
                            CheckboxSetting("Sleep schedule", sleepScheduleEnabled, onSleepScheduleEnabledChanged)
                            ChronosTimeWindowControls(
                                startText = sleepStartText,
                                endText = sleepEndText,
                                onStartChange = {
                                    sleepStartText = it
                                    parseFlexibleMinute(it)?.let(onSleepScheduleStartMinuteChanged)
                                },
                                onEndChange = {
                                    sleepEndText = it
                                    parseFlexibleMinute(it)?.let(onSleepScheduleEndMinuteChanged)
                                },
                                parsedStart = parsedSleepStart,
                                parsedEnd = parsedSleepEnd,
                                showFineTuneFields = true,
                                onNudgeStart = { delta ->
                                    sleepStartText = nudgeMinuteText(
                                        currentText = sleepStartText,
                                        deltaMinutes = delta,
                                        fallbackMinute = sleepScheduleStartMinute
                                    )
                                    parseFlexibleMinute(sleepStartText)?.let(onSleepScheduleStartMinuteChanged)
                                },
                                onNudgeEnd = { delta ->
                                    sleepEndText = nudgeMinuteText(
                                        currentText = sleepEndText,
                                        deltaMinutes = delta,
                                        fallbackMinute = sleepScheduleEndMinute
                                    )
                                    parseFlexibleMinute(sleepEndText)?.let(onSleepScheduleEndMinuteChanged)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (sleepWindowError != null) {
                                Text(
                                    sleepWindowError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    if (sleepScheduleEnabled) {
                                        "Sleep window active: ${formatDisplayMinute(sleepScheduleStartMinute)} - ${formatDisplayMinute(sleepScheduleEndMinute)}"
                                    } else {
                                        "Sleep schedule is off."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = backdropMutedText
                                )
                            }
                            Text(reminderScheduleStatus, style = MaterialTheme.typography.bodySmall, color = backdropMutedText)
                            Text(medicationReliabilityStatus, style = MaterialTheme.typography.bodySmall, color = backdropMutedText)
                            ChronosOutlinedButton(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Notifications, contentDescription = null)
                                Text("Notification permission")
                            }
                            ChronosOutlinedButton(onClick = onOpenExactAlarmSettings, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Text("Exact alarm settings")
                            }
                        }
                    }
                }
                SidebarPage.APPEARANCE -> {
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(
                                title = "Theme",
                                subtitle = "Appearance, motion, and dial density"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                AppearanceMode.entries.forEach { mode ->
                                    ChronosFilterChip(
                                        selected = appearanceMode == mode,
                                        onClick = { onAppearanceModeSelected(mode) },
                                        label = { Text(mode.label) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = when (mode) {
                                                    AppearanceMode.SYSTEM -> Icons.Outlined.SettingsSuggest
                                                    AppearanceMode.LIGHT -> Icons.Outlined.LightMode
                                                    AppearanceMode.DARK -> Icons.Outlined.DarkMode
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                            }
                            ChronosSectionTitle(
                                title = "Backdrop",
                                subtitle = "Choose the ambient background style · applies live behind this panel"
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                ChronosBackdropTheme.entries.forEach { theme ->
                                    ChronosFilterChip(
                                        selected = backdropTheme == theme,
                                        onClick = { onBackdropThemeSelected(theme) },
                                        label = { Text(theme.label) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 22.dp, height = 14.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(backdropThemeSwatchBrush(theme))
                                            )
                                        }
                                    )
                                }
                            }
                            CheckboxSetting("Dynamic color", dynamicColorEnabled, onDynamicColorChanged)
                            CheckboxSetting("Glass surfaces", glassSurfacesEnabled, onGlassSurfacesChanged)
                            CheckboxSetting("Reduce motion", reduceMotionEnabled, onReduceMotionChanged)
                            CheckboxSetting("High contrast", highContrastEnabled, onHighContrastChanged)
                            var showRingGuide by rememberPersistentBoolean("show_ring_guide", true)
                            CheckboxSetting("Ring guides", showRingGuide, onCheckedChange = { showRingGuide = it })
                        }
                    }
                }
                SidebarPage.DATA_EXPORT -> {
                    SensitiveSidebarGate(
                        title = "Data export is protected",
                        message = "Unlock to export or import your ChronosFlow data.",
                        requiresAuth = dataExportRequiresAuth,
                        canAuthenticate = dataExportCanAuthenticate,
                        errorMessage = dataExportAuthError,
                        onUnlock = onUnlockDataExport
                    ) {
                        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ChronosSectionTitle(
                                    title = "Data and export",
                                    subtitle = "Create a full local JSON export"
                                )
                                DataExportPanel(
                                    state = dataExportState,
                                    onCreateExport = onCreateDataExport
                                )
                            }
                        }
                        ActionGrid(
                            listOf(
                                "Import Backup" to onOpenImport,
                                "Weekly Summary" to onOpenWeeklySummary,
                                "Save Template" to onSaveCurrentAsTemplate
                            )
                        )
                    }
                }
                SidebarPage.DEVELOPER -> {
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChronosSectionTitle(title = "Developer")
                            Text("AI provider: ${GenAiAssistCopy.privacyModeLabel(privacyMode)}", color = backdropMutedText)
                            Text("Database: Room local store", color = backdropMutedText)
                            Text("Blocks loaded: ${timeBlocks.size}", color = backdropMutedText)
                            ChronosSectionTitle(title = "Feature flags", subtitle = "Control secondary surfaces")
                            developerFeatureFlagRows(featureFlags).forEach { row ->
                                CheckboxSetting(
                                    row.label,
                                    row.enabled,
                                    onCheckedChange = { enabled ->
                                        when (row.flag) {
                                            DeveloperFeatureFlag.HABITS -> onHabitsFeatureEnabledChanged(enabled)
                                            DeveloperFeatureFlag.GOALS -> onGoalsFeatureEnabledChanged(enabled)
                                            DeveloperFeatureFlag.MEDICATION -> onMedicationFeatureEnabledChanged(enabled)
                                            DeveloperFeatureFlag.REVIEW -> onReviewFeatureEnabledChanged(enabled)
                                            DeveloperFeatureFlag.JOURNAL -> onJournalFeatureEnabledChanged(enabled)
                                            DeveloperFeatureFlag.SLEEP -> onSleepFeatureEnabledChanged(enabled)
                                            DeveloperFeatureFlag.AI_ADVISOR -> onAiAdvisorFeatureEnabledChanged(enabled)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    ActionGrid(listOf("View Diagnostics" to onOpenDiagnostics, "View Logs" to onOpenLogs))
                }
                SidebarPage.ABOUT -> {
                    val aboutContext = LocalContext.current
                    val versionLabel = remember(aboutContext) { aboutVersionLabel(aboutContext) }
                    var privacyExpanded by rememberSaveable { mutableStateOf(false) }
                    var termsExpanded by rememberSaveable { mutableStateOf(false) }
                    var licensesExpanded by rememberSaveable { mutableStateOf(false) }

                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChronosFlowLogo(modifier = Modifier.size(56.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("ChronosFlow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Day planner, focus execution, and actual-time insights.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = backdropMutedText
                                )
                                Text(versionLabel, style = MaterialTheme.typography.labelMedium, color = backdropMutedText)
                            }
                        }
                    }

                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(title = "What ChronosFlow is")
                            Text(aboutOverviewText(), style = MaterialTheme.typography.bodyMedium, color = backdropMutedText)
                            ChronosSectionTitle(title = "Your data stays yours", subtitle = "Privacy-first by design")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                aboutPrivacyHighlights().forEach { highlight ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("•", color = backdropMutedText)
                                        Text(highlight, style = MaterialTheme.typography.bodyMedium, color = backdropMutedText)
                                    }
                                }
                            }
                        }
                    }

                    ChronosCollapsibleSection(
                        title = "Privacy Policy",
                        summary = "How ChronosFlow handles your data",
                        expanded = privacyExpanded,
                        onExpandedChange = { privacyExpanded = it }
                    ) {
                        Text(aboutPrivacyPolicyText(), style = MaterialTheme.typography.bodyMedium, color = backdropMutedText)
                    }

                    ChronosCollapsibleSection(
                        title = "Terms of Use",
                        summary = "The agreement for using ChronosFlow",
                        expanded = termsExpanded,
                        onExpandedChange = { termsExpanded = it }
                    ) {
                        Text(aboutTermsText(), style = MaterialTheme.typography.bodyMedium, color = backdropMutedText)
                    }

                    ChronosCollapsibleSection(
                        title = "Open-source licenses",
                        summary = "Libraries that power ChronosFlow",
                        expanded = licensesExpanded,
                        onExpandedChange = { licensesExpanded = it }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            aboutOpenSourceLicenses().forEach { (library, license) ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(library, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(license, style = MaterialTheme.typography.labelMedium, color = backdropMutedText)
                                }
                            }
                        }
                    }

                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(
                                title = "Support & feedback",
                                subtitle = "Questions, bugs, or ideas? Reach out."
                            )
                            ActionGrid(
                                listOf(
                                    "Email support" to {
                                        launchAboutFeedbackEmail(aboutContext, versionLabel, showMessage)
                                    }
                                )
                            )
                            Text(
                                "© 2026 ChronosFlow · Made for focused days.",
                                style = MaterialTheme.typography.labelMedium,
                                color = backdropMutedText
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun SidebarPageLayout(
    page: SidebarPage,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val topContentPadding = sidebarPageLayoutTopPadding(contentTopPadding)
    val bottomContentPadding = sidebarPageLayoutBottomPadding(contentBottomPadding)
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SidebarPageLayoutHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SidebarPageLayoutVerticalSpacing)
        ) {
            Spacer(Modifier.height(topContentPadding))
            DayDialPageHeader(
                title = page.label,
                subtitle = dayDialSidebarPageSubtitle(page),
                icon = page.icon
            )
            content()
            Spacer(Modifier.height(bottomContentPadding))
        }
    }
}

// Hub destinations open their full screens directly; this only fires when a
// stale launch target or restored state still lands on the retired stub page.
@Composable
private fun SidebarDirectLaunchEffect(page: SidebarPage, onOpen: () -> Unit) {
    LaunchedEffect(page) { onOpen() }
}

private fun formatMinutesToLabel(minutes: Int): String =
    formatDurationLabel(minutes)

private fun canPostPromotedNotificationsCompat(context: android.content.Context): Boolean {
    // Promoted (live update) notifications exist from API 37; older versions fall back
    // to a regular ongoing notification, so there is nothing to warn about.
    if (android.os.Build.VERSION.SDK_INT < 37) return true
    return try {
        val manager =
            context.getSystemService(android.app.NotificationManager::class.java)
        manager?.canPostPromotedNotifications() ?: true
    } catch (_: SecurityException) {
        true
    }
}

internal val CalendarTimelineSourceLabels =
    listOf("Calendar", "All-day calendar", "Task", "Habit", "Medication", "Plan")

internal enum class CalendarTimelinePreset(val label: String) {
    ALL("All"),
    SCHEDULE("Schedule"),
    CALENDAR("Calendar"),
    CUSTOM("Custom");

    companion object {
        /** Presets offered in the segmented control; CUSTOM only describes manual mixes. */
        val segments = listOf(ALL, SCHEDULE, CALENDAR)
    }
}

internal fun timelineSourcesForPreset(preset: CalendarTimelinePreset): Set<String>? = when (preset) {
    CalendarTimelinePreset.ALL -> CalendarTimelineSourceLabels.toSet()
    CalendarTimelinePreset.SCHEDULE -> setOf("Task", "Habit", "Medication", "Plan")
    CalendarTimelinePreset.CALENDAR -> setOf("Calendar", "All-day calendar")
    CalendarTimelinePreset.CUSTOM -> null
}

internal fun presetForTimelineSources(sources: Set<String>): CalendarTimelinePreset =
    CalendarTimelinePreset.segments.firstOrNull { timelineSourcesForPreset(it) == sources }
        ?: CalendarTimelinePreset.CUSTOM

internal fun parseTimelineSources(value: String): Set<String> =
    value.split(",")
        .map(String::trim)
        .filter { it in CalendarTimelineSourceLabels }
        .toSet()

internal fun dayToolsReviewMissedLabel(missedCount: Int): String =
    if (missedCount > 0) "Review missed ($missedCount)" else "Review missed"

internal fun clearDayConfirmationMessage(blockCount: Int, selectedDate: LocalDate): String =
    "Delete $blockCount block${if (blockCount == 1) "" else "s"} for $selectedDate? This can't be undone."

internal fun calendarMonthNavigationLabel(selectedDate: LocalDate, monthOffset: Int): String {
    val targetMonth = selectedDate.plusMonths(monthOffset.toLong())
    return "Go to ${targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}"
}

internal fun calendarDaySelectionLabel(date: LocalDate, isSelected: Boolean): String {
    val action = if (isSelected) "Selected" else "Select"
    return "$action ${date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))}"
}

internal fun templateApplyActionLabel(template: TemplateBlueprint): String = "Apply ${template.name} template"

internal fun routineApplyTodayActionLabel(template: TemplateBlueprint): String = "Apply ${template.name} routine today"

internal fun routineApplyTomorrowActionLabel(template: TemplateBlueprint): String = "Apply ${template.name} routine tomorrow"

internal fun routineCompletionLabel(doneCount: Int, totalCount: Int): String =
    "$doneCount/$totalCount blocks done today"

internal fun templateEditActionLabel(template: TemplateBlueprint): String = "Edit ${template.name} template"

internal fun templateCopyActionLabel(template: TemplateBlueprint): String = "Copy ${template.name} template"

private data class SidebarTimelineItem(
    val id: String,
    val title: String,
    val sourceLabel: String,
    val timeLabel: String,
    val detail: String,
    val statusText: String,
    val isDone: Boolean,
    val sortMinute: Int,
    val accentColor: Color,
    val primaryActionLabel: String? = null,
    val onPrimaryAction: (() -> Unit)? = null,
    val secondaryActionLabel: String? = null,
    val onSecondaryAction: (() -> Unit)? = null,
    val onRowClick: (() -> Unit)? = null,
    val isFirstInSource: Boolean = false
)

private const val UNSCHEDULED_TIMELINE_MINUTE = 24 * 60 + 10

private fun buildSidebarTimelineItems(
    timeBlocks: List<TimeBlockUiModel>,
    quickItems: DayQuickItemsUiState,
    hideCompletedItems: Boolean,
    showConnectedCalendarOnly: Boolean,
    calendarSourcesConnected: Boolean,
    visibleSourceLabels: Set<String>,
    onOpenBlock: (String?) -> Unit,
    onTaskDone: (String) -> Unit,
    onHabitDone: (String) -> Unit,
    onMedicationTaken: (String, Int?) -> Unit,
    onMedicationMissed: (String, Int?) -> Unit
): List<SidebarTimelineItem> = buildList {
    addAll(timeBlocks.map { block ->
        SidebarTimelineItem(
            id = "block:${block.id}",
            title = block.title.ifBlank {
                if (block.isAllDayCalendarImport()) "All-day calendar note" else "Focus block"
            },
            sourceLabel = timelineSourceLabel(block),
            timeLabel = if (block.isAllDayCalendarImport()) {
                "All day"
            } else {
                "${formatDisplayMinute(block.startMinuteOfDay)} – ${formatDisplayMinute((block.startMinuteOfDay + block.durationMinutes) % (24 * 60))}"
            },
            detail = if (block.isAllDayCalendarImport()) {
                "Calendar import"
            } else {
                block.category
            },
            statusText = if (block.isAllDayCalendarImport()) "Calendar item" else "Planned",
            isDone = false,
            sortMinute = if (block.isAllDayCalendarImport()) 0 else block.startMinuteOfDay,
            accentColor = categoryColor(block.category),
            onRowClick = { onOpenBlock(block.id) }
        )
    })
    addAll(quickItems.tasks.map { item ->
        item.toSidebarTimelineItem(
            kind = DayQuickItemKind.TASK,
            primaryActionLabel = if (item.isDone) null else "Complete",
            onPrimaryAction = if (item.isDone) null else ({ onTaskDone(item.id) })
        )
    })
    addAll(quickItems.habits.map { item ->
        item.toSidebarTimelineItem(
            kind = DayQuickItemKind.HABIT,
            primaryActionLabel = if (item.isDone) null else "Complete",
            onPrimaryAction = if (item.isDone) null else ({ onHabitDone(item.id) })
        )
    })
    addAll(quickItems.medications.map { item ->
        item.toSidebarTimelineItem(
            kind = DayQuickItemKind.MEDICATION,
            primaryActionLabel = if (item.isDone) null else "Take",
            onPrimaryAction = if (item.isDone) null else ({
                onMedicationTaken(item.baseMedicationPlanId(), item.scheduledMinuteOfDay)
            }),
            secondaryActionLabel = if (item.isDone) null else "Missed",
            onSecondaryAction = { onMedicationMissed(item.baseMedicationPlanId(), item.scheduledMinuteOfDay) }
        )
    })
}
    .filter { item ->
        val isCalendarSource = item.sourceLabel == "Calendar" || item.sourceLabel == "All-day calendar"
        val showItem = when {
            hideCompletedItems && item.isDone -> false
            showConnectedCalendarOnly -> isCalendarSource && calendarSourcesConnected
            !visibleSourceLabels.contains(item.sourceLabel) -> false
            else -> true
        }
        showItem
    }
    .sortedWith(
        compareBy<SidebarTimelineItem> { sourceRank(it.sourceLabel) }
            .thenBy { it.sortMinute }
            .thenBy { it.title.lowercase() }
    )
    .toList()
    .let { ordered ->
        ordered.mapIndexed { index, item ->
            val previousSource = ordered.getOrNull(index - 1)?.sourceLabel
            item.copy(isFirstInSource = previousSource != item.sourceLabel)
        }
    }

private fun timelineSourceLabel(block: TimeBlockUiModel): String = when {
    block.isAllDayCalendarImport() -> "All-day calendar"
    block.calendarEventId != null -> "Calendar"
    block.taskId != null -> "Task"
    block.habitId != null -> "Habit"
    block.medicationPlanId != null -> "Medication"
    else -> "Plan"
}

private fun DayQuickItemUiModel.toSidebarTimelineItem(
    kind: DayQuickItemKind,
    primaryActionLabel: String?,
    onPrimaryAction: (() -> Unit)?,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
): SidebarTimelineItem {
    val scheduledMinute = scheduledMinuteOfDay ?: UNSCHEDULED_TIMELINE_MINUTE
    val detail = detail.ifBlank {
        when (kind) {
            DayQuickItemKind.TASK -> "Task"
            DayQuickItemKind.HABIT -> "Habit"
            DayQuickItemKind.MEDICATION -> "Medication"
        }
    }
    return SidebarTimelineItem(
        id = "quick:${id}",
        title = title.ifBlank { "Unnamed item" },
        sourceLabel = when (kind) {
            DayQuickItemKind.TASK -> "Task"
            DayQuickItemKind.HABIT -> "Habit"
            DayQuickItemKind.MEDICATION -> "Medication"
        },
        timeLabel = scheduledMinuteOfDay?.let { formatDisplayMinute(it) } ?: "Unscheduled",
        detail = detail,
        statusText = if (isDone) "Completed" else status,
        isDone = isDone,
        sortMinute = scheduledMinute,
        accentColor = when (kind) {
            DayQuickItemKind.TASK -> ChronosColors.QuickItemTask
            DayQuickItemKind.HABIT -> ChronosColors.QuickItemHabit
            DayQuickItemKind.MEDICATION -> ChronosColors.QuickItemMedication
        },
        primaryActionLabel = primaryActionLabel,
        onPrimaryAction = onPrimaryAction,
        secondaryActionLabel = secondaryActionLabel,
        onSecondaryAction = onSecondaryAction?.takeIf { !isDone }
    )
}

private fun sourceRank(source: String): Int = when (source) {
    "Calendar" -> 0
    "All-day calendar" -> 1
    "Task" -> 2
    "Habit" -> 3
    "Medication" -> 4
    else -> 5
}

@Composable
private fun SidebarTimelineItemRow(item: SidebarTimelineItem) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (item.onRowClick != null) {
                        Modifier.chronosHapticClick(
                            onClick = item.onRowClick,
                            onClickLabel = item.title
                        )
                    } else Modifier
                ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Standard)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(item.accentColor)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                Text(
                    text = item.timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                    if (item.primaryActionLabel != null) {
                        ChronosButton(
                            onClick = item.onPrimaryAction ?: {},
                            enabled = item.onPrimaryAction != null,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(item.primaryActionLabel)
                        }
                    }
                    if (!item.isDone && item.secondaryActionLabel != null && item.onSecondaryAction != null) {
                        ChronosOutlinedButton(
                            onClick = item.onSecondaryAction,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(item.secondaryActionLabel)
                        }
                    }
                }
            }
        }
    }
}

private fun DayQuickItemUiModel.baseMedicationPlanId(): String = id.substringBefore(":")

internal const val AboutSupportEmail = "bharath.vbcr@gmail.com"

internal fun aboutVersionLabel(context: android.content.Context): String =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "Version ${info.versionName} · build $code"
    }.getOrDefault("Version 0.1.0")

internal fun aboutOverviewText(): String =
    "An offline-first day planner that pairs intentional scheduling with focus execution and " +
        "honest, actual-time insights. Plan your day on the dial, run focus sessions, track habits, " +
        "medications, and goals, then review how the day really went."

internal fun aboutPrivacyHighlights(): List<String> = listOf(
    "Your schedule, tasks, and notes live in a local database on this device.",
    "On-device AI (Gemini Nano) is the default — nothing leaves the device for it.",
    "Cloud AI is opt-in and clearly labeled; you decide when it is used.",
    "Backups and exports are created only when you ask, and stay where you put them."
)

internal fun aboutPrivacyPolicyText(): String = """
    ChronosFlow is built privacy-first.

    What we store
    • Your schedule, tasks, habits, goals, medications, journal, and sleep entries are stored locally in an on-device database. They are not uploaded to any ChronosFlow server.

    AI features
    • On-device AI (Gemini Nano) runs entirely on your device. Prompts and results never leave it.
    • Cloud AI is optional and off unless you turn it on. When enabled, only the text needed for a request is sent to the configured provider to generate a response, governed by that provider's policy.

    Connected data
    • Calendar and Health Connect data are read only after you grant permission, and are used solely to show and plan your day on this device.

    Backups & exports
    • Backups and exports are generated on demand and saved to the location you choose. You control where that data goes.

    Your control
    • Remove entries or clear app data at any time. Uninstalling the app removes the local database.
""".trimIndent()

internal fun aboutTermsText(): String = """
    By using ChronosFlow you agree to the following.

    • ChronosFlow is provided "as is", without warranties of any kind. It is a personal planning aid, not medical, legal, or professional advice.
    • Medication, health, and sleep features are reminders and records to support you — always follow guidance from a qualified professional.
    • You are responsible for keeping your own backups of important data.
    • AI-generated suggestions can be inaccurate; review them before acting.
    • Continued use after an update means you accept the current version of these terms.
""".trimIndent()

internal fun aboutOpenSourceLicenses(): List<Pair<String, String>> = listOf(
    "Jetpack Compose & AndroidX" to "Apache License 2.0",
    "Kotlin & Coroutines" to "Apache License 2.0",
    "Room" to "Apache License 2.0",
    "Hilt / Dagger" to "Apache License 2.0",
    "Material Components & Icons" to "Apache License 2.0",
    "ML Kit GenAI (Gemini Nano)" to "Google APIs Terms of Service"
)

private fun launchAboutFeedbackEmail(
    context: android.content.Context,
    versionLabel: String,
    showMessage: (String) -> Unit
) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("mailto:$AboutSupportEmail")
        putExtra(android.content.Intent.EXTRA_SUBJECT, "ChronosFlow feedback ($versionLabel)")
    }
    runCatching { context.startActivity(intent) }
        .onFailure { showMessage("No email app available") }
}
