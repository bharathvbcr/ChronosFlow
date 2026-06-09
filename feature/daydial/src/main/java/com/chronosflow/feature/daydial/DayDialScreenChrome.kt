package com.chronosflow.feature.daydial

import androidx.activity.BackEventCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.chronosflow.core.ui.shell.ChronosShellChromeSuppression
import com.chronosflow.core.ui.shell.LocalChronosShellBottomInset
import com.chronosflow.core.ui.shell.ChronosSnackbarHost
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronosflow.core.data.security.AppLockAuthResult
import com.chronosflow.core.data.security.SensitiveArea
import com.chronosflow.core.ui.components.ChronosBackdrop
import com.chronosflow.core.ui.components.ChronosPredictiveBackHandlerWithProgress
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.feature.daydial.model.AppearanceMode
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.SheetTarget
import com.chronosflow.feature.daydial.model.SidebarPage
import com.chronosflow.feature.daydial.ui.DayDialSidebar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    drawerOpen: Boolean,
    activeSidebarPage: SidebarPage?
): Boolean = drawerOpen || activeSidebarPage != null

internal fun sidebarPageForDrawerSelection(page: SidebarPage): SidebarPage = page

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
    onOpenMedication: () -> Unit,
    onOpenReview: () -> Unit,
    onSelectPrimaryTab: (DayDialTab) -> Unit,
    onOpenCommandPalette: (() -> Unit)?
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
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
        "day-drawer",
        shouldSuppressDayDialShellChrome(
            drawerOpen = drawerState.isOpen,
            activeSidebarPage = renderedActiveSidebarPage
        )
    )

    val onSidebarBack = {
        uiState.activeSidebarPage = null
        onSelectPrimaryTab(sidebarBackTargetTab(renderedCurrentTab))
    }

    // Track swipe-back progress for the sidebar-page predictive back preview.
    // A value of 0f means no gesture in progress; the signed -1f..1f value drives
    // horizontal translation so content follows the swipe edge in the same coordinate space.
    var sidebarBackProgress by remember { mutableFloatStateOf(0f) }
    val sidebarBackEnabled = renderedActiveSidebarPage != null && drawerState.currentValue == DrawerValue.Closed
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
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    DayDialSidebar(
                        activePage = renderedActiveSidebarPage,
                        privacyMode = vmState.privacyMode,
                        compactMode = vmState.compactMode,
                        onPrivacyModeSelected = viewModel::setPrivacyMode,
                        onCompactModeToggled = viewModel::onCompactModeToggled,
                        dark = darkTheme,
                        glassSurfacesEnabled = settings.glassSurfacesEnabled,
                        featureFlags = featureFlags,
                        versionLabel = appVersionLabel,
                        onPageSelected = { page ->
                            scope.launch { drawerState.close() }
                            uiState.activeSidebarPage = sidebarPageForDrawerSelection(page)
                        }
                    )
                }
            ) {
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
                        onSelectDate = viewModel::selectDate,
                            onMenuClick = { scope.launch { drawerState.open() } },
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
                            .padding(paddingValues)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            DayDialMainContent(
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
                                genAiRuntimeStatus = vmState.genAiRuntimeStatus,
                                insightsTabState = vmState.insightsTabState,
                                manualMissedBlockIds = vmState.manualMissedIds,
                                planningStyle = settings.planningStyle,
                                showRingGuide = settings.showRingGuide,
                                protectFocusBlocks = settings.protectFocusBlocks,
                                addBreaksAutomatically = settings.addBreaksAutomatically,
                                preserveManualBlocks = settings.preserveManualBlocks,
                                syncCloud = settings.syncCloud,
                                syncStatus = settings.syncStatus,
                                blockStartReminders = settings.blockStartReminders,
                                breakReminders = settings.breakReminders,
                                missedAlerts = settings.missedAlerts,
                                endDayReviewReminder = settings.endDayReviewReminder,
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
                                onMedicationFeatureEnabledChanged = { settings.medicationFeatureEnabled = it },
                                onReviewFeatureEnabledChanged = { settings.reviewFeatureEnabled = it },
                                onAiAdvisorFeatureEnabledChanged = { settings.aiAdvisorFeatureEnabled = it },
                                onPlanningStyleSelected = { settings.planningStyle = it },
                                onPreviewOnDeviceModelChanged = viewModel::setPreviewOnDeviceModel,
                                onShowRingGuideChanged = { settings.showRingGuide = it },
                                onProtectFocusChanged = { settings.protectFocusBlocks = it },
                                onAddBreaksAutomaticallyChanged = { settings.addBreaksAutomatically = it },
                                onPreserveManualBlocksChanged = { settings.preserveManualBlocks = it },
                                onSyncCloudChanged = { settings.syncCloud = it },
                                onSyncStatusChanged = { settings.syncStatus = it },
                                onBlockStartRemindersChanged = { settings.blockStartReminders = it },
                                onBreakRemindersChanged = { settings.breakReminders = it },
                                onMissedAlertsChanged = { settings.missedAlerts = it },
                                onEndDayReviewReminderChanged = { settings.endDayReviewReminder = it },
                                onSleepScheduleEnabledChanged = { settings.sleepScheduleEnabled = it },
                                onSleepScheduleStartMinuteChanged = { settings.sleepScheduleStartMinute = it },
                                onSleepScheduleEndMinuteChanged = { settings.sleepScheduleEndMinute = it },
                                onDynamicColorChanged = { settings.dynamicColorEnabled = it },
                                onGlassSurfacesChanged = { settings.glassSurfacesEnabled = it },
                                onAppearanceModeSelected = { settings.appearanceModeValue = it.name },
                                onBackdropThemeSelected = { settings.backdropThemeValue = it.name },
                                onReduceMotionChanged = { settings.reduceMotionEnabled = it },
                                onHighContrastChanged = { settings.highContrastEnabled = it },
                                onActiveSheetChanged = { uiState.activeSheet = it },
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
                                contentBottomPadding = bottomContentPadding,
                                onOpenPlanTab = {
                                    onSelectPrimaryTab(DayDialTab.PLAN)
                                    uiState.activeSidebarPage = null
                                },
                                showMessage = { uiState.snackbarMessage = it },
                                sidebarBackProgress = sidebarBackProgress,
                                currentTab = renderedCurrentTab,
                                activeSidebarPage = renderedActiveSidebarPage
                            )
                        }
                    }
                }
            }
            }
            }
        }
    }
}

private fun signedSidebarBackProgress(backEvent: BackEventCompat): Float =
    if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) {
        -backEvent.progress
    } else {
        backEvent.progress
    }
