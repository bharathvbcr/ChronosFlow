package com.chronosflow.feature.daydial

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.chronosflow.core.ui.shell.ChronosShellChromeSuppression
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ui.components.ChronosBackdrop
import com.chronosflow.core.ui.shell.ChronosSnackbarHost
import com.chronosflow.core.ui.shell.LocalChronosShellBottomInset
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.delegate.AppLockSettingsState
import com.chronosflow.feature.daydial.model.DayQuickItemsUiState
import com.chronosflow.feature.daydial.model.SheetTarget
import com.chronosflow.feature.daydial.model.SidebarPage
import com.chronosflow.feature.daydial.ui.SidebarPageContent
import java.io.File
import java.time.LocalDate

private const val LAUNCH_TARGET_INSIGHTS = "insights"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DayDialScreen(
    launchTarget: String? = null,
    initialFocusCapture: String? = null,
    requestedPrimaryTab: DayDialTab? = null,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenFocusScreen: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenMedication: () -> Unit,
    onOpenReview: () -> Unit,
    onSelectPrimaryTab: (DayDialTab) -> Unit,
    onOpenCommandPalette: (() -> Unit)? = null
) {
    if (launchTargetUsesLightweightSidebar(launchTarget)) {
        DayDialLightweightSidebarScreen(
            launchTarget = launchTarget,
            contentPadding = contentPadding,
            onOpenFocusScreen = onOpenFocusScreen,
            onOpenTasks = onOpenTasks,
            onOpenHabits = onOpenHabits,
            onOpenMedication = onOpenMedication,
            onOpenReview = onOpenReview,
            onSelectPrimaryTab = onSelectPrimaryTab,
            onOpenCommandPalette = onOpenCommandPalette
        )
        return
    }

    val dataViewModel = hiltViewModel<DayDialViewModel>()
    DayDialDataScreen(
        viewModel = dataViewModel,
        launchTarget = launchTarget,
        initialFocusCapture = initialFocusCapture,
        requestedPrimaryTab = requestedPrimaryTab,
        contentPadding = contentPadding,
        onOpenFocusScreen = onOpenFocusScreen,
        onOpenTasks = onOpenTasks,
        onOpenHabits = onOpenHabits,
        onOpenMedication = onOpenMedication,
        onOpenReview = onOpenReview,
        onSelectPrimaryTab = onSelectPrimaryTab,
        onOpenCommandPalette = onOpenCommandPalette
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DayDialDataScreen(
    viewModel: DayDialViewModel,
    launchTarget: String? = null,
    initialFocusCapture: String? = null,
    requestedPrimaryTab: DayDialTab? = null,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenFocusScreen: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenMedication: () -> Unit,
    onOpenReview: () -> Unit,
    onSelectPrimaryTab: (DayDialTab) -> Unit,
    onOpenCommandPalette: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val vmState = rememberDayDialViewModelState(viewModel)
    val uiState = rememberDayDialScreenUiState()
    val settings = rememberDayDialSettingsState()
    val appearanceMode = settings.appearanceMode
    val darkTheme = resolveDayDialDarkTheme(appearanceMode)
    val dayDialColorScheme = chronosColorScheme(
        context = context,
        dynamicColorEnabled = settings.dynamicColorEnabled,
        darkTheme = darkTheme,
        highContrastEnabled = settings.highContrastEnabled
    )
    val requestNotificationPermission = rememberNotificationPermissionRequester(context, viewModel, settings, uiState)
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    var calendarPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var calendarPermissionRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    var showCalendarPermissionRationale by rememberSaveable { mutableStateOf(false) }
    var pendingCalendarAction by remember { mutableStateOf<PendingCalendarAction?>(null) }
    val calendarPermissionStatus = remember(calendarPermissionRequested, calendarPermissionRefreshKey, activity) {
        resolveCalendarPermissionStatus(
            readGranted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
            writeGranted = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED,
            readShouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) == true,
            writeShouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR) == true,
            requestedBefore = calendarPermissionRequested
        )
    }
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                calendarPermissionRefreshKey = nextCalendarPermissionRefreshKey(calendarPermissionRefreshKey)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun performCalendarAction(action: PendingCalendarAction) {
        when (action) {
            PendingCalendarAction.SyncDeviceEvents -> {
                viewModel.syncCalendar()
                uiState.snackbarMessage = "Refreshing device calendar"
            }
            PendingCalendarAction.EnableExports -> {
                uiState.snackbarMessage = "Calendar exports connected"
            }
            is PendingCalendarAction.ExportBlock -> {
                viewModel.exportBlockToCalendar(action.blockId)
                uiState.snackbarMessage = "Connecting block to calendar"
            }
            is PendingCalendarAction.RefreshBlock -> {
                viewModel.refreshCalendarExport(action.blockId)
                uiState.snackbarMessage = "Refreshing linked calendar event"
            }
            is PendingCalendarAction.RemoveBlock -> {
                viewModel.removeCalendarExport(action.blockId)
                uiState.snackbarMessage = "Removing linked calendar event"
            }
        }
    }

    fun createDataExport() {
        viewModel.createDataExport { file ->
            runCatching {
                shareChronosDataExport(context, file)
            }.onSuccess {
                uiState.snackbarMessage = "Export ready: ${file.name}"
            }.onFailure { error ->
                uiState.snackbarMessage = error.message ?: "Unable to share data export"
            }
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        calendarPermissionRequested = true
        calendarPermissionRefreshKey = nextCalendarPermissionRefreshKey(calendarPermissionRefreshKey)
        showCalendarPermissionRationale = false
        val requestedAction = pendingCalendarAction
        pendingCalendarAction = null
        val readGranted = results[Manifest.permission.READ_CALENDAR] == true ||
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = results[Manifest.permission.WRITE_CALENDAR] == true ||
            context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (requestedAction != null && requestedAction.hasRequiredPermissions(readGranted, writeGranted)) {
            performCalendarAction(requestedAction)
        } else {
            val updatedStatus = resolveCalendarPermissionStatus(
                readGranted = readGranted,
                writeGranted = writeGranted,
                readShouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) == true,
                writeShouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR) == true,
                requestedBefore = true
            )
            uiState.snackbarMessage = calendarPermissionSnackbarMessage(updatedStatus)
        }
    }

    val requestCalendarAction: (PendingCalendarAction) -> Unit = { action ->
        pendingCalendarAction = action
        val permissionResolution = action.resolvePermission(calendarPermissionStatus, showCalendarPermissionRationale)
        if (!permissionResolution.keepsPendingCalendarAction()) {
            pendingCalendarAction = null
        }
        when (permissionResolution) {
            CalendarPermissionResolution.READY -> {
                showCalendarPermissionRationale = false
                performCalendarAction(action)
            }
            CalendarPermissionResolution.OPEN_SETTINGS -> {
                showCalendarPermissionRationale = false
                uiState.snackbarMessage = calendarPermissionSnackbarMessage(calendarPermissionStatus)
            }
            CalendarPermissionResolution.SHOW_RATIONALE -> {
                showCalendarPermissionRationale = true
            }
            CalendarPermissionResolution.REQUEST_PERMISSIONS -> {
                calendarPermissionRequested = true
                showCalendarPermissionRationale = false
                calendarPermissionLauncher.launch(action.requiredCalendarPermissions())
            }
        }
    }

    // Apply the launch target in the same composition pass so the AnimatedContent
    // transition starts immediately — LaunchedEffect would add a ~16ms coroutine-hop
    // delay before uiState.currentTab changed, causing a frozen frame before the slide.
    val lastAppliedTarget = remember { mutableStateOf<String?>("__unset__") }
    if (lastAppliedTarget.value != launchTarget) {
        lastAppliedTarget.value = launchTarget
        applyLaunchTarget(launchTarget, uiState, viewModel)
    }

    val renderedCurrentTab = dayDialRenderedCurrentTab(
        requestedPrimaryTab = requestedPrimaryTab,
        currentTab = uiState.currentTab
    )
    val renderedActiveSidebarPage = dayDialRenderedActiveSidebarPage(
        requestedPrimaryTab = requestedPrimaryTab,
        currentTab = uiState.currentTab,
        activeSidebarPage = uiState.activeSidebarPage
    )
    // Render from the requested shell tab in this frame, then sync backing state
    // after composition so the shell and content do not drift one selection apart.
    SideEffect {
        requestedPrimaryTab?.let { requestedTab ->
            if (uiState.currentTab != requestedTab) {
                applyRequestedPrimaryTab(requestedTab, uiState)
            }
        }
    }

    val sortedBlocks = remember(vmState.timeBlocks) { vmState.timeBlocks.sortedBy { it.startMinuteOfDay } }
    val templateState = rememberDayDialTemplateState(
        sortedBlocks = sortedBlocks,
        createBlock = { title, startMinute, duration, category ->
            viewModel.createQuickBlock(title, startMinute, duration, category)
        },
        showMessage = { uiState.snackbarMessage = it }
    )

    val restorePrevious = {
        if (vmState.canUndo) {
            viewModel.undo()
            uiState.snackbarMessage = "Restored previous state"
        } else {
            uiState.snackbarMessage = "Nothing to restore yet"
        }
    }


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val isViewingToday = vmState.selectedDate == LocalDate.now()
    val activeBlock = remember(sortedBlocks, vmState.currentMinute, isViewingToday) {
        findActiveBlock(sortedBlocks, vmState.currentMinute, forToday = isViewingToday)
    }
    val nextBlock = remember(sortedBlocks, vmState.currentMinute, isViewingToday) {
        findNextBlock(sortedBlocks, vmState.currentMinute, forToday = isViewingToday)
    }
    val missedBlocks = remember(vmState.timeBlocks, vmState.manualMissedIds) {
        vmState.timeBlocks.filter { it.id in vmState.manualMissedIds }
    }
    val recoverableServiceSessionId by viewModel.recoverableServiceSessionId.collectAsStateWithLifecycle()
    val focusRemainingSeconds = remember(vmState.focusSession, uiState.focusTick) {
        viewModel.focusRemainingSeconds(vmState.focusSession)
    }
    val focusElapsedSeconds = remember(vmState.focusSession, uiState.focusTick) {
        viewModel.focusElapsedSeconds(vmState.focusSession)
    }
    val hasGeneratedAiPlanResult = vmState.aiPlanResult != null ||
        vmState.explainPlan != null ||
        vmState.repairPlanResult != null ||
        vmState.suggestedBlocks.isNotEmpty()
    var aiPlanAutoDismissState by remember { mutableStateOf(AiPlanAutoDismissState()) }

    val dismissSheet = {
        uiState.activeSheet = null
        viewModel.onBlockSelected(null)
        viewModel.clearAiPlanPrefill()
    }

    LaunchedEffect(uiState.activeSheet, vmState.isGenerating, hasGeneratedAiPlanResult, aiPlanAutoDismissState) {
        val update = reduceAiPlanAutoDismiss(
            state = aiPlanAutoDismissState,
            activeSheet = uiState.activeSheet,
            isGenerating = vmState.isGenerating,
            hasGeneratedPlanResult = hasGeneratedAiPlanResult
        )
        if (update.nextState != aiPlanAutoDismissState) {
            aiPlanAutoDismissState = update.nextState
        }
        if (update.shouldDismiss) {
            dismissSheet()
        }
    }

    DayDialScreenEffects(viewModel, vmState, uiState, settings, snackbarHostState)

    ChronosShellChromeSuppression("day-sheet", uiState.activeSheet != null)

    DayDialFocusNotificationBridge(
        context = context,
        focusSession = vmState.focusSession,
        remainingSeconds = focusRemainingSeconds,
        recoverableServiceSessionId = recoverableServiceSessionId
    )

    val selectPrimaryTab: (DayDialTab) -> Unit = { tab ->
        if (uiState.currentTab != tab) {
            uiState.currentTab = tab
        }
        uiState.activeSidebarPage = null
        onSelectPrimaryTab(tab)
    }
    val backFallbackTab = dayDialBackFallbackTab(
        currentTab = renderedCurrentTab,
        launchTarget = launchTarget,
        activeSheet = uiState.activeSheet
    )
    BackHandler(enabled = backFallbackTab != null) {
        backFallbackTab?.let(selectPrimaryTab)
    }

    DayDialScreenChrome(
        viewModel = viewModel,
        vmState = vmState,
        uiState = uiState,
        settings = settings,
        templateState = templateState,
        colorScheme = dayDialColorScheme,
        darkTheme = darkTheme,
        appearanceMode = appearanceMode,
        sortedBlocks = sortedBlocks,
        missedBlocks = missedBlocks,
        activeBlock = activeBlock,
        nextBlock = nextBlock,
        initialFocusCapture = initialFocusCapture,
        contentPadding = contentPadding,
        focusRemainingSeconds = focusRemainingSeconds,
        focusElapsedSeconds = focusElapsedSeconds,
        snackbarHostState = snackbarHostState,
        renderedCurrentTab = renderedCurrentTab,
        renderedActiveSidebarPage = renderedActiveSidebarPage,
        requestNotificationPermission = requestNotificationPermission,
        calendarPermissionStatus = calendarPermissionStatus,
        showCalendarPermissionRationale = showCalendarPermissionRationale,
        onDismissCalendarPermissionRationale = {
            showCalendarPermissionRationale = false
            pendingCalendarAction = null
        },
        onRequestCalendarSync = { requestCalendarAction(PendingCalendarAction.SyncDeviceEvents) },
        onRequestCalendarExportAccess = { requestCalendarAction(PendingCalendarAction.EnableExports) },
        onOpenCalendarSettings = { openCalendarPermissionSettings(context) },
        onExportBlockToCalendar = { requestCalendarAction(PendingCalendarAction.ExportBlock(it)) },
        onRefreshCalendarExport = { requestCalendarAction(PendingCalendarAction.RefreshBlock(it)) },
        onRemoveCalendarExport = { requestCalendarAction(PendingCalendarAction.RemoveBlock(it)) },
        onCreateDataExport = ::createDataExport,
        calendarConnectionState = vmState.calendarConnectionState,
        restorePrevious = restorePrevious,
        onOpenFocusScreen = onOpenFocusScreen,
        onOpenTasks = onOpenTasks,
        onOpenHabits = onOpenHabits,
        onOpenMedication = onOpenMedication,
        onOpenReview = onOpenReview,
        onSelectPrimaryTab = selectPrimaryTab,
        onOpenCommandPalette = onOpenCommandPalette
    )

    MaterialTheme(colorScheme = dayDialColorScheme) {
        DayDialSheetHost(
            activeSheet = uiState.activeSheet,
            sheetState = sheetState,
            selectedBlock = vmState.selectedBlock,
            selectedDate = vmState.selectedDate,
            sortedBlocks = sortedBlocks,
            missedBlocks = missedBlocks,
            manualMissedBlockIds = vmState.manualMissedIds,
            suggestedBlocks = vmState.suggestedBlocks,
            review = vmState.review,
            privacyMode = vmState.privacyMode,
            isGenerating = vmState.isGenerating,
            genAiRuntimeStatus = vmState.genAiRuntimeStatus,
            aiPlanResult = vmState.aiPlanResult,
            explainPlan = vmState.explainPlan,
            explainPlanSource = vmState.explainPlanSource,
            repairPlanResult = vmState.repairPlanResult,
            aiPlanGoalPrefill = vmState.aiPlanGoalPrefill,
            aiPlanSuggestedGoals = vmState.aiPlanSuggestedGoals,
            focusElapsedSeconds = focusElapsedSeconds,
            syncStatus = settings.syncStatus,
            blockStartReminders = settings.blockStartReminders,
            breakReminders = settings.breakReminders,
            missedAlerts = settings.missedAlerts,
            endDayReviewReminder = settings.endDayReviewReminder,
            reminderScheduleStatus = vmState.reminderScheduleStatus,
            medicationReliabilityStatus = vmState.medicationReliabilityStatus,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            glassSurfacesEnabled = settings.glassSurfacesEnabled,
            appearanceMode = appearanceMode,
            reduceMotionEnabled = settings.reduceMotionEnabled,
            highContrastEnabled = settings.highContrastEnabled,
            calendarPermissionStatus = calendarPermissionStatus,
            showCalendarPermissionRationale = showCalendarPermissionRationale,
            calendarConnectionState = vmState.calendarConnectionState,
            onDismiss = dismissSheet,
            onDismissCalendarPermissionRationale = {
                showCalendarPermissionRationale = false
                pendingCalendarAction = null
            },
            onDeleteSelectedBlock = {
                viewModel.deleteSelectedBlock()
                uiState.activeSheet = null
            },
            onStartFocus = viewModel::startFocusSession,
            onAdjustFocus = { minutes ->
                if (minutes >= 0) viewModel.extendFocusSession(minutes) else viewModel.shortenFocusSession(-minutes)
            },
            onCreateBlock = { title, startMinute, duration, category ->
                viewModel.createQuickBlock(title, startMinute, duration, category)
                uiState.activeSheet = null
            },
            onGeneratePlan = { goals ->
                aiPlanAutoDismissState = AiPlanAutoDismissState(pendingDismiss = true)
                viewModel.clearAiPlanPrefill()
                viewModel.onAiPlanRequested(goals)
            },
            onApplyAiSuggestions = viewModel::applyAiSuggestions,
            onDismissAiSuggestions = viewModel::rejectAllAiSuggestions,
            onAcceptAiSuggestion = viewModel::acceptAiSuggestion,
            onRejectAiSuggestion = viewModel::rejectAiSuggestion,
            onModifyAiSuggestion = viewModel::modifyAiSuggestion,
            onSetPrivacyMode = viewModel::setPrivacyMode,
            onMarkComplete = viewModel::markBlockComplete,
            onMarkMissed = { blockId -> viewModel.markCurrentBlockMissed(blockId, true) },
            onUndoMissed = { blockId -> viewModel.markCurrentBlockMissed(blockId, false) },
            onDuplicateBlock = viewModel::duplicateBlock,
            onUpdateBlockDetails = viewModel::updateBlockDetails,
            onExportBlockToCalendar = { requestCalendarAction(PendingCalendarAction.ExportBlock(it)) },
            onRefreshCalendarExport = { requestCalendarAction(PendingCalendarAction.RefreshBlock(it)) },
            onRemoveCalendarExport = { requestCalendarAction(PendingCalendarAction.RemoveBlock(it)) },
            onOpenCalendarSettings = { openCalendarPermissionSettings(context) },
            dataExportState = vmState.dataExportState,
            onCreateDataExport = ::createDataExport,
            onImportBackup = { text ->
                templateState.importBackupText(text)
                uiState.activeSheet = null
            },
            onFinishFocus = { note ->
                viewModel.finishFocusSession(note)
                uiState.activeSheet = null
            },
            onEndDay = {
                viewModel.endDayReview(markMissed = missedBlocks.map { it.id })
                uiState.activeSheet = null
            },
            showMessage = { uiState.snackbarMessage = it }
        )
    }

    DayDialTemplateEditorSheet(templateState)
}

