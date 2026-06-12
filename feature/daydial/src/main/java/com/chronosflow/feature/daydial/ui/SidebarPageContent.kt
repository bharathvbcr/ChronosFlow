package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsSuggest
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosMetricTile
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.components.ChronosWarningBanner
import com.chronosflow.core.ui.theme.ChronosSpacing
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.genai.GenAiAssistCopy
import com.chronosflow.core.ui.components.ChronosTimeWindowControls
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.core.ui.components.nudgeMinuteText
import com.chronosflow.core.ui.components.parseFlexibleMinute
import com.chronosflow.core.ui.settings.ChronosBackdropTheme
import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import com.chronosflow.feature.daydial.CalendarConnectionState
import com.chronosflow.feature.daydial.CalendarPermissionStatus
import com.chronosflow.feature.daydial.DataExportPanel
import com.chronosflow.feature.daydial.DataExportState
import com.chronosflow.feature.daydial.DailyReview
import com.chronosflow.feature.daydial.RoutineCompletionSummary
import com.chronosflow.feature.daydial.TimeBlockUiModel
import com.chronosflow.feature.daydial.isAllDayCalendarImport
import com.chronosflow.feature.daydial.model.DayQuickItemKind
import com.chronosflow.feature.daydial.model.DayQuickItemUiModel
import com.chronosflow.feature.daydial.model.DayQuickItemsUiState
import com.chronosflow.feature.daydial.calendarConnectionStatusMessage
import com.chronosflow.feature.daydial.model.AppearanceMode
import com.chronosflow.feature.daydial.delegate.AppLockSettingsState
import com.chronosflow.feature.daydial.model.SidebarPage
import com.chronosflow.feature.daydial.model.TemplateBlueprint
import com.chronosflow.core.ui.theme.categoryColor
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

