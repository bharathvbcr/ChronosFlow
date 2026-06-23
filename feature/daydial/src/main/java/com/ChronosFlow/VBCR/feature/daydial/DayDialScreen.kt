package com.ChronosFlow.VBCR.feature.daydial

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.BackEventCompat
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.ChronosFlow.VBCR.core.ui.shell.ChronosShellChromeSuppression
import com.ChronosFlow.VBCR.core.ui.shell.ChronosShellViewedDateReporter
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.domain.diagnostics.AppEventLogEntry
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.notifications.NotificationPermissions
import com.ChronosFlow.VBCR.core.ui.components.ChronosBackdrop
import com.ChronosFlow.VBCR.core.ui.components.ChronosPredictiveBackHandlerWithProgress
import com.ChronosFlow.VBCR.core.ui.shell.ChronosSnackbarHost
import com.ChronosFlow.VBCR.core.ui.shell.LocalChronosShellBottomInset
import com.ChronosFlow.VBCR.feature.daydial.model.DayDialTab
import com.ChronosFlow.VBCR.feature.daydial.delegate.AppLockSettingsState
import com.ChronosFlow.VBCR.feature.daydial.model.DayQuickItemsUiState
import com.ChronosFlow.VBCR.feature.daydial.model.SheetTarget
import com.ChronosFlow.VBCR.feature.daydial.model.SidebarPage
import com.ChronosFlow.VBCR.feature.daydial.ui.SidebarPageContent
import com.ChronosFlow.VBCR.feature.daydial.ui.isFocusSessionActiveForTab
import java.io.File
import java.time.LocalDate