private fun applyLaunchTarget(
    target: String?,
    uiState: DayDialScreenUiState,
    viewModel: DayDialViewModel
) {
    sidebarPageForLaunchTarget(target)?.let { page ->
        uiState.activeSidebarPage = page
        return
    }

    when (target) {
        null -> Unit
        "today" -> {
            uiState.currentTab = DayDialTab.TODAY
            uiState.activeSidebarPage = null
        }
        "today-reset" -> {
            uiState.currentTab = DayDialTab.TODAY
            uiState.activeSidebarPage = null
            viewModel.selectDate(LocalDate.now())
        }
        "add-block" -> {
            uiState.currentTab = DayDialTab.TODAY
            uiState.activeSidebarPage = null
            uiState.activeSheet = SheetTarget.NewBlock(title = "New Block")
        }
        "plan" -> {
            uiState.currentTab = DayDialTab.PLAN
            uiState.activeSidebarPage = null
        }
        "focus-planner" -> {
            uiState.currentTab = DayDialTab.FOCUS
            uiState.activeSidebarPage = null
        }
        LAUNCH_TARGET_INSIGHTS -> {
            uiState.currentTab = DayDialTab.INSIGHTS
            uiState.activeSidebarPage = null
        }
    }
}

internal fun sidebarPageForLaunchTarget(target: String?): SidebarPage? =
    when (target) {
        "day-tools" -> SidebarPage.DAY_TOOLS
        "tasks" -> SidebarPage.TASKS
        "habits" -> SidebarPage.HABITS
        "medication" -> SidebarPage.MEDICATION
        "review" -> SidebarPage.REVIEW
        "templates" -> SidebarPage.TEMPLATES
        "ai-settings" -> SidebarPage.AI_SETTINGS
        "privacy-sync" -> SidebarPage.PRIVACY_SYNC
        "notifications" -> SidebarPage.NOTIFICATIONS
        "appearance" -> SidebarPage.APPEARANCE
        else -> null
    }