private fun backdropThemeSwatch(theme: ChronosBackdropTheme): Color =
    when (theme) {
        ChronosBackdropTheme.LIQUID -> Color(0xFF8B5CF6)
        ChronosBackdropTheme.SMOKE -> Color(0xFF94A3B8)
        ChronosBackdropTheme.WATER_DROPS -> Color(0xFF38BDF8)
        ChronosBackdropTheme.AURORA -> Color(0xFF22C55E)
        ChronosBackdropTheme.SUNSET_GLOW -> Color(0xFFF59E0B)
        ChronosBackdropTheme.NEBULA -> Color(0xFF6366F1)
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
    compactMode: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onPrivacyModeSelected: (PrivacyMode) -> Unit,
    onPreviewOnDeviceModelChanged: (Boolean) -> Unit,
    onCompactModeToggled: () -> Unit,
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
        contentBottomPadding = contentBottomPadding
    ) {
            var hideCompletedItems by rememberSaveable { mutableStateOf(true) }
            var connectedSourcesOnly by rememberSaveable { mutableStateOf(false) }
            var didAutoApplyConnectedFilter by rememberSaveable { mutableStateOf(false) }
            var visibleTimelineSources by remember {
                mutableStateOf(setOf("Calendar", "All-day calendar", "Task", "Habit", "Medication", "Plan"))
            }
            val timelineSourceOptions = listOf(
                "Calendar",
                "All-day calendar",
                "Task",
                "Habit",
                "Medication",
                "Plan"
            )
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
                            accent = MaterialTheme.colorScheme.error
                        )
                        ChronosMetricTile(
                            "Planned",
                            formatMinutesToLabel(review.plannedMinutes),
                            Modifier.width(140.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionGrid(
                        buildList {
                            if (featureFlags.aiAdvisorEnabled) {
                                add("Generate Plan" to onGeneratePlan)
                            }
                            add("Rebalance Day" to onRebalance)
                            add("Fill Empty Time" to onFillGaps)
                            add("Save Template" to onSaveTemplate)
                            add("Restore previous" to onRestorePrevious)
                            add("Review Missed" to onOpenMissed)
                            add("Clear Day" to onClearDay)
                        }
                    )
                }
                SidebarPage.TASKS -> {
                    SidebarHubPanel(
                        description = "Open task capture, priorities, and completion tracking.",
                        buttonLabel = "Open Tasks",
                        icon = Icons.Default.Checklist,
                        onClick = onOpenTasks
                    )
                }
                SidebarPage.FOCUS_TIMER -> {
                    SidebarHubPanel(
                        description = "Open the Focus Planner to review upcoming focus blocks and start a session.",
                        buttonLabel = "Open Focus Planner",
                        icon = Icons.Default.Timer,
                        onClick = { onOpenFocusScreen() }
                    )
                }
                SidebarPage.HABITS -> {
                    SidebarHubPanel(
                        description = "Open habit streaks, recurring routines, and completion history.",
                        buttonLabel = "Open Habits",
                        icon = Icons.Default.Favorite,
                        onClick = onOpenHabits
                    )
                }
                SidebarPage.GOALS -> {
                    SidebarHubPanel(
                        description = "Open long-term goals, their progress, and the work linked to them.",
                        buttonLabel = "Open Goals",
                        icon = Icons.Default.Flag,
                        onClick = onOpenGoals
                    )
                }
                SidebarPage.MEDICATION -> {
                    SidebarHubPanel(
                        description = "Open medication schedules, dose tracking, and reminder reliability.",
                        buttonLabel = "Open Meds",
                        icon = Icons.Default.Medication,
                        onClick = onOpenMedication
                    )
                }
                SidebarPage.REVIEW -> {
                    Text("Compare your planned day against actual focus execution.")
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ChronosSectionTitle(title = "Today's progress")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Planned", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(formatMinutesToLabel(review.plannedMinutes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Actual", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                    Text(formatMinutesToLabel(review.actualMinutes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Missed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                    Text(formatMinutesToLabel(review.missedMinutes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Button(onClick = onOpenReview, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Assessment, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Daily Review")
                    }
                }
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
                                    TextButton(
                                        onClick = { onApplyTemplateToday(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = routineApplyTodayActionLabel(template)
                                        }
                                    ) { Text("Apply today") }
                                    TextButton(
                                        onClick = { onApplyTemplateTomorrow(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = routineApplyTomorrowActionLabel(template)
                                        }
                                    ) { Text("Apply tomorrow") }
                                    TextButton(
                                        onClick = { onApplyTemplate(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = templateApplyActionLabel(template)
                                        }
                                    ) { Text("Seed day") }
                                    TextButton(
                                        onClick = { onEditTemplate(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = templateEditActionLabel(template)
                                        }
                                    ) { Text("Edit") }
                                    TextButton(
                                        onClick = { onDuplicateTemplate(template) },
                                        modifier = Modifier.semantics {
                                            contentDescription = templateCopyActionLabel(template)
                                        }
                                    ) { Text("Copy") }
                                }
                            }
                        }
                    }
                    Button(onClick = onSaveTemplate, modifier = Modifier.fillMaxWidth()) { Text("Save current day as routine") }
                }
                SidebarPage.CALENDARS -> {
                    val previousMonthLabel = calendarMonthNavigationLabel(selectedDate, monthOffset = -1)
                    val nextMonthLabel = calendarMonthNavigationLabel(selectedDate, monthOffset = 1)

                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onSelectDate(selectedDate.minusMonths(1)) }) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = previousMonthLabel)
                                }
                                Text(selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { onSelectDate(selectedDate.plusMonths(1)) }) {
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
                                                        .clickable(
                                                            onClickLabel = calendarDaySelectionLabel(date, isSelected = false),
                                                            role = Role.Button
                                                        ) { onSelectDate(date) },
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
                            DailyReviewHeader(review)
                        }
                    }
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(
                                title = "Device calendar",
                                subtitle = "Import Android events and keep exported blocks linked"
                            )
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
                            Text(
                                "Connection: $connectionLabel",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
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
                                        Button(onClick = onRequestCalendarExportAccess) {
                                            Text("Continue")
                                        }
                                        TextButton(onClick = onDismissCalendarPermissionRationale) {
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
                                        Button(onClick = onOpenCalendarSettings) {
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
                                        Button(onClick = onRequestCalendarSync) {
                                            Text("Continue")
                                        }
                                        TextButton(onClick = onDismissCalendarPermissionRationale) {
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
                                        Button(onClick = onRequestCalendarSync) {
                                            Text("Connect calendar")
                                        }
                                        OutlinedButton(onClick = onOpenCalendarSettings) {
                                            Text("Open settings")
                                        }
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = hideCompletedItems,
                                    onClick = { hideCompletedItems = !hideCompletedItems },
                                    label = { Text("Hide completed") }
                                )
                                FilterChip(
                                    selected = connectedSourcesOnly,
                                    onClick = {
                                        if (calendarPermissionStatus.readGranted) {
                                            didAutoApplyConnectedFilter = true
                                            connectedSourcesOnly = !connectedSourcesOnly
                                        }
                                    },
                                    enabled = calendarPermissionStatus.readGranted,
                                    label = { Text("Connected calendar only") }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                timelineSourceOptions.forEach { source ->
                                    FilterChip(
                                        selected = visibleTimelineSources.contains(source),
                                        onClick = {
                                            visibleTimelineSources = if (visibleTimelineSources.contains(source)) {
                                                visibleTimelineSources - source
                                            } else {
                                                visibleTimelineSources + source
                                            }
                                        },
                                        label = { Text(source) }
                                    )
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
                            ChronosSectionTitle(title = "AI planner", subtitle = "Privacy and planning style")
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("Strict", "Balanced", "Flexible").forEach { style ->
                                    FilterChip(
                                        selected = planningStyle == style,
                                        onClick = { onPlanningStyleSelected(style) },
                                        label = { Text(style) }
                                    )
                                }
                            }
                            CheckboxSetting("Protect focus blocks", protectFocusBlocks, onProtectFocusChanged)
                            CheckboxSetting("Add breaks automatically", addBreaksAutomatically, onAddBreaksAutomaticallyChanged)
                            CheckboxSetting("Preserve manual blocks", preserveManualBlocks, onPreserveManualBlocksChanged)
                            Button(
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
                            ChronosSectionTitle(title = "Privacy and sync", subtitle = "On-device AI and checkpoints")
                            PrivacyModeSelector(privacyMode, onPrivacyModeSelected)
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
                }
                SidebarPage.NOTIFICATIONS -> {
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChronosSectionTitle(title = "Notifications", subtitle = "Reminders and quiet hours")
                            CheckboxSetting("Block start reminders", blockStartReminders, onBlockStartRemindersChanged)
                            CheckboxSetting("Break reminders", breakReminders, onBreakRemindersChanged)
                            CheckboxSetting("Missed block alerts", missedAlerts, onMissedAlertsChanged)
                            CheckboxSetting("End-of-day review", endDayReviewReminder, onEndDayReviewReminderChanged)
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
                            OutlinedButton(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Notifications, contentDescription = null)
                                Text("Notification permission")
                            }
                            OutlinedButton(onClick = onOpenExactAlarmSettings, modifier = Modifier.fillMaxWidth()) {
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
                                    FilterChip(
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
                                subtitle = "Choose the ambient background style"
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                ChronosBackdropTheme.entries.forEach { theme ->
                                    FilterChip(
                                        selected = backdropTheme == theme,
                                        onClick = { onBackdropThemeSelected(theme) },
                                        label = { Text(theme.label) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(backdropThemeSwatch(theme))
                                            )
                                        }
                                    )
                                }
                            }
                            CheckboxSetting("Dynamic color", dynamicColorEnabled, onDynamicColorChanged)
                            CheckboxSetting("Glass surfaces", glassSurfacesEnabled, onGlassSurfacesChanged)
                            CheckboxSetting("Reduce motion", reduceMotionEnabled, onReduceMotionChanged)
                            CheckboxSetting("High contrast", highContrastEnabled, onHighContrastChanged)
                            FilterChip(
                                selected = compactMode,
                                onClick = onCompactModeToggled,
                                label = { Text(if (compactMode) "12h zoom on" else "24h dial") }
                            )
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
                    ActionGrid(listOf("View Diagnostics" to onOpenDiagnostics, "View Logs" to onOpenDiagnostics))
                }
                SidebarPage.ABOUT -> {
                    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ChronosFlow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Day planner, focus execution, and actual-time insights.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = backdropMutedText
                            )
                            Text("Version 0.1.0", style = MaterialTheme.typography.labelMedium, color = backdropMutedText)
                        }
                    }
                }
            }
    }
}

@Composable
private fun SidebarPageLayout(
    page: SidebarPage,
    contentBottomPadding: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val bottomContentPadding = sidebarPageLayoutBottomPadding(contentBottomPadding)
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SidebarPageLayoutHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SidebarPageLayoutVerticalSpacing)
        ) {
            Spacer(Modifier.height(SidebarPageLayoutHorizontalPadding))
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

@Composable
private fun SidebarHubPanel(
    description: String,
    buttonLabel: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel)
            }
        }
    }
}

private fun formatMinutesToLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

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
            DayQuickItemKind.TASK -> Color(0xFF2F6BEA)
            DayQuickItemKind.HABIT -> Color(0xFF5DAA54)
            DayQuickItemKind.MEDICATION -> Color(0xFFD17A2A)
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
                        Modifier.clickable(
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
                        Button(
                            onClick = item.onPrimaryAction ?: {},
                            enabled = item.onPrimaryAction != null,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(item.primaryActionLabel)
                        }
                    }
                    if (!item.isDone && item.secondaryActionLabel != null && item.onSecondaryAction != null) {
                        OutlinedButton(
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
