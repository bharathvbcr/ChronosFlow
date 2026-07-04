package com.ChronosFlow.VBCR.feature.daydial

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ChronosFlow.VBCR.core.ui.shell.ChronosShellChromeSuppression
import com.ChronosFlow.VBCR.core.ui.shell.LocalChronosShellBottomInset
import com.ChronosFlow.VBCR.core.ui.shell.ChronosSnackbarHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ChronosFlow.VBCR.core.data.security.AppLockAuthResult
import com.ChronosFlow.VBCR.feature.focus.FocusService
import com.ChronosFlow.VBCR.feature.focus.sendFocusServiceCommand
import com.ChronosFlow.VBCR.core.data.security.SensitiveArea
import com.ChronosFlow.VBCR.core.ui.components.ChronosBackdrop
import com.ChronosFlow.VBCR.core.ui.components.ChronosPredictiveBackHandlerWithProgress
import com.ChronosFlow.VBCR.core.ui.motion.ChronosMotionDefaults
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionDirection
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionFactory
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionSet
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import com.ChronosFlow.VBCR.feature.daydial.model.AppearanceMode
import com.ChronosFlow.VBCR.feature.daydial.model.DayDialTab
import com.ChronosFlow.VBCR.feature.daydial.model.SheetTarget
import com.ChronosFlow.VBCR.feature.daydial.model.SidebarPage
import com.ChronosFlow.VBCR.feature.daydial.ui.DayDialSidebar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun dayDialContentBottomPadding(
    routeBottomPadding: Dp,
    shellBottomInset: Dp
): Dp {
    val bottomInset = if (shellBottomInset > routeBottomPadding) {
        shellBottomInset
    } else {
        routeBottomPadding
    }
    return if (bottomInset > 0.dp) {
        bottomInset + ChronosSpacing.Medium
    } else {
        0.dp
    }
}

internal fun sidebarBackTargetTab(currentTab: DayDialTab): DayDialTab {
    return if (currentTab == DayDialTab.PLAN) {
        DayDialTab.PLAN
    } else {
        DayDialTab.TODAY
    }
}

internal fun shouldSuppressDayDialShellChrome(
    activeSidebarPage: SidebarPage?
): Boolean = activeSidebarPage != null

/**
 * Pages whose menu entry opens the full feature screen directly instead of an
 * in-dial sidebar page. Their stub hub pages were removed.
 */
internal fun sidebarPageOpensFullScreen(page: SidebarPage): Boolean =
    when (page) {
        SidebarPage.TASKS,
        SidebarPage.FOCUS_TIMER,
        SidebarPage.HABITS,
        SidebarPage.MEDICATION -> true
        else -> false
    }

internal fun sidebarPageForDrawerSelection(page: SidebarPage): SidebarPage? =
    if (sidebarPageOpensFullScreen(page)) null else page

/**
 * Bottom clearance for the floating sidebar menu so it hovers just above the
 * shell bottom bar; falls back to standard spacing when no bar is shown.
 */
internal fun sidebarMenuBottomPadding(shellBottomInset: Dp): Dp =
    if (shellBottomInset > 0.dp) shellBottomInset else ChronosSpacing.Standard

internal fun sidebarMenuMaxHeight(availableHeight: Dp): Dp = availableHeight * 0.72f

private fun sidebarMenuTransition(reducedMotion: Boolean): ChronosTransitionSet =
    if (reducedMotion) {
        ChronosTransitionFactory.fadeScale(
            durationMillis = ChronosMotionDefaults.ReducedDurationMillis,
            easing = ChronosMotionDefaults.MaterialStandardEasing,
            direction = ChronosTransitionDirection.Neutral,
            enterScale = 1f,
            exitScale = 1f
        )
    } else {
        ChronosTransitionFactory.materialSharedAxisY(
            durationMillis = ChronosMotionDefaults.DefaultDurationMillis,
            easing = ChronosMotionDefaults.MaterialStandardEasing,
            direction = ChronosTransitionDirection.Forward,
            slideFraction = 0.5f
        )
    }