internal fun launchTargetUsesLightweightSidebar(target: String?): Boolean =
    sidebarPageForLaunchTarget(target) != null

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DayDialLightweightSidebarScreen(
    launchTarget: String?,
    contentPadding: PaddingValues,
    onOpenFocusScreen: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenMedication: () -> Unit,
    onOpenReview: () -> Unit,
    onSelectPrimaryTab: (DayDialTab) -> Unit,
    onOpenCommandPalette: (() -> Unit)?
) {
    val context = LocalContext.current
    val selectedDate = remember { LocalDate.now() }
    val settings = rememberDayDialSettingsState()
    val appearanceMode = settings.appearanceMode
    val darkTheme = resolveDayDialDarkTheme(appearanceMode)
    val colorScheme = chronosColorScheme(
        context = context,
        dynamicColorEnabled = settings.dynamicColorEnabled,
        darkTheme = darkTheme,
        highContrastEnabled = settings.highContrastEnabled
    )
    val activeSidebarPage = sidebarPageForLaunchTarget(launchTarget) ?: SidebarPage.DAY_TOOLS
    val snackbarHostState = remember { SnackbarHostState() }
    val contentBottomPadding = dayDialContentBottomPadding(
        routeBottomPadding = contentPadding.calculateBottomPadding(),
        shellBottomInset = LocalChronosShellBottomInset.current
    )
    val backgroundBrush = remember(colorScheme, darkTheme, settings.highContrastEnabled) {
        chronosBackgroundBrush(colorScheme, darkTheme, settings.highContrastEnabled)
    }

    ChronosShellChromeSuppression("day-lightweight-sidebar", true)

    MaterialTheme(colorScheme = colorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            if (
                shouldShowDayDialBackdrop(
                    glassSurfacesEnabled = settings.glassSurfacesEnabled,
                    highContrastEnabled = settings.highContrastEnabled,
                    activeSidebarPage = activeSidebarPage
                )
            ) {
                ChronosBackdrop(
                    theme = settings.backdropTheme,
                    darkTheme = darkTheme,
                    reduceMotionEnabled = settings.reduceMotionEnabled
                )
            }
            Scaffold(
                snackbarHost = { ChronosSnackbarHost(snackbarHostState) },
                modifier = Modifier.fillMaxSize(),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                topBar = {
                    DayDialTopBar(
                        activeSidebarPage = activeSidebarPage,
                        currentTab = DayDialTab.TODAY,
                        selectedDate = selectedDate,
                        canUndo = false,
                        canRedo = false,
                        darkTheme = darkTheme,
                        glassSurfacesEnabled = settings.glassSurfacesEnabled,
                        highContrastEnabled = settings.highContrastEnabled,
                        onSelectDate = {},
                        onMenuClick = {},
                        onBackClick = { onSelectPrimaryTab(DayDialTab.TODAY) },
                        onUndo = {},
                        onRedo = {},
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
                    SidebarPageContent(
                        page = activeSidebarPage,
                        selectedDate = selectedDate,
                        timeBlocks = emptyList(),
                        templates = emptyList(),
                        missedBlocks = emptyList(),
                        review = DailyReview(
                            plannedMinutes = 0,
                            actualMinutes = 0,
                            missedMinutes = 0,
                            completedBlocks = 0
                        ),
                        privacyMode = PrivacyMode.ON_DEVICE_ONLY,
                        previewOnDeviceModel = false,
                        compactMode = false,
                        onSelectDate = {},
                        onPrivacyModeSelected = {},
                        onPreviewOnDeviceModelChanged = {},
                        onCompactModeToggled = {},
                        planningStyle = settings.planningStyle,
                        onPlanningStyleSelected = { settings.planningStyle = it },
                        protectFocusBlocks = settings.protectFocusBlocks,
                        onProtectFocusChanged = { settings.protectFocusBlocks = it },
                        addBreaksAutomatically = settings.addBreaksAutomatically,
                        onAddBreaksAutomaticallyChanged = { settings.addBreaksAutomatically = it },
                        preserveManualBlocks = settings.preserveManualBlocks,
                        onPreserveManualBlocksChanged = { settings.preserveManualBlocks = it },
                        onGeneratePlan = { snackbarHostState.currentSnackbarData?.dismiss() },
                        onRebalance = {},
                        onFillGaps = {},
                        onClearDay = {},
                        onRestorePrevious = {},
                        onCopyPlan = {},
                        onSaveTemplate = {},
                        onApplyTemplate = {},
                        onEditTemplate = {},
                        onDuplicateTemplate = {},
                        onSaveCurrentAsTemplate = {},
                        onOpenPlannedBreakdown = {},
                        onOpenActualLog = {},
                        onOpenMissedRecovery = {},
                        onOpenMissed = {},
                        onOpenAiPlan = {},
                        syncCloud = settings.syncCloud,
                        syncStatus = settings.syncStatus,
                        onSyncCloudChanged = { settings.syncCloud = it },
                        onSyncNow = { settings.syncStatus = "Synced locally at launch" },
                        blockStartReminders = settings.blockStartReminders,
                        onBlockStartRemindersChanged = { settings.blockStartReminders = it },
                        breakReminders = settings.breakReminders,
                        onBreakRemindersChanged = { settings.breakReminders = it },
                        missedAlerts = settings.missedAlerts,
                        onMissedAlertsChanged = { settings.missedAlerts = it },
                        endDayReviewReminder = settings.endDayReviewReminder,
                        onEndDayReviewReminderChanged = { settings.endDayReviewReminder = it },
                        sleepScheduleEnabled = settings.sleepScheduleEnabled,
                        onSleepScheduleEnabledChanged = { settings.sleepScheduleEnabled = it },
                        sleepScheduleStartMinute = settings.sleepScheduleStartMinute,
                        onSleepScheduleStartMinuteChanged = { settings.sleepScheduleStartMinute = it },
                        sleepScheduleEndMinute = settings.sleepScheduleEndMinute,
                        onSleepScheduleEndMinuteChanged = { settings.sleepScheduleEndMinute = it },
                        reminderScheduleStatus = "Reminder scheduler will refresh after startup.",
                        medicationReliabilityStatus = "Medication reliability checks will refresh after startup.",
                        onRequestNotificationPermission = {},
                        calendarPermissionStatus = CalendarPermissionStatus(
                            readGranted = false,
                            writeGranted = false,
                            shouldShowRationale = false,
                            permanentlyDenied = false
                        ),
                        showCalendarPermissionRationale = false,
                        onDismissCalendarPermissionRationale = {},
                        onRequestCalendarSync = {},
                        onRequestCalendarExportAccess = {},
                        onOpenCalendarSettings = {},
                        onOpenExactAlarmSettings = {},
                        calendarConnectionState = CalendarConnectionState(),
                        dynamicColorEnabled = settings.dynamicColorEnabled,
                        onDynamicColorChanged = { settings.dynamicColorEnabled = it },
                        glassSurfacesEnabled = settings.glassSurfacesEnabled,
                        onGlassSurfacesChanged = { settings.glassSurfacesEnabled = it },
                        appearanceMode = appearanceMode,
                        onAppearanceModeSelected = { settings.appearanceModeValue = it.name },
                        backdropTheme = settings.backdropTheme,
                        onBackdropThemeSelected = { settings.backdropThemeValue = it.name },
                        reduceMotionEnabled = settings.reduceMotionEnabled,
                        onReduceMotionChanged = { settings.reduceMotionEnabled = it },
                        highContrastEnabled = settings.highContrastEnabled,
                        onHighContrastChanged = { settings.highContrastEnabled = it },
                        featureFlags = settings.featureFlags,
                        onHabitsFeatureEnabledChanged = { settings.habitsFeatureEnabled = it },
                        onMedicationFeatureEnabledChanged = { settings.medicationFeatureEnabled = it },
                        onReviewFeatureEnabledChanged = { settings.reviewFeatureEnabled = it },
                        onAiAdvisorFeatureEnabledChanged = { settings.aiAdvisorFeatureEnabled = it },
                        onOpenImport = {},
                        onOpenWeeklySummary = {},
                        onOpenDiagnostics = {},
                        selectedBlockId = null,
                        onOpenFocusScreen = onOpenFocusScreen,
                        onOpenTasks = onOpenTasks,
                        onOpenHabits = onOpenHabits,
                        onOpenMedication = onOpenMedication,
                        onOpenReview = onOpenReview,
                        appLockSettings = AppLockSettingsState(),
                        appLockCanAuthenticate = false,
                        onAppLockEnabledChanged = {},
                        onLockOnResumeChanged = {},
                        onRequireAuthMedicationChanged = {},
                        onRequireAuthReviewChanged = {},
                        onRequireAuthDataExportChanged = {},
                        dataExportRequiresAuth = false,
                        dataExportCanAuthenticate = false,
                        dataExportAuthError = null,
                        onUnlockDataExport = {},
                        dataExportState = DataExportState(),
                        onCreateDataExport = {},
                        onExportBlockToCalendar = {},
                        onRefreshCalendarExport = {},
                        onRemoveCalendarExport = {},
                        quickItems = DayQuickItemsUiState(),
                        onQuickTaskDone = {},
                        onQuickHabitDone = {},
                        onQuickMedicationTaken = { _, _ -> },
                        onQuickMedicationMissed = { _, _ -> },
                        onOpenBlock = {},
                        contentBottomPadding = contentBottomPadding,
                        showMessage = {}
                    )
                }
            }
        }
    }
}