private const val LAUNCH_TARGET_INSIGHTS = "insights"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DayDialScreen(
    launchTarget: String? = null,
    launchTargetGeneration: Int = 0,
    initialFocusCapture: String? = null,
    requestedPrimaryTab: DayDialTab? = null,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenFocusScreen: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenMedication: () -> Unit,
    onOpenReview: () -> Unit,
    onSelectPrimaryTab: (DayDialTab) -> Unit,
    onOpenCommandPalette: (() -> Unit)? = null,
    isActiveSection: Boolean = true
) {
    if (launchTargetUsesLightweightSidebar(launchTarget)) {
        DayDialLightweightSidebarScreen(
            launchTarget = launchTarget,
            contentPadding = contentPadding,
            onOpenFocusScreen = onOpenFocusScreen,
            onOpenTasks = onOpenTasks,
            onOpenHabits = onOpenHabits,
            onOpenGoals = onOpenGoals,
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
        launchTargetGeneration = launchTargetGeneration,
        initialFocusCapture = initialFocusCapture,
        requestedPrimaryTab = requestedPrimaryTab,
        contentPadding = contentPadding,
        onOpenFocusScreen = onOpenFocusScreen,
        onOpenTasks = onOpenTasks,
        onOpenHabits = onOpenHabits,
            onOpenGoals = onOpenGoals,
        onOpenMedication = onOpenMedication,
        onOpenReview = onOpenReview,
        onSelectPrimaryTab = onSelectPrimaryTab,
        onOpenCommandPalette = onOpenCommandPalette,
        isActiveSection = isActiveSection
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DayDialDataScreen(
    viewModel: DayDialViewModel,
    launchTarget: String? = null,
    launchTargetGeneration: Int = 0,
    initialFocusCapture: String? = null,
    requestedPrimaryTab: DayDialTab? = null,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenFocusScreen: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenMedication: () -> Unit,
    onOpenReview: () -> Unit,
    onSelectPrimaryTab: (DayDialTab) -> Unit,
    onOpenCommandPalette: (() -> Unit)? = null,
    isActiveSection: Boolean = true
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
    // Hold the screen awake only while a focus session is actually running and the
    // user opted in, so an idle DayDial never drains the battery.
    val keepScreenOnDuringFocus = settings.keepScreenOnDuringFocus &&
        isFocusSessionActiveForTab(vmState.focusSession)
    val rootView = LocalView.current
    DisposableEffect(keepScreenOnDuringFocus) {
        rootView.keepScreenOn = keepScreenOnDuringFocus
        onDispose { rootView.keepScreenOn = false }
    }
    var calendarPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var calendarPermissionRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    var showCalendarPermissionRationale by rememberSaveable { mutableStateOf(false) }
    var pendingCalendarAction by rememberSaveable(stateSaver = PendingCalendarActionSaver) { mutableStateOf(null) }
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
        // Cold start already auto-syncs the day in view through the ViewModel's
        // per-date gate, so skip the first ON_START and only refresh on later
        // foreground returns — reopening the app then pulls calendar edits made
        // while it was backgrounded. The refresh is quiet (no-ops without calendar
        // permission, never raises a banner).
        var skipInitialForegroundSync = true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (skipInitialForegroundSync) {
                        skipInitialForegroundSync = false
                    } else {
                        viewModel.refreshCalendarForAppForeground()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    calendarPermissionRefreshKey = nextCalendarPermissionRefreshKey(calendarPermissionRefreshKey)
                }
                else -> Unit
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
    // Key on target + generation so repeating the same target (e.g. double-tapping
    // the Today tab again after browsing to another date) re-applies it.
    val lastAppliedTarget = remember { mutableStateOf("__unset__") }
    val launchTargetKey = "$launchTarget#$launchTargetGeneration"
    if (lastAppliedTarget.value != launchTargetKey) {
        lastAppliedTarget.value = launchTargetKey
        applyLaunchTarget(launchTarget, uiState, viewModel)
    }

    // `launchTarget` tracks the *live* requested Day target (re-applied on every
    // dayTargetGeneration bump above to drive in-place tab switching). The system-back
    // fallback instead needs the tab the screen was originally *routed* into: backing out
    // of a non-Today primary tab (Plan / Focus / Review) returns to Today, but a deep link
    // / cold start straight into a tab makes that tab the root, so back should exit to its
    // owner. Capturing the launch target once (and across process death) keeps an in-app
    // visit distinct from a deep link — otherwise the live target, which equals the current
    // tab whenever it's showing, would make every visit look like a deep link and close the
    // app on back (the original Review regression).
    val initialLaunchTarget = rememberSaveable { launchTarget ?: "" }
    val launchTab = dayDialTabForLaunchTarget(initialLaunchTarget)

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
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val routineTemplates = remember(routines) { routines.map { it.toTemplateBlueprint() } }
    val routineCompletions = remember(vmState.timeBlocks) {
        deriveRoutineCompletions(vmState.timeBlocks)
    }
    // One-time idempotent seeding: built-in templates were never persisted (custom templates
    // lived only in memory before the routines table existed), so seed the table from the
    // built-ins the first time it is empty. This makes every template DB-backed, which is what
    // lets Apply today/tomorrow route through ApplyRoutineToDateUseCase for built-ins too;
    // they stay read-only in the UI via the built-in id check in rememberDayDialTemplateState.
    var didImportLegacyRoutines by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(didImportLegacyRoutines) {
        if (!didImportLegacyRoutines) {
            viewModel.importLegacyRoutinesIfEmpty(
                legacyRoutines = builtInDayDialTemplates().map { it.toRoutine() }
            ) {
                didImportLegacyRoutines = true
            }
        }
    }
    val templateState = rememberDayDialTemplateState(
        sortedBlocks = sortedBlocks,
        customTemplates = routineTemplates,
        createBlock = { title, startMinute, duration, category ->
            viewModel.createQuickBlock(title, startMinute, duration, category)
        },
        onPersistTemplate = { template -> viewModel.persistRoutine(template.toRoutine()) },
        onDeleteTemplate = { routineId -> viewModel.deleteRoutineById(routineId) },
        onApplyTemplateToDate = { template, date ->
            // Step offsets are absolute minutes-of-day (anchor 0) — see RoutineTemplateConverters.
            viewModel.applyRoutineToDate(template.id, date, ROUTINE_TEMPLATE_ANCHOR_MINUTE)
        },
        routineCompletions = routineCompletions,
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

    // Open the end-of-day review as a local sheet so the in-content Review button
    // fires on every tap and reflects the date currently in view, instead of routing
    // through a single-top navigation that no-ops on a repeat tap and would reset the
    // screen back to today. Clearing the active sidebar page first means the retired
    // Review stub page (SidebarPage.REVIEW) never lingers as a blank surface behind the
    // sheet — dismissing the sheet returns to the Today dial, not an empty page.
    val openReviewSheet = {
        uiState.activeSidebarPage = null
        uiState.activeSheet = SheetTarget.EndOfDayReview
    }


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val isViewingToday = vmState.selectedDate == LocalDate.now()
    ChronosShellViewedDateReporter(vmState.selectedDate)
    val currentBlockNotificationEnabled by rememberPersistentBoolean(
        "notifications.currentBlockLive",
        false
    )
    LaunchedEffect(currentBlockNotificationEnabled) {
        viewModel.setCurrentBlockNotificationEnabled(currentBlockNotificationEnabled)
    }
    LaunchedEffect(vmState.timeBlocks) {
        if (currentBlockNotificationEnabled) {
            viewModel.refreshCurrentBlockNotification()
        }
    }
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

    // When Day stops being the active section (a sub-section is pushed on top — including the
    // predictive-back preview that composes Day while topLevelSection is still the sub-section),
    // drop any in-place sheet/selection so backing out returns to a clean dial rather than
    // reviving a stale sheet (whose dismiss BackHandler would otherwise swallow the back gesture).
    LaunchedEffect(isActiveSection) {
        if (!isActiveSection) {
            dismissSheet()
            uiState.activeSidebarPage = null
        }
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
        launchTab = launchTab,
        activeSheet = uiState.activeSheet
    )
    // Drive a swipe-following preview as a non-Today primary tab (Plan / Focus / Review) is
    // dismissed back to Today, so the back gesture is predictive instead of snapping on
    // release. Mirrors the sidebar-page predictive back handler in DayDialScreenChrome.
    var reviewBackProgress by remember { mutableFloatStateOf(0f) }
    ChronosPredictiveBackHandlerWithProgress(
        // Inert while a sub-section is on top (incl. the back-preview that composes Day): the back
        // belongs to NavDisplay's pop, not this in-place tab-fallback handler.
        enabled = isActiveSection && backFallbackTab != null,
        onBackStarted = { backEvent -> reviewBackProgress = signedReviewBackProgress(backEvent) },
        onBackProgressed = { backEvent -> reviewBackProgress = signedReviewBackProgress(backEvent) },
        onBackCancelled = { reviewBackProgress = 0f },
        onBackInvoked = {
            reviewBackProgress = 0f
            backFallbackTab?.let(selectPrimaryTab)
        }
    )

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
        reviewBackProgress = reviewBackProgress,
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
            onOpenGoals = onOpenGoals,
        onOpenMedication = onOpenMedication,
        onOpenReview = openReviewSheet,
        onSelectPrimaryTab = selectPrimaryTab,
        onOpenCommandPalette = onOpenCommandPalette
    )

    MaterialTheme(colorScheme = dayDialColorScheme) {
        DayDialSheetHost(
            // Gate the sheet inputs to null while Day is not the active section so its
            // ModalBottomSheet (and the dismiss BackHandler that would otherwise swallow a
            // sub-section back gesture) never composes during the predictive-back preview.
            activeSheet = if (isActiveSection) uiState.activeSheet else null,
            sheetState = sheetState,
            selectedBlock = if (isActiveSection) vmState.selectedBlock else null,
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
            focusRemainingSeconds = focusRemainingSeconds,
            focusSessionActive = isFocusSessionActiveForTab(vmState.focusSession),
            currentMinuteOfDay = vmState.currentMinute,
            syncStatus = settings.syncStatus,
            protectFocusBlocks = settings.protectFocusBlocks,
            onProtectFocusChanged = { settings.protectFocusBlocks = it },
            addBreaksAutomatically = settings.addBreaksAutomatically,
            onAddBreaksAutomaticallyChanged = { settings.addBreaksAutomatically = it },
            keepScreenOnDuringFocus = settings.keepScreenOnDuringFocus,
            onKeepScreenOnDuringFocusChanged = { settings.keepScreenOnDuringFocus = it },
            dailyFocusGoalMinutes = settings.dailyFocusGoalMinutes,
            onDailyFocusGoalMinutesChanged = { settings.dailyFocusGoalMinutes = it },
            defaultFocusBreakPreset = settings.defaultFocusBreakPreset,
            onDefaultFocusBreakPresetChanged = { settings.defaultFocusBreakPreset = it },
            blockStartReminders = settings.blockStartReminders,
            onBlockStartRemindersChanged = { settings.blockStartReminders = it },
            breakReminders = settings.breakReminders,
            onBreakRemindersChanged = { settings.breakReminders = it },
            missedAlerts = settings.missedAlerts,
            onMissedAlertsChanged = { settings.missedAlerts = it },
            endDayReviewReminder = settings.endDayReviewReminder,
            onEndDayReviewReminderChanged = { settings.endDayReviewReminder = it },
            notificationsReady = NotificationPermissions.areFocusNotificationsReady(context),
            onRequestNotificationPermission = requestNotificationPermission,
            reminderScheduleStatus = vmState.reminderScheduleStatus,
            medicationReliabilityStatus = vmState.medicationReliabilityStatus,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            glassSurfacesEnabled = settings.glassSurfacesEnabled,
            appearanceMode = appearanceMode,
            reduceMotionEnabled = settings.reduceMotionEnabled,
            highContrastEnabled = settings.highContrastEnabled,
            appEventLog = vmState.appEventLog,
            onClearLogs = viewModel::clearAppEventLog,
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
                uiState.showSnackbar("Block deleted", actionLabel = "Undo") {
                    viewModel.undo()
                }
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
            onMarkComplete = viewModel::markBlockComplete,
            onMarkMissed = { blockId -> viewModel.markCurrentBlockMissed(blockId, true) },
            onUndoMissed = { blockId -> viewModel.markCurrentBlockMissed(blockId, false) },
            onDuplicateBlock = { blockId ->
                // Message from the real planner outcome — the copy is rejected when the day has
                // no free slot, so the sheet must not claim success unconditionally.
                viewModel.duplicateBlock(blockId) { result ->
                    uiState.snackbarMessage = if (result is PlannerOperationResult.Applied) {
                        "Block duplicated"
                    } else {
                        result.message.ifBlank { "Couldn't duplicate block" }
                    }
                }
            },
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
            showMessage = { uiState.snackbarMessage = it },
            sleepTrack = vmState.sleepTrack,
            onSaveSleep = viewModel::saveSleepLog
        )
    }

    val routineAssistState by viewModel.routineAssistState.collectAsStateWithLifecycle()
    DayDialTemplateEditorSheet(
        templateState = templateState,
        assistState = routineAssistState,
        onRequestAssist = viewModel::requestRoutineAssist,
        onClearAssist = viewModel::clearRoutineAssist
    )
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
        // No feature-flag guard here (mirrors "add-block" and the Today-tab card handlers that set
        // these sheets directly): every entry point that produces a "journal"/"sleep" launch target
        // is already gated on the feature being enabled (the Add-FAB item, command, and reminder).
        // Re-reading the flag here regressed the Add-FAB path — its settings snapshot could resolve
        // the flag to false even while the FAB item was shown — so the sheet silently never opened.
        "journal" -> {
            uiState.currentTab = DayDialTab.TODAY
            uiState.activeSidebarPage = null
            uiState.activeSheet = SheetTarget.Journal(LocalDate.now())
        }
        "sleep" -> {
            uiState.currentTab = DayDialTab.TODAY
            uiState.activeSidebarPage = null
            uiState.activeSheet = SheetTarget.SleepLog(LocalDate.now())
        }
        // "review"/"review-sheet" are legacy aliases for pinned shortcuts and old
        // notifications; they now resolve to the unified Review page (the Insights tab),
        // consistent with every other review entry point.
        LAUNCH_TARGET_INSIGHTS, "review-sheet", "review" -> {
            uiState.currentTab = DayDialTab.INSIGHTS
            uiState.activeSidebarPage = null
        }
        "plan" -> {
            uiState.currentTab = DayDialTab.PLAN
            uiState.activeSidebarPage = null
        }
        "focus-planner" -> {
            uiState.currentTab = DayDialTab.FOCUS
            uiState.activeSidebarPage = null
        }
    }
}

internal fun sidebarPageForLaunchTarget(target: String?): SidebarPage? =
    when (target) {
        "day-tools" -> SidebarPage.DAY_TOOLS
        "tasks" -> SidebarPage.TASKS
        "reading" -> SidebarPage.READING_LIST
        "reading-inbox" -> SidebarPage.INBOX
        "habits" -> SidebarPage.HABITS
        "medication" -> SidebarPage.MEDICATION
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
    onOpenGoals: () -> Unit,
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
                ) {
                    SidebarPageContent(
                        page = activeSidebarPage,
                        contentTopPadding = paddingValues.calculateTopPadding(),
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
                        cloudAiEnabled = false,
                        onSelectDate = {},
                        onPrivacyModeSelected = {},
                        onPreviewOnDeviceModelChanged = {},
                        onCloudAiEnabledChanged = {},
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
                        sleepJournalLogReminder = settings.sleepJournalLogReminder,
                        onSleepJournalLogReminderChanged = { settings.sleepJournalLogReminder = it },
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
                        onGoalsFeatureEnabledChanged = { settings.goalsFeatureEnabled = it },
                        onMedicationFeatureEnabledChanged = { settings.medicationFeatureEnabled = it },
                        onReviewFeatureEnabledChanged = { settings.reviewFeatureEnabled = it },
                        onJournalFeatureEnabledChanged = { settings.journalFeatureEnabled = it },
                        onSleepFeatureEnabledChanged = { settings.sleepFeatureEnabled = it },
                        onAiAdvisorFeatureEnabledChanged = { settings.aiAdvisorFeatureEnabled = it },
                        onOpenImport = {},
                        onOpenWeeklySummary = {},
                        onOpenDiagnostics = {},
                        onOpenLogs = {},
                        selectedBlockId = null,
                        onOpenFocusScreen = onOpenFocusScreen,
                        onOpenTasks = onOpenTasks,
                        onOpenHabits = onOpenHabits,
            onOpenGoals = onOpenGoals,
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
                        contentBottomPadding = contentBottomPadding + paddingValues.calculateBottomPadding(),
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

/**
 * Signed -1f..1f swipe progress for the Review-tab predictive back preview. A right-edge
 * gesture translates content toward the left (negative) and a left-edge gesture toward the
 * right (positive), so the dismissing tab follows the swipe edge.
 */
private fun signedReviewBackProgress(backEvent: BackEventCompat): Float =
    if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) {
        -backEvent.progress
    } else {
        backEvent.progress
    }

/**
 * The Day tab a launch/routing target lands on, mirroring [applyLaunchTarget]'s tab assignment
 * (sidebar and one-shot-sheet targets keep the underlying Today tab). Used to recover the tab the
 * app was originally routed into so the back fallback can leave a deep-link/cold-start root to exit.
 */
internal fun dayDialTabForLaunchTarget(target: String?): DayDialTab =
    when (target) {
        "plan" -> DayDialTab.PLAN
        "focus-planner" -> DayDialTab.FOCUS
        LAUNCH_TARGET_INSIGHTS, "review-sheet", "review" -> DayDialTab.INSIGHTS
        else -> DayDialTab.TODAY
    }

/**
 * Which Day tab a system-back gesture should fall back to, or null to let back propagate (exit).
 * Today is the home/root, so back from any other primary tab returns to Today — except when that
 * tab is the one the app was launched/deep-linked into (then it's the root and back exits), or a
 * sheet is open (the sheet owns back).
 */
internal fun dayDialBackFallbackTab(
    currentTab: DayDialTab,
    launchTab: DayDialTab,
    activeSheet: SheetTarget?
): DayDialTab? =
    if (currentTab != DayDialTab.TODAY &&
        currentTab != launchTab &&
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