private fun sidebarMenuScrimTransition(reducedMotion: Boolean): ChronosTransitionSet =
    ChronosTransitionFactory.fadeScale(
        durationMillis = if (reducedMotion) {
            ChronosMotionDefaults.ReducedDurationMillis
        } else {
            ChronosMotionDefaults.DefaultDurationMillis
        },
        easing = ChronosMotionDefaults.MaterialStandardEasing,
        direction = ChronosTransitionDirection.Neutral,
        enterScale = 1f,
        exitScale = 1f
    )

@Suppress("UNUSED_PARAMETER")
internal fun shouldShowDayDialBackdrop(
    glassSurfacesEnabled: Boolean,
    highContrastEnabled: Boolean,
    activeSidebarPage: SidebarPage?
): Boolean =
    glassSurfacesEnabled &&
        !highContrastEnabled

@Suppress("UNUSED_PARAMETER")
internal fun shouldShowDayDialLiquidBackdrop(
    glassSurfacesEnabled: Boolean,
    highContrastEnabled: Boolean,
    reduceMotionEnabled: Boolean,
    activeSidebarPage: SidebarPage?
): Boolean =
    shouldShowDayDialBackdrop(
        glassSurfacesEnabled = glassSurfacesEnabled,
        highContrastEnabled = highContrastEnabled,
        activeSidebarPage = activeSidebarPage
    )

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DayDialScreenChrome(
    viewModel: DayDialViewModel,
    vmState: DayDialViewModelState,
    uiState: DayDialScreenUiState,
    settings: DayDialSettingsState,
    templateState: DayDialTemplateState,
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    appearanceMode: AppearanceMode,
    sortedBlocks: List<TimeBlockUiModel>,
    missedBlocks: List<TimeBlockUiModel>,
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    initialFocusCapture: String? = null,
    contentPadding: PaddingValues,
    focusRemainingSeconds: Long,
    focusElapsedSeconds: Long,
    snackbarHostState: SnackbarHostState,
    renderedCurrentTab: DayDialTab = uiState.currentTab,
    renderedActiveSidebarPage: SidebarPage? = uiState.activeSidebarPage,
    reviewBackProgress: Float = 0f,
    requestNotificationPermission: () -> Unit,
    calendarPermissionStatus: CalendarPermissionStatus,
    showCalendarPermissionRationale: Boolean,
    onDismissCalendarPermissionRationale: () -> Unit,
    onRequestCalendarSync: () -> Unit,
    onRequestCalendarExportAccess: () -> Unit,
    onOpenCalendarSettings: () -> Unit,
    onExportBlockToCalendar: (String) -> Unit,
    onRefreshCalendarExport: (String) -> Unit,
    onRemoveCalendarExport: (String) -> Unit,
    onCreateDataExport: () -> Unit,
    calendarConnectionState: CalendarConnectionState,
    restorePrevious: () -> Unit,
    onOpenFocusScreen: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenMedication: () -> Unit,
    onOpenReview: () -> Unit,
    onSelectPrimaryTab: (DayDialTab) -> Unit,
    onOpenCommandPalette: (() -> Unit)?
) {
    var sidebarMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val appLockSettings by viewModel.appLockSettings.collectAsStateWithLifecycle()
    val sensitiveSession by viewModel.sensitiveSession.collectAsStateWithLifecycle()
    var dataExportAuthError by remember { mutableStateOf<String?>(null) }
    val dataExportRequiresAuth = remember(sensitiveSession) {
        viewModel.requiresSensitiveAuth(SensitiveArea.DATA_EXPORT)
    }
    var dataExportCanAuthenticate by remember(activity) { mutableStateOf(false) }
    LaunchedEffect(activity) {
        dataExportCanAuthenticate = false
        dataExportCanAuthenticate = activity?.let { host ->
            withContext(Dispatchers.IO) {
                viewModel.canAuthenticate(host)
            }
        } ?: false
    }
    val appLockCanAuthenticate = dataExportCanAuthenticate
    val bottomContentPadding = dayDialContentBottomPadding(
        routeBottomPadding = contentPadding.calculateBottomPadding(),
        shellBottomInset = LocalChronosShellBottomInset.current
    )
    val featureFlags = settings.featureFlags
    val appVersionLabel = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    LaunchedEffect(featureFlags, uiState.activeSidebarPage) {
        val activePage = uiState.activeSidebarPage
        if (activePage in SidebarPage.rootPages && activePage !in SidebarPage.rootPages(featureFlags)) {
            uiState.activeSidebarPage = null
        }
    }

    ChronosShellChromeSuppression(
        "day-sidebar-page",
        shouldSuppressDayDialShellChrome(
            activeSidebarPage = renderedActiveSidebarPage
        )
    )

    val onSidebarBack = {
        uiState.activeSidebarPage = null
        onSelectPrimaryTab(sidebarBackTargetTab(renderedCurrentTab))
    }

    // Close the floating sidebar menu when the user switches primary tabs
    // (e.g. via the shell bottom bar) while it is open.
    LaunchedEffect(renderedCurrentTab) {
        sidebarMenuExpanded = false
    }
    BackHandler(enabled = sidebarMenuExpanded) {
        sidebarMenuExpanded = false
    }

    // Track swipe-back progress for the sidebar-page predictive back preview.
    // A value of 0f means no gesture in progress; the signed -1f..1f value drives
    // horizontal translation so content follows the swipe edge in the same coordinate space.
    var sidebarBackProgress by remember { mutableFloatStateOf(0f) }
    val sidebarBackEnabled = renderedActiveSidebarPage != null && !sidebarMenuExpanded
    ChronosPredictiveBackHandlerWithProgress(
        enabled = sidebarBackEnabled,
        onBackStarted = { backEvent -> sidebarBackProgress = signedSidebarBackProgress(backEvent) },
        onBackProgressed = { backEvent -> sidebarBackProgress = signedSidebarBackProgress(backEvent) },
        onBackCancelled = { sidebarBackProgress = 0f },
        onBackInvoked = {
            sidebarBackProgress = 0f
            onSidebarBack()
        }
    )

    val backgroundBrush = remember(colorScheme, darkTheme, settings.highContrastEnabled) {
        chronosBackgroundBrush(colorScheme, darkTheme, settings.highContrastEnabled)
    }

    MaterialTheme(colorScheme = colorScheme) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            if (
                shouldShowDayDialBackdrop(
                    glassSurfacesEnabled = settings.glassSurfacesEnabled,
                    highContrastEnabled = settings.highContrastEnabled,
                    activeSidebarPage = renderedActiveSidebarPage
                )
            ) {
                ChronosBackdrop(
                    theme = settings.backdropTheme,
                    darkTheme = darkTheme,
                    reduceMotionEnabled = settings.reduceMotionEnabled
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                Scaffold(
                    snackbarHost = { ChronosSnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    topBar = {
                        DayDialTopBar(
                            activeSidebarPage = renderedActiveSidebarPage,
                            currentTab = renderedCurrentTab,
                            selectedDate = vmState.selectedDate,
                            canUndo = vmState.canUndo,
                            canRedo = vmState.canRedo,
                            darkTheme = darkTheme,
                        glassSurfacesEnabled = settings.glassSurfacesEnabled,
                        highContrastEnabled = settings.highContrastEnabled,
                        menuExpanded = sidebarMenuExpanded,
                        onSelectDate = viewModel::selectDate,
                            onMenuClick = { sidebarMenuExpanded = !sidebarMenuExpanded },
                            onBackClick = onSidebarBack,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            reduceMotionEnabled = settings.reduceMotionEnabled,
                            onOpenCommandPalette = onOpenCommandPalette
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            DayDialMainContent(
                                scaffoldPadding = paddingValues,
                                viewModel = viewModel,
                                selectedDate = vmState.selectedDate,
                                sortedBlocks = sortedBlocks,
                                freeTime = vmState.freeTime,
                                currentMinute = vmState.currentMinute,
                                selectedBlockId = vmState.selectedBlockId,
                                hapticCue = vmState.hapticCue,
                                review = vmState.review,
                                privacyMode = vmState.privacyMode,
                                previewOnDeviceModel = vmState.previewOnDeviceModel,
                                cloudAiEnabled = vmState.cloudAiEnabled,
                                compactMode = vmState.compactMode,
                                compactWindowStart = vmState.compactWindowStart,
                                missedBlocks = missedBlocks,
                                suggestedBlocks = vmState.suggestedBlocks,
                                templateState = templateState,
                                focusedBlock = vmState.focusedBlock,
                                selectedBlock = vmState.selectedBlock,
                                activeBlock = activeBlock,
                                nextBlock = nextBlock,
                                initialFocusCapture = initialFocusCapture,
                                focusSession = vmState.focusSession,
                                focusRemainingSeconds = focusRemainingSeconds,
                                focusElapsedSeconds = focusElapsedSeconds,
                                focusRestoredMessage = vmState.focusRestoredMessage,
                                moodEnergyCheckIns = vmState.moodEnergyCheckIns,
                                moodCheckInCoaching = vmState.moodCheckInCoaching,
                                focusGuidance = vmState.focusGuidance,
                                focusNextBlockSuggestion = vmState.focusNextBlockSuggestion,
                                sleepTrack = vmState.sleepTrack,
                                journalEntry = vmState.journalEntry,
                                genAiRuntimeStatus = vmState.genAiRuntimeStatus,
                                insightsTabState = vmState.insightsTabState,
                                manualMissedBlockIds = vmState.manualMissedIds,
                                planningStyle = settings.planningStyle,
                                showRingGuide = settings.showRingGuide,
                                protectFocusBlocks = settings.protectFocusBlocks,
                                addBreaksAutomatically = settings.addBreaksAutomatically,
                                defaultFocusBreakPreset = settings.defaultFocusBreakPreset,
                                preserveManualBlocks = settings.preserveManualBlocks,
                                syncCloud = settings.syncCloud,
                                syncStatus = settings.syncStatus,
                                blockStartReminders = settings.blockStartReminders,
                                breakReminders = settings.breakReminders,
                                missedAlerts = settings.missedAlerts,
                                endDayReviewReminder = settings.endDayReviewReminder,
                                sleepJournalLogReminder = settings.sleepJournalLogReminder,
                                sleepScheduleEnabled = settings.sleepScheduleEnabled,
                                sleepScheduleStartMinute = settings.sleepScheduleStartMinute,
                                sleepScheduleEndMinute = settings.sleepScheduleEndMinute,
                                reminderScheduleStatus = vmState.reminderScheduleStatus,
                                medicationReliabilityStatus = vmState.medicationReliabilityStatus,
                                dynamicColorEnabled = settings.dynamicColorEnabled,
                                glassSurfacesEnabled = settings.glassSurfacesEnabled,
                                appearanceMode = appearanceMode,
                                backdropTheme = settings.backdropTheme,
                                reduceMotionEnabled = settings.reduceMotionEnabled,
                                highContrastEnabled = settings.highContrastEnabled,
                                featureFlags = featureFlags,
                                onHabitsFeatureEnabledChanged = { settings.habitsFeatureEnabled = it },
                                onGoalsFeatureEnabledChanged = { settings.goalsFeatureEnabled = it },
                                onMedicationFeatureEnabledChanged = { settings.medicationFeatureEnabled = it },
                                onReviewFeatureEnabledChanged = { settings.reviewFeatureEnabled = it },
                                onJournalFeatureEnabledChanged = { settings.journalFeatureEnabled = it },
                                onSleepFeatureEnabledChanged = { settings.sleepFeatureEnabled = it },
                                onAiAdvisorFeatureEnabledChanged = { settings.aiAdvisorFeatureEnabled = it },
                                onPlanningStyleSelected = { settings.planningStyle = it },
                                onPreviewOnDeviceModelChanged = viewModel::setPreviewOnDeviceModel,
                                onProtectFocusChanged = { settings.protectFocusBlocks = it },
                                onAddBreaksAutomaticallyChanged = { settings.addBreaksAutomatically = it },
                                onPreserveManualBlocksChanged = { settings.preserveManualBlocks = it },
                                onSyncCloudChanged = { settings.syncCloud = it },
                                onSyncStatusChanged = { settings.syncStatus = it },
                                onBlockStartRemindersChanged = { settings.blockStartReminders = it },
                                onBreakRemindersChanged = { settings.breakReminders = it },
                                onMissedAlertsChanged = { settings.missedAlerts = it },
                                onEndDayReviewReminderChanged = { settings.endDayReviewReminder = it },
                                onSleepJournalLogReminderChanged = { settings.sleepJournalLogReminder = it },
                                onSleepScheduleEnabledChanged = { settings.sleepScheduleEnabled = it },
                                onSleepScheduleStartMinuteChanged = { settings.sleepScheduleStartMinute = it },
                                onSleepScheduleEndMinuteChanged = { settings.sleepScheduleEndMinute = it },
                                onDynamicColorChanged = { settings.dynamicColorEnabled = it },
                                onGlassSurfacesChanged = { settings.glassSurfacesEnabled = it },
                                onAppearanceModeSelected = { settings.appearanceModeValue = it.name },
                                onBackdropThemeSelected = { settings.backdropThemeValue = it.name },
                                onReduceMotionChanged = { settings.reduceMotionEnabled = it },
                                onHighContrastChanged = { settings.highContrastEnabled = it },
                                onActiveSheetChanged = { target ->
                                    uiState.activeSheet = target
                                    sheetOpenLogLabel(target)?.let(viewModel::recordSheetOpened)
                                },
                                onRequestNotificationPermission = requestNotificationPermission,
                                calendarPermissionStatus = calendarPermissionStatus,
                                showCalendarPermissionRationale = showCalendarPermissionRationale,
                                onDismissCalendarPermissionRationale = onDismissCalendarPermissionRationale,
                                onRequestCalendarSync = onRequestCalendarSync,
                                onRequestCalendarExportAccess = onRequestCalendarExportAccess,
                                onOpenCalendarSettings = onOpenCalendarSettings,
                                onExportBlockToCalendar = onExportBlockToCalendar,
                                onRefreshCalendarExport = onRefreshCalendarExport,
                                onRemoveCalendarExport = onRemoveCalendarExport,
                                dataExportState = vmState.dataExportState,
                                onCreateDataExport = onCreateDataExport,
                                calendarConnectionState = calendarConnectionState,
                                onRestorePrevious = restorePrevious,
                                onOpenFocusScreen = onOpenFocusScreen,
                                onOpenTasks = onOpenTasks,
                                onOpenHabits = onOpenHabits,
                                onOpenGoals = onOpenGoals,
                                onOpenMedication = onOpenMedication,
                                onOpenReview = onOpenReview,
                                appLockSettings = appLockSettings,
                                appLockCanAuthenticate = appLockCanAuthenticate,
                                onAppLockEnabledChanged = viewModel::setAppLockEnabled,
                                onLockOnResumeChanged = viewModel::setLockOnResume,
                                onRequireAuthMedicationChanged = viewModel::setRequireAuthMedication,
                                onRequireAuthReviewChanged = viewModel::setRequireAuthReview,
                                onRequireAuthDataExportChanged = viewModel::setRequireAuthDataExport,
                                dataExportRequiresAuth = dataExportRequiresAuth,
                                dataExportCanAuthenticate = dataExportCanAuthenticate,
                                dataExportAuthError = dataExportAuthError,
                                onUnlockDataExport = {
                                    val host = activity
                                    if (host != null) {
                                        viewModel.unlockSensitiveArea(host, SensitiveArea.DATA_EXPORT) { result ->
                                            dataExportAuthError = when (result) {
                                                AppLockAuthResult.Success -> null
                                                AppLockAuthResult.Cancelled -> null
                                                AppLockAuthResult.Unavailable ->
                                                    "Set a screen lock (PIN, pattern, or password) in Android settings."
                                                is AppLockAuthResult.Error -> result.message
                                            }
                                        }
                                    }
                                },
                                onDeleteAllData = {
                                    viewModel.deleteAllData(
                                        onComplete = {
                                            // Navigate away from settings back to the root tab so the
                                            // user lands on an empty-but-valid state.
                                            uiState.activeSidebarPage = null
                                            uiState.snackbarMessage = "All data has been deleted."
                                        },
                                        onError = { reason ->
                                            uiState.snackbarMessage = reason
                                        }
                                    )
                                },
                                onReminderKindPreferencesChanged = {
                                    viewModel.refreshReminderScheduleFromStoredPreferences()
                                    viewModel.refreshCurrentBlockNotification()
                                },
                                onLiveSurfacePreferencesChanged = { enabled ->
                                    viewModel.setCurrentBlockNotificationEnabled(enabled)
                                    viewModel.reconcileSeparateFoldableAlarms()
                                    viewModel.refreshCurrentBlockNotification()
                                },
                                onFocusLiveActivityPreferenceChanged = {
                                    context.sendFocusServiceCommand(
                                        action = FocusService.ACTION_SYNC,
                                        timeLeft = 0,
                                        totalSeconds = 0,
                                        sessionId = null
                                    )
                                },
                                contentBottomPadding = bottomContentPadding,
                                onOpenPlanTab = {
                                    onSelectPrimaryTab(DayDialTab.PLAN)
                                    uiState.activeSidebarPage = null
                                },
                                showMessage = { uiState.snackbarMessage = it },
                                onShowUndoSnackbar = { message, onUndo ->
                                    uiState.showSnackbar(message, actionLabel = "Undo", onAction = onUndo)
                                },
                                sidebarBackProgress = sidebarBackProgress,
                                reviewBackProgress = reviewBackProgress,
                                currentTab = renderedCurrentTab,
                                activeSidebarPage = renderedActiveSidebarPage
                            )
                        }
                    }
                }
                }
            }

            val sidebarMenuScrim = sidebarMenuScrimTransition(settings.reduceMotionEnabled)
            AnimatedVisibility(
                visible = sidebarMenuExpanded,
                enter = sidebarMenuScrim.enter,
                exit = sidebarMenuScrim.exit
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Close menu",
                            onClick = { sidebarMenuExpanded = false }
                        )
                )
            }

            val sidebarMenuMotion = sidebarMenuTransition(settings.reduceMotionEnabled)
            val shellBottomInset = LocalChronosShellBottomInset.current
            AnimatedVisibility(
                visible = sidebarMenuExpanded,
                enter = sidebarMenuMotion.enter,
                exit = sidebarMenuMotion.exit,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .then(
                        if (shellBottomInset > 0.dp) Modifier else Modifier.navigationBarsPadding()
                    )
                    .padding(
                        start = ChronosSpacing.Standard,
                        bottom = sidebarMenuBottomPadding(shellBottomInset)
                    )
            ) {
                DayDialSidebar(
                    activePage = renderedActiveSidebarPage,
                    dark = darkTheme,
                    glassSurfacesEnabled = settings.glassSurfacesEnabled,
                    featureFlags = featureFlags,
                    versionLabel = appVersionLabel,
                    onPageSelected = { page ->
                        sidebarMenuExpanded = false
                        when (page) {
                            // Review resolves to the Review tab — the same destination as
                            // the bottom nav and command palette — instead of the retired
                            // in-shell stub page. The detailed planned/actual/missed sheet
                            // is opened from within that tab.
                            SidebarPage.REVIEW -> onSelectPrimaryTab(DayDialTab.INSIGHTS)
                            else -> {
                                val inShellPage = sidebarPageForDrawerSelection(page)
                                if (inShellPage != null) {
                                    uiState.activeSidebarPage = inShellPage
                                } else {
                                    when (page) {
                                        SidebarPage.TASKS -> onOpenTasks()
                                        SidebarPage.FOCUS_TIMER -> onOpenFocusScreen()
                                        SidebarPage.HABITS -> onOpenHabits()
                                        SidebarPage.MEDICATION -> onOpenMedication()
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.heightIn(max = sidebarMenuMaxHeight(maxHeight))
                )
            }
        }
    }
}

/**
 * Human label for the developer log when a sheet opens, or null for high-frequency
 * transient sheets (block editor) that would otherwise flood the recent-events feed.
 */
private fun sheetOpenLogLabel(target: SheetTarget?): String? = when (target) {
    null, is SheetTarget.BlockEditor -> null
    is SheetTarget.NewBlock -> "new block"
    SheetTarget.QuickAdd -> "quick add"
    SheetTarget.AiPlan -> "AI planning"
    SheetTarget.MissedBlocks -> "missed blocks"
    SheetTarget.EndOfDayReview -> "day review"
    SheetTarget.FocusSettings -> "focus settings"
    SheetTarget.ExportData -> "export data"
    SheetTarget.ImportBackup -> "import backup"
    SheetTarget.WeeklySummary -> "weekly summary"
    SheetTarget.Diagnostics -> "diagnostics"
    SheetTarget.Logs -> "logs"
    is SheetTarget.Journal -> "journal"
    is SheetTarget.SleepLog -> "sleep log"
    is SheetTarget.ReviewDetails -> "review details"
}

private fun signedSidebarBackProgress(backEvent: BackEventCompat): Float =
    if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) {
        -backEvent.progress
    } else {
        backEvent.progress
    }