internal fun applyRequestedPrimaryTab(
    requestedPrimaryTab: DayDialTab,
    uiState: DayDialScreenUiState
) {
    uiState.currentTab = requestedPrimaryTab
    uiState.activeSidebarPage = null
}

internal fun dayDialRenderedCurrentTab(
    requestedPrimaryTab: DayDialTab?,
    currentTab: DayDialTab
): DayDialTab = requestedPrimaryTab ?: currentTab

internal fun dayDialRenderedActiveSidebarPage(
    requestedPrimaryTab: DayDialTab?,
    currentTab: DayDialTab,
    activeSidebarPage: SidebarPage?
): SidebarPage? =
    if (requestedPrimaryTab != null && requestedPrimaryTab != currentTab) {
        null
    } else {
        activeSidebarPage
    }

internal fun applyRequestedPrimaryTabIfChanged(
    lastAppliedPrimaryTab: DayDialTab?,
    requestedPrimaryTab: DayDialTab?,
    uiState: DayDialScreenUiState
): DayDialTab? {
    if (lastAppliedPrimaryTab == requestedPrimaryTab) {
        return lastAppliedPrimaryTab
    }
    requestedPrimaryTab?.let { applyRequestedPrimaryTab(it, uiState) }
    return requestedPrimaryTab
}

internal fun dayDialBackFallbackTab(
    currentTab: DayDialTab,
    launchTarget: String?,
    activeSheet: SheetTarget?
): DayDialTab? =
    if (currentTab == DayDialTab.INSIGHTS &&
        launchTarget != LAUNCH_TARGET_INSIGHTS &&
        activeSheet == null
    ) {
        DayDialTab.TODAY
    } else {
        null
    }

internal data class AiPlanAutoDismissState(
    val pendingDismiss: Boolean = false,
    val sawGenerationStart: Boolean = false
)

internal data class AiPlanAutoDismissUpdate(
    val nextState: AiPlanAutoDismissState,
    val shouldDismiss: Boolean
)

internal fun reduceAiPlanAutoDismiss(
    state: AiPlanAutoDismissState,
    activeSheet: SheetTarget?,
    isGenerating: Boolean,
    hasGeneratedPlanResult: Boolean
): AiPlanAutoDismissUpdate {
    if (activeSheet != SheetTarget.AiPlan) {
        return AiPlanAutoDismissUpdate(
            nextState = AiPlanAutoDismissState(),
            shouldDismiss = false
        )
    }
    if (state.pendingDismiss && isGenerating && !state.sawGenerationStart) {
        return AiPlanAutoDismissUpdate(
            nextState = state.copy(sawGenerationStart = true),
            shouldDismiss = false
        )
    }
    if (state.pendingDismiss && state.sawGenerationStart && !isGenerating && hasGeneratedPlanResult) {
        return AiPlanAutoDismissUpdate(
            nextState = AiPlanAutoDismissState(),
            shouldDismiss = true
        )
    }
    return AiPlanAutoDismissUpdate(
        nextState = state,
        shouldDismiss = false
    )
}

private fun shareChronosDataExport(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "ChronosFlow data export")
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export ChronosFlow data"))
}
