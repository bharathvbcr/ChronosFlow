package com.chronosflow.feature.daydial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronosflow.core.ai.AssistNarrative
import com.chronosflow.core.ai.FocusNextBlockSuggestion
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.genai.GenAiRuntimeStatus
import com.chronosflow.core.domain.model.MoodEnergyCheckIn
import com.chronosflow.core.domain.planner.DialRing
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.ui.motion.ChronosMotionDefaults
import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.core.ui.motion.chronosBackPeek
import com.chronosflow.core.ui.motion.chronosCrossSectionTransitionSet
import com.chronosflow.core.ui.components.ChronosBackdrop
import com.chronosflow.core.ui.components.formatLastSyncedLabel
import com.chronosflow.core.ui.motion.ChronosTransitionSet
import com.chronosflow.core.ui.settings.ChronosBackdropTheme
import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import com.chronosflow.feature.daydial.model.AppearanceMode
import com.chronosflow.feature.daydial.model.InsightsTabUiState
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.ReviewDetailSection
import com.chronosflow.feature.daydial.model.SheetTarget
import com.chronosflow.feature.daydial.delegate.AppLockSettingsState
import com.chronosflow.feature.daydial.model.SidebarPage
import com.chronosflow.feature.daydial.ui.FocusTab
import com.chronosflow.feature.daydial.ui.focusCaptureBlockTitle
import com.chronosflow.feature.daydial.ui.focusCaptureBlockDurationMinutes
import com.chronosflow.feature.daydial.ui.focusCaptureBlockStartMinute
import com.chronosflow.feature.daydial.ui.InsightsTab
import com.chronosflow.feature.daydial.ui.PlanTab
import com.chronosflow.feature.daydial.ui.SidebarPageContent
import com.chronosflow.feature.daydial.ui.TodayTab
import java.time.LocalDate
import kotlin.math.roundToInt

private const val DayDialRouteEnterFadeDelayMillis = 0
private const val DayDialRouteEnterFadeDurationMillis = 1
private const val DayDialRouteExitFadeDurationMillis = 80
private const val DayDialRouteEnterInitialAlpha = 1f

internal fun shouldRenderPrimaryTabContent(
    activeSidebarPage: SidebarPage?,
    sidebarBackProgress: Float
): Boolean =
    activeSidebarPage == null || sidebarBackProgress != 0f


internal fun primaryTabContentTab(
    currentTab: DayDialTab,
    activeSidebarPage: SidebarPage?
): DayDialTab =
    if (activeSidebarPage != null) {
        sidebarBackTargetTab(currentTab)
    } else {
        currentTab
    }

internal fun primaryTabLayerZIndex(isActive: Boolean): Float = if (isActive) 1f else 0f

internal fun sidebarPageLayerZIndex(
    page: SidebarPage?,
    activeSidebarPage: SidebarPage? = page
): Float =
    when {
        page == null -> 0f
        page == activeSidebarPage -> 2f
        else -> 1.5f
    }

internal fun sidebarPagesForAnimatedLayer(
    activeSidebarPage: SidebarPage?,
    currentSidebarPage: SidebarPage?,
    targetSidebarPage: SidebarPage?
): List<SidebarPage> =
    listOfNotNull(currentSidebarPage, activeSidebarPage, targetSidebarPage).distinct()

internal fun dayDialPrimaryTabTransition(
    initialState: DayDialTab,
    targetState: DayDialTab,
    reducedMotion: Boolean
): ChronosTransitionSet =
    dayDialRouteTransition(
        direction = dayDialPrimaryTabTransitionDirection(initialState, targetState),
        reducedMotion = reducedMotion
    )

internal fun dayDialMoveInTravelMultiplier(direction: ChronosTransitionDirection): Int =
    when (direction) {
        ChronosTransitionDirection.Forward -> 1
        ChronosTransitionDirection.Backward -> -1
        ChronosTransitionDirection.Neutral -> 0
    }

internal fun dayDialRouteEnterFadeDelayMillis(): Int = DayDialRouteEnterFadeDelayMillis

internal fun dayDialRouteEnterInitialAlpha(reducedMotion: Boolean): Float =
    if (reducedMotion) 0f else DayDialRouteEnterInitialAlpha

internal fun dayDialRouteEnterFadeDurationMillis(routeDurationMillis: Int): Int =
    DayDialRouteEnterFadeDurationMillis.coerceAtMost(routeDurationMillis)

internal fun dayDialRouteExitFadeDurationMillis(routeDurationMillis: Int): Int =
    DayDialRouteExitFadeDurationMillis.coerceAtMost(routeDurationMillis)

@Composable
private fun DayDialPrimaryTabLayer(
    tab: DayDialTab,
    renderedPrimaryTab: DayDialTab,
    transition: Transition<DayDialTab>,
    reduceMotionEnabled: Boolean,
    backProgress: Float = 0f,
    content: @Composable (Boolean) -> Unit
) {
    val isActive = tab == renderedPrimaryTab
    val routeTransition = dayDialPrimaryTabTransition(
        initialState = transition.currentState,
        targetState = transition.targetState,
        reducedMotion = reduceMotionEnabled
    )

    AnimatedVisibility(
        visible = isActive,
        enter = routeTransition.enter,
        exit = routeTransition.exit,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(primaryTabLayerZIndex(isActive))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    chronosBackPeek(backProgress = backProgress, reducedMotion = reduceMotionEnabled)
                }
        ) {
            content(isActive)
        }
    }
}

@Composable
private fun DayDialSidebarPageLayer(
    page: SidebarPage,
    activeSidebarPage: SidebarPage?,
    transition: Transition<SidebarPage?>,
    reduceMotionEnabled: Boolean,
    sidebarBackProgress: Float,
    content: @Composable () -> Unit
) {
    val isActive = page == activeSidebarPage
    val transitionDirection = dayDialSidebarPageTransitionDirection(
        initialState = transition.currentState,
        targetState = transition.targetState
    )
    // Sidebar pages share the SAME cross-section motion as the NavDisplay routes
    // (chronosCrossSectionTransitionSet) so every floating-sidebar destination matches whether it
    // opens a full route (Meds/Tasks/Habits/Goals) or renders in-place (Calendar, settings, …).
    val routeTransition = chronosCrossSectionTransitionSet(
        direction = transitionDirection,
        reducedMotion = reduceMotionEnabled
    )

    AnimatedVisibility(
        visible = isActive,
        enter = routeTransition.enter,
        exit = routeTransition.exit,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(
                sidebarPageLayerZIndex(
                    page = page,
                    activeSidebarPage = activeSidebarPage
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    chronosBackPeek(backProgress = sidebarBackProgress, reducedMotion = reduceMotionEnabled)
                }
        ) {
            content()
        }
    }
}

@Composable
internal fun DayDialMainContent(
    viewModel: DayDialViewModel,
    selectedDate: LocalDate,
    sortedBlocks: List<TimeBlockUiModel>,
    freeTime: List<TimeRangeUi>,
    currentMinute: Int,
    selectedBlockId: String?,
    hapticCue: PlannerHapticCue,
    review: DailyReview,
    privacyMode: PrivacyMode,
    previewOnDeviceModel: Boolean,
    compactMode: Boolean,
    compactWindowStart: Int,
    missedBlocks: List<TimeBlockUiModel>,
    suggestedBlocks: List<TimeBlockUiModel>,
    templateState: DayDialTemplateState,
    focusedBlock: TimeBlockUiModel?,
    selectedBlock: TimeBlockUiModel?,
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    initialFocusCapture: String? = null,
    focusSession: FocusExecutionState,
    focusRemainingSeconds: Long,
    focusElapsedSeconds: Long,
    focusRestoredMessage: String?,
    moodEnergyCheckIns: List<MoodEnergyCheckIn>,
    moodCheckInCoaching: AssistNarrative? = null,
    focusGuidance: AssistNarrative? = null,
    focusNextBlockSuggestion: FocusNextBlockSuggestion? = null,
    sleepTrack: com.chronosflow.core.domain.model.SleepTrack? = null,
    journalEntry: com.chronosflow.core.domain.model.JournalEntry? = null,
    genAiRuntimeStatus: GenAiRuntimeStatus,
    insightsTabState: InsightsTabUiState,
    manualMissedBlockIds: Set<String>,
    planningStyle: String,
    showRingGuide: Boolean,
    protectFocusBlocks: Boolean,
    addBreaksAutomatically: Boolean,
    defaultFocusBreakPreset: Int = 0,
    preserveManualBlocks: Boolean,
    syncCloud: Boolean,
    syncStatus: String,
    blockStartReminders: Boolean,
    breakReminders: Boolean,
    missedAlerts: Boolean,
    endDayReviewReminder: Boolean,
    sleepJournalLogReminder: Boolean,
    sleepScheduleEnabled: Boolean,
    sleepScheduleStartMinute: Int,
    sleepScheduleEndMinute: Int,
    reminderScheduleStatus: String,
    medicationReliabilityStatus: String,
    dynamicColorEnabled: Boolean,
    glassSurfacesEnabled: Boolean,
    appearanceMode: AppearanceMode,
    backdropTheme: ChronosBackdropTheme,
    reduceMotionEnabled: Boolean,
    highContrastEnabled: Boolean,
    featureFlags: ChronosFeatureFlags,
    onPlanningStyleSelected: (String) -> Unit,
    onPreviewOnDeviceModelChanged: (Boolean) -> Unit,
    onProtectFocusChanged: (Boolean) -> Unit,
    onAddBreaksAutomaticallyChanged: (Boolean) -> Unit,
    onPreserveManualBlocksChanged: (Boolean) -> Unit,
    onSyncCloudChanged: (Boolean) -> Unit,
    onSyncStatusChanged: (String) -> Unit,
    onBlockStartRemindersChanged: (Boolean) -> Unit,
    onBreakRemindersChanged: (Boolean) -> Unit,
    onMissedAlertsChanged: (Boolean) -> Unit,
    onEndDayReviewReminderChanged: (Boolean) -> Unit,
    onSleepJournalLogReminderChanged: (Boolean) -> Unit,
    onSleepScheduleEnabledChanged: (Boolean) -> Unit,
    onSleepScheduleStartMinuteChanged: (Int) -> Unit,
    onSleepScheduleEndMinuteChanged: (Int) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onGlassSurfacesChanged: (Boolean) -> Unit,
    onAppearanceModeSelected: (AppearanceMode) -> Unit,
    onBackdropThemeSelected: (ChronosBackdropTheme) -> Unit,
    onReduceMotionChanged: (Boolean) -> Unit,
    onHighContrastChanged: (Boolean) -> Unit,
    onHabitsFeatureEnabledChanged: (Boolean) -> Unit,
    onGoalsFeatureEnabledChanged: (Boolean) -> Unit,
    onMedicationFeatureEnabledChanged: (Boolean) -> Unit,
    onReviewFeatureEnabledChanged: (Boolean) -> Unit,
    onJournalFeatureEnabledChanged: (Boolean) -> Unit,
    onSleepFeatureEnabledChanged: (Boolean) -> Unit,
    onAiAdvisorFeatureEnabledChanged: (Boolean) -> Unit,
    onActiveSheetChanged: (SheetTarget?) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    calendarPermissionStatus: CalendarPermissionStatus,
    showCalendarPermissionRationale: Boolean,
    onDismissCalendarPermissionRationale: () -> Unit,
    onRequestCalendarSync: () -> Unit,
    onRequestCalendarExportAccess: () -> Unit,
    onOpenCalendarSettings: () -> Unit,
    onExportBlockToCalendar: (String) -> Unit,
    onRefreshCalendarExport: (String) -> Unit,
    onRemoveCalendarExport: (String) -> Unit,
    dataExportState: DataExportState,
    onCreateDataExport: () -> Unit,
    calendarConnectionState: CalendarConnectionState,
    onRestorePrevious: () -> Unit,
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
    contentBottomPadding: Dp = 0.dp,
    onOpenPlanTab: () -> Unit,
    showMessage: (String) -> Unit,
    onShowUndoSnackbar: (message: String, onUndo: () -> Unit) -> Unit = { message, _ -> showMessage(message) },
    sidebarBackProgress: Float = 0f,
    reviewBackProgress: Float = 0f,
    currentTab: DayDialTab = DayDialTab.TODAY,
    activeSidebarPage: SidebarPage? = null,
    scaffoldPadding: PaddingValues = PaddingValues()
) {
    val darkTheme = resolveDayDialDarkTheme(appearanceMode)
    val colorScheme = MaterialTheme.colorScheme
    val backgroundBrush = remember(colorScheme, darkTheme, highContrastEnabled) {
        chronosBackgroundBrush(colorScheme, darkTheme, highContrastEnabled)
    }
    val isViewingToday = selectedDate == LocalDate.now()
    val quickItems by viewModel.dayQuickItems.collectAsStateWithLifecycle()
    val lastCalendarSyncAtMillis by viewModel.lastCalendarSyncAtMillis.collectAsStateWithLifecycle()
    val focusAccentBlockId = focusSession.blockId ?: focusedBlock?.id ?: selectedBlock?.id ?: activeBlock?.id
    val focusCachedAccent = viewModel.focusMoodAccentFor(focusAccentBlockId)
    val sidebarPageTransition = updateTransition(
        targetState = activeSidebarPage,
        label = "dayDialSidebarPages"
    )
    val renderedPrimaryTab = primaryTabContentTab(currentTab, activeSidebarPage)
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (shouldRenderPrimaryTabContent(activeSidebarPage, sidebarBackProgress)) {
            val primaryTabTransition = updateTransition(
                targetState = renderedPrimaryTab,
                label = "dayDialPrimaryTabs"
            )
            DayDialPrimaryTabLayer(
                tab = DayDialTab.TODAY,
                renderedPrimaryTab = renderedPrimaryTab,
                transition = primaryTabTransition,
                reduceMotionEnabled = reduceMotionEnabled
            ) { isPageActive ->
                TodayTab(
                        timeBlocks = sortedBlocks,
                        freeTime = freeTime,
                        currentMinute = currentMinute,
                        isViewingToday = isViewingToday,
                        isPageActive = isPageActive,
                        selectedBlockId = selectedBlockId,
                        hapticCue = hapticCue,
                        review = review,
                        activeBlock = activeBlock,
                        nextBlock = nextBlock,
                        missedBlocks = missedBlocks,
                        manualMissedBlockIds = manualMissedBlockIds,
                        compactMode = compactMode,
                        compactWindowStart = compactWindowStart,
                        nightStartMinute = sleepScheduleStartMinute,
                        nightEndMinute = sleepScheduleEndMinute,
                        glassSurfacesEnabled = glassSurfacesEnabled,
                        highContrastEnabled = highContrastEnabled,
                        showRingGuide = showRingGuide,
                        habitsFeatureEnabled = featureFlags.habitsEnabled,
                        medicationFeatureEnabled = featureFlags.medicationEnabled,
                        reviewFeatureEnabled = featureFlags.reviewEnabled,
                        onBlockSelected = viewModel::onBlockSelected,
                        onBlockDragStarted = viewModel::onBlockDragStarted,
                        onBlockMoved = viewModel::onBlockDragMoved,
                        onBlockMoveCommitted = viewModel::onBlockMoveCommitted,
                        onBlockResize = viewModel::onBlockResize,
                        onBlockResizeCommitted = viewModel::onBlockResizeCommitted,
                        onEmptyAreaSelected = { minute ->
                            openNewBlockSheet(
                                viewModel = viewModel,
                                onActiveSheetChanged = onActiveSheetChanged,
                                startMinute = minute,
                                title = "Open Time"
                            )
                        },
                        onEmptyRingSelected = { minute, ring ->
                            val (title, category) = quickCreateDefaultsForRing(ring)
                            openNewBlockSheet(
                                viewModel = viewModel,
                                onActiveSheetChanged = onActiveSheetChanged,
                                startMinute = minute,
                                title = title,
                                category = category
                            )
                        },
                        onDragCancel = viewModel::onBlockDragCancelled,
                        onToggleCompactMode = viewModel::onCompactModeToggled,
                        onWindowBack = viewModel::moveWindowBack,
                        onWindowForward = viewModel::moveWindowForward,
                        onCenterWindowOnNow = viewModel::centerWindowOnNow,
                        onStartFocus = viewModel::startFocusSession,
                        onCompleteBlock = viewModel::markBlockComplete,
                        onUndoMissed = { blockId -> viewModel.markCurrentBlockMissed(blockId, false) },
                        onShowMissed = { onActiveSheetChanged(SheetTarget.MissedBlocks) },
                        onOpenPlanTab = onOpenPlanTab,
                        onOpenReview = onOpenReview,
                        onOpenTasks = onOpenTasks,
                        onOpenHabits = onOpenHabits,
                        onOpenMedication = onOpenMedication,
                        quickItems = quickItems,
                        onQuickTaskDone = viewModel::completeDayQuickTask,
                        onQuickHabitDone = viewModel::completeDayQuickHabit,
                        onQuickMedicationTaken = viewModel::markDayQuickMedicationTaken,
                        onQuickMedicationMissed = viewModel::markDayQuickMedicationMissed,
                        onQuickContextActionFailed = showMessage,
                        onOpenPlanned = { onActiveSheetChanged(SheetTarget.ReviewDetails(ReviewDetailSection.PLANNED)) },
                        onOpenActual = { onActiveSheetChanged(SheetTarget.ReviewDetails(ReviewDetailSection.ACTUAL)) },
                        onOpenMissedRecovery = { onActiveSheetChanged(SheetTarget.ReviewDetails(ReviewDetailSection.MISSED)) },
                        showSleepPrompt = featureFlags.sleepEnabled &&
                            isViewingToday &&
                            sleepTrack == null &&
                            currentMinute < 12 * 60,
                        onLogSleep = { onActiveSheetChanged(SheetTarget.SleepLog(LocalDate.now())) },
                        contentTopPadding = scaffoldPadding.calculateTopPadding(),
                        contentBottomPadding = contentBottomPadding + scaffoldPadding.calculateBottomPadding(),
                        onAiStripAction = { label ->
                            when (label) {
                                "Fill gaps" -> {
                                    viewModel.fillEmptyTime(addBreaksAutomatically)
                                    onActiveSheetChanged(SheetTarget.AiPlan)
                                }
                                "Rebalance" -> viewModel.rebalanceDay()
                                "Add breaks" -> viewModel.createQuickBlock(title = "Break", durationMinutes = 15)
                                "Protect focus" -> {
                                    viewModel.onAiPlanRequested(listOf("protect focus"))
                                    onActiveSheetChanged(SheetTarget.AiPlan)
                                }
                            }
                        }
                    )
            }

            DayDialPrimaryTabLayer(
                tab = DayDialTab.PLAN,
                renderedPrimaryTab = renderedPrimaryTab,
                transition = primaryTabTransition,
                reduceMotionEnabled = reduceMotionEnabled
            ) {
                PlanTab(
                        timeBlocks = sortedBlocks,
                        suggestedBlocks = suggestedBlocks,
                        templates = templateState.allTemplates,
                        selectedDate = selectedDate,
                        onSelectDate = viewModel::selectDate,
                        onSyncCalendar = onRequestCalendarSync,
                        calendarReadGranted = calendarPermissionStatus.readGranted,
                        calendarSyncInProgress = calendarConnectionState.isWorking,
                        lastSyncedLabel = formatLastSyncedLabel(
                            lastCalendarSyncAtMillis,
                            System.currentTimeMillis()
                        ),
                        onAiPlanRequested = { viewModel.onAiPlanRequested(listOf("balanced")) },
                        onApplyAiSuggestions = viewModel::applyAiSuggestions,
                        onAcceptAiSuggestion = viewModel::acceptAiSuggestion,
                        onRejectAiSuggestion = viewModel::rejectAiSuggestion,
                        onRebalanceDay = viewModel::rebalanceDay,
                        onExplainPlan = viewModel::explainCurrentPlan,
                        onRepairConflicts = { conflictDescription ->
                            viewModel.repairConflictingPlan(conflictDescription)
                            onActiveSheetChanged(SheetTarget.AiPlan)
                        },
                        onOpenAiSheet = { onActiveSheetChanged(SheetTarget.AiPlan) },
                        onOpenTasks = onOpenTasks,
                        onOpenHabits = onOpenHabits,
                        onOpenMedication = onOpenMedication,
                        habitsFeatureEnabled = featureFlags.habitsEnabled,
                        medicationFeatureEnabled = featureFlags.medicationEnabled,
                        quickItems = quickItems,
                        onQuickTaskDone = viewModel::completeDayQuickTask,
                        onQuickHabitDone = viewModel::completeDayQuickHabit,
                        onQuickMedicationTaken = viewModel::markDayQuickMedicationTaken,
                        onQuickMedicationMissed = viewModel::markDayQuickMedicationMissed,
                        onCreateBlock = {
                            openNewBlockSheet(
                                viewModel = viewModel,
                                onActiveSheetChanged = onActiveSheetChanged,
                                startMinute = currentMinute
                            )
                        },
                        onBlockSelected = viewModel::onBlockSelected,
                        onDeleteBlock = { blockId ->
                            viewModel.deleteBlock(blockId)
                            onShowUndoSnackbar("Block deleted") { viewModel.undo() }
                        },
                        onDuplicateBlock = { blockId ->
                            // Report the real planner outcome: the copy can be rejected when the
                            // day has no free slot for it, so don't claim success unconditionally.
                            viewModel.duplicateBlock(blockId) { result ->
                                showMessage(
                                    if (result is PlannerOperationResult.Applied) {
                                        "Block duplicated"
                                    } else {
                                        result.message.ifBlank { "Couldn't duplicate block" }
                                    }
                                )
                            }
                        },
                        onApplyTemplate = templateState.applyTemplate,
                        onFillGaps = {
                            viewModel.fillEmptyTime(addBreaksAutomatically)
                            onActiveSheetChanged(SheetTarget.AiPlan)
                        },
                        contentTopPadding = scaffoldPadding.calculateTopPadding(),
                        contentBottomPadding = contentBottomPadding + scaffoldPadding.calculateBottomPadding()
                    )
            }

            DayDialPrimaryTabLayer(
                tab = DayDialTab.FOCUS,
                renderedPrimaryTab = renderedPrimaryTab,
                transition = primaryTabTransition,
                reduceMotionEnabled = reduceMotionEnabled
            ) {
                val focusBlock = focusedBlock ?: selectedBlock ?: activeBlock
                FocusTab(
                            selectedBlock = focusBlock,
                            nextBlock = nextBlock,
                            initialFocusCapture = initialFocusCapture,
                            privacyMode = privacyMode,
                            focusSession = focusSession,
                            remainingSeconds = focusRemainingSeconds,
                            elapsedSeconds = focusElapsedSeconds,
                            reduceMotionEnabled = reduceMotionEnabled,
                            highContrastEnabled = highContrastEnabled,
                            defaultBreakPresetIndex = defaultFocusBreakPreset,
                            onSaveMoodEnergyCheckIn = viewModel::saveMoodEnergyCheckIn,
                            onStart = viewModel::startFocusSession,
                            onStartWithBreaks = { blockId, workMinutes, breakMinutes ->
                                viewModel.startFocusSession(blockId, workMinutes, breakMinutes)
                            },
                            onMarkBlockComplete = viewModel::markBlockComplete,
                            onAdvancePhase = viewModel::advanceFocusPhase,
                            onPause = viewModel::pauseFocusSession,
                            onResume = viewModel::resumeFocusSession,
                            onFinish = { viewModel.finishFocusSession("Complete") },
                            onSkip = viewModel::skipFocusSession,
                            onExtend = { viewModel.extendFocusSession() },
                            onShorten = { viewModel.shortenFocusSession() },
                            onAdjustByMinutes = { minutes ->
                                if (minutes >= 0) viewModel.extendFocusSession(minutes) else viewModel.shortenFocusSession(-minutes)
                            },
                            onInjectBreak = viewModel::injectBreakNow,
                            onEndBreak = viewModel::endBreakEarly,
                            onOpenSettings = { onActiveSheetChanged(SheetTarget.FocusSettings) },
                            onOpenPlanTab = onOpenPlanTab,
                            onPlanCapturedFocus = { capture ->
                                openNewBlockSheet(
                                    viewModel = viewModel,
                                    onActiveSheetChanged = onActiveSheetChanged,
                                    startMinute = focusCaptureBlockStartMinute(capture) ?: currentMinute,
                                    title = focusCaptureBlockTitle(capture),
                                    category = "WORK",
                                    durationMinutes = focusCaptureBlockDurationMinutes(capture)
                                )
                            },
                            onEndDay = { onActiveSheetChanged(SheetTarget.EndOfDayReview) },
                            sessionResumedBanner = focusRestoredMessage,
                            onDismissSessionResumedBanner = viewModel::clearFocusRestoredMessage,
                            moodEnergyCheckIns = moodEnergyCheckIns,
                            moodCheckInCoaching = moodCheckInCoaching,
                            focusGuidance = focusGuidance,
                            focusNextBlockSuggestion = focusNextBlockSuggestion,
                            onRequestNextFocusSuggestion = viewModel::refreshNextFocusSuggestion,
                            onRefreshFocusGuidance = { remaining ->
                                viewModel.refreshFocusGuidance(
                                    remainingSeconds = remaining,
                                    nextBlockTitle = nextBlock?.title
                                )
                            },
                            onClearFocusGuidance = viewModel::clearFocusGuidance,
                            genAiRuntimeStatus = genAiRuntimeStatus,
                            cachedMoodScore = focusCachedAccent.first,
                            cachedEnergyScore = focusCachedAccent.second,
                            linkedBlockManuallyMissed = focusBlock?.id in manualMissedBlockIds,
                            contentTopPadding = scaffoldPadding.calculateTopPadding(),
                            contentBottomPadding = contentBottomPadding + scaffoldPadding.calculateBottomPadding()
                        )
            }

            DayDialPrimaryTabLayer(
                tab = DayDialTab.INSIGHTS,
                renderedPrimaryTab = renderedPrimaryTab,
                transition = primaryTabTransition,
                reduceMotionEnabled = reduceMotionEnabled,
                backProgress = reviewBackProgress
            ) {
                // Collected inside the Insights layer (composed only while the tab is visible) so the
                // five trend Room sources are subscribed only on this tab, not the whole day screen.
                val insightsTrends by viewModel.insightsTrends.collectAsStateWithLifecycle()
                val insightsTrendRange by viewModel.trendRangeDays.collectAsStateWithLifecycle()
                InsightsTab(
                        review = review,
                        timeBlocks = sortedBlocks,
                        missedCount = missedBlocks.size,
                        reviewInsights = insightsTabState.reviewInsights,
                        recommendations = insightsTabState.recommendations,
                        assistSnapshot = insightsTabState.assistSnapshot,
                        digest = insightsTabState.digest,
                        isRefreshing = insightsTabState.isRefreshing,
                        trendRangeDays = insightsTrendRange,
                        trends = insightsTrends,
                        journalEntry = journalEntry,
                        sleepTrack = sleepTrack,
                        onTrendRangeSelected = viewModel::setInsightsTrendRange,
                        onOpenJournal = { onActiveSheetChanged(SheetTarget.Journal(selectedDate)) },
                        onOpenSleepLog = { onActiveSheetChanged(SheetTarget.SleepLog(selectedDate)) },
                        journalEnabled = featureFlags.journalEnabled,
                        sleepEnabled = featureFlags.sleepEnabled,
                        onRefreshRecommendations = viewModel::refreshInsightsRecommendations,
                        onApplyRecommendation = { recommendation ->
                            viewModel.applyInsightRecommendation(
                                recommendation = recommendation.text,
                                onOpenAiPlan = { onActiveSheetChanged(SheetTarget.AiPlan) },
                                onApplied = showMessage
                            )
                        },
                        onOpenFullReview = onOpenReview,
                        onCreatePlan = onOpenPlanTab,
                        contentTopPadding = scaffoldPadding.calculateTopPadding(),
                        contentBottomPadding = contentBottomPadding + scaffoldPadding.calculateBottomPadding(),
                        period = insightsTabState.period,
                        periodSummary = insightsTabState.periodSummary,
                        onPeriodSelected = viewModel::setInsightsPeriod,
                        onStartFocus = onOpenFocusScreen
                    )
            }
        }

        val visibleSidebarPages = remember(
            activeSidebarPage,
            sidebarPageTransition.currentState,
            sidebarPageTransition.targetState
        ) {
            sidebarPagesForAnimatedLayer(
                activeSidebarPage = activeSidebarPage,
                currentSidebarPage = sidebarPageTransition.currentState,
                targetSidebarPage = sidebarPageTransition.targetState
            )
        }

        visibleSidebarPages.forEach { page ->
            DayDialSidebarPageLayer(
                page = page,
                activeSidebarPage = activeSidebarPage,
                transition = sidebarPageTransition,
                reduceMotionEnabled = reduceMotionEnabled,
                sidebarBackProgress = sidebarBackProgress
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundBrush)
                ) {
                    if (
                        shouldShowDayDialBackdrop(
                            glassSurfacesEnabled = glassSurfacesEnabled,
                            highContrastEnabled = highContrastEnabled,
                            activeSidebarPage = page
                        )
                    ) {
                        ChronosBackdrop(
                            theme = backdropTheme,
                            darkTheme = darkTheme,
                            reduceMotionEnabled = reduceMotionEnabled
                        )
                    }
                    SidebarPageContent(
                        page = page,
                        selectedDate = selectedDate,
                        timeBlocks = sortedBlocks,
                        templates = templateState.allTemplates,
                        missedBlocks = missedBlocks,
                        review = review,
                        privacyMode = privacyMode,
                        previewOnDeviceModel = previewOnDeviceModel,
                        onSelectDate = viewModel::selectDate,
                        onPrivacyModeSelected = viewModel::setPrivacyMode,
                        onPreviewOnDeviceModelChanged = onPreviewOnDeviceModelChanged,
                        onGeneratePlan = {
                            viewModel.onAiPlanRequested(listOf("Generate plan for $selectedDate"))
                            onActiveSheetChanged(SheetTarget.AiPlan)
                        },
                        planningStyle = planningStyle,
                        onPlanningStyleSelected = onPlanningStyleSelected,
                        protectFocusBlocks = protectFocusBlocks,
                        onProtectFocusChanged = onProtectFocusChanged,
                        addBreaksAutomatically = addBreaksAutomatically,
                        onAddBreaksAutomaticallyChanged = onAddBreaksAutomaticallyChanged,
                        preserveManualBlocks = preserveManualBlocks,
                        onPreserveManualBlocksChanged = onPreserveManualBlocksChanged,
                        onRebalance = viewModel::rebalanceDay,
                        onFillGaps = {
                            viewModel.fillEmptyTime(addBreaksAutomatically)
                            onActiveSheetChanged(SheetTarget.AiPlan)
                        },
                        onClearDay = viewModel::clearCurrentDay,
                        onSaveTemplate = templateState.saveCurrentAsTemplate,
                        onOpenMissed = { onActiveSheetChanged(SheetTarget.MissedBlocks) },
                        onOpenAiPlan = { onActiveSheetChanged(SheetTarget.AiPlan) },
                        onRestorePrevious = onRestorePrevious,
                        onCopyPlan = { viewModel.copyPlanFromPreviousDay() },
                        onApplyTemplate = templateState.applyTemplate,
                        onApplyTemplateToday = templateState.applyTemplateToday,
                        onApplyTemplateTomorrow = templateState.applyTemplateTomorrow,
                        routineCompletionFor = templateState.routineCompletionFor,
                        onEditTemplate = templateState.editTemplate,
                        onDuplicateTemplate = templateState.duplicateTemplate,
                        onSaveCurrentAsTemplate = templateState.saveCurrentAsTemplate,
                        onOpenPlannedBreakdown = { onActiveSheetChanged(SheetTarget.ReviewDetails(ReviewDetailSection.PLANNED)) },
                        onOpenActualLog = { onActiveSheetChanged(SheetTarget.ReviewDetails(ReviewDetailSection.ACTUAL)) },
                        onOpenMissedRecovery = { onActiveSheetChanged(SheetTarget.ReviewDetails(ReviewDetailSection.MISSED)) },
                        syncCloud = syncCloud,
                        syncStatus = syncStatus,
                        onSyncCloudChanged = onSyncCloudChanged,
                        onSyncNow = {
                            onSyncStatusChanged("Synced locally at ${formatDayDialMinute(currentMinute)}")
                            showMessage("Local sync checkpoint created")
                        },
                        blockStartReminders = blockStartReminders,
                        onBlockStartRemindersChanged = onBlockStartRemindersChanged,
                        breakReminders = breakReminders,
                        onBreakRemindersChanged = onBreakRemindersChanged,
                        missedAlerts = missedAlerts,
                        onMissedAlertsChanged = onMissedAlertsChanged,
                        endDayReviewReminder = endDayReviewReminder,
                        onEndDayReviewReminderChanged = onEndDayReviewReminderChanged,
                        sleepJournalLogReminder = sleepJournalLogReminder,
                        onSleepJournalLogReminderChanged = onSleepJournalLogReminderChanged,
                        sleepScheduleEnabled = sleepScheduleEnabled,
                        onSleepScheduleEnabledChanged = onSleepScheduleEnabledChanged,
                        sleepScheduleStartMinute = sleepScheduleStartMinute,
                        onSleepScheduleStartMinuteChanged = onSleepScheduleStartMinuteChanged,
                        sleepScheduleEndMinute = sleepScheduleEndMinute,
                        onSleepScheduleEndMinuteChanged = onSleepScheduleEndMinuteChanged,
                        reminderScheduleStatus = reminderScheduleStatus,
                        medicationReliabilityStatus = medicationReliabilityStatus,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        calendarPermissionStatus = calendarPermissionStatus,
                        showCalendarPermissionRationale = showCalendarPermissionRationale,
                        onDismissCalendarPermissionRationale = onDismissCalendarPermissionRationale,
                        onRequestCalendarSync = onRequestCalendarSync,
                        onRequestCalendarExportAccess = onRequestCalendarExportAccess,
                        onOpenCalendarSettings = onOpenCalendarSettings,
                        onOpenExactAlarmSettings = viewModel::openExactAlarmSettings,
                        calendarConnectionState = calendarConnectionState,
                        dynamicColorEnabled = dynamicColorEnabled,
                        onDynamicColorChanged = onDynamicColorChanged,
                        glassSurfacesEnabled = glassSurfacesEnabled,
                        onGlassSurfacesChanged = onGlassSurfacesChanged,
                        appearanceMode = appearanceMode,
                        onAppearanceModeSelected = onAppearanceModeSelected,
                        backdropTheme = backdropTheme,
                        onBackdropThemeSelected = onBackdropThemeSelected,
                        reduceMotionEnabled = reduceMotionEnabled,
                        onReduceMotionChanged = onReduceMotionChanged,
                        highContrastEnabled = highContrastEnabled,
                        onHighContrastChanged = onHighContrastChanged,
                        featureFlags = featureFlags,
                        onHabitsFeatureEnabledChanged = onHabitsFeatureEnabledChanged,
                        onGoalsFeatureEnabledChanged = onGoalsFeatureEnabledChanged,
                        onMedicationFeatureEnabledChanged = onMedicationFeatureEnabledChanged,
                        onReviewFeatureEnabledChanged = onReviewFeatureEnabledChanged,
                        onJournalFeatureEnabledChanged = onJournalFeatureEnabledChanged,
                        onSleepFeatureEnabledChanged = onSleepFeatureEnabledChanged,
                        onAiAdvisorFeatureEnabledChanged = onAiAdvisorFeatureEnabledChanged,
                        onOpenImport = { onActiveSheetChanged(SheetTarget.ImportBackup) },
                        onOpenWeeklySummary = { onActiveSheetChanged(SheetTarget.WeeklySummary) },
                        onOpenDiagnostics = { onActiveSheetChanged(SheetTarget.Diagnostics) },
                        onOpenLogs = { onActiveSheetChanged(SheetTarget.Logs) },
                        selectedBlockId = selectedBlockId,
                        onOpenFocusScreen = onOpenFocusScreen,
                        onOpenTasks = onOpenTasks,
                        onOpenHabits = onOpenHabits,
                        onOpenGoals = onOpenGoals,
                        onOpenMedication = onOpenMedication,
                        onOpenReview = onOpenReview,
                        appLockSettings = appLockSettings,
                        appLockCanAuthenticate = appLockCanAuthenticate,
                        onAppLockEnabledChanged = onAppLockEnabledChanged,
                        onLockOnResumeChanged = onLockOnResumeChanged,
                        onRequireAuthMedicationChanged = onRequireAuthMedicationChanged,
                        onRequireAuthReviewChanged = onRequireAuthReviewChanged,
                        onRequireAuthDataExportChanged = onRequireAuthDataExportChanged,
                        dataExportRequiresAuth = dataExportRequiresAuth,
                        dataExportCanAuthenticate = dataExportCanAuthenticate,
                        dataExportAuthError = dataExportAuthError,
                        onUnlockDataExport = onUnlockDataExport,
                        dataExportState = dataExportState,
                        onCreateDataExport = onCreateDataExport,
                        onExportBlockToCalendar = onExportBlockToCalendar,
                        onRefreshCalendarExport = onRefreshCalendarExport,
                        onRemoveCalendarExport = onRemoveCalendarExport,
                        quickItems = quickItems,
                        onQuickTaskDone = viewModel::completeDayQuickTask,
                        onQuickHabitDone = viewModel::completeDayQuickHabit,
                        onQuickMedicationTaken = viewModel::markDayQuickMedicationTaken,
                        onQuickMedicationMissed = viewModel::markDayQuickMedicationMissed,
                        onOpenBlock = viewModel::onBlockSelected,
                        contentTopPadding = scaffoldPadding.calculateTopPadding(),
                        contentBottomPadding = contentBottomPadding + scaffoldPadding.calculateBottomPadding(),
                        showMessage = showMessage
                    )
                }
            }
        }
    }
}

internal fun openNewBlockSheet(
    viewModel: DayDialViewModel,
    onActiveSheetChanged: (SheetTarget?) -> Unit,
    startMinute: Int,
    title: String = "",
    category: String = "WORK",
    durationMinutes: Int? = null
) {
    viewModel.onBlockSelected(null)
    onActiveSheetChanged(
        SheetTarget.NewBlock(
            startMinute = DialUtils.snapToIncrement(startMinute),
            title = title,
            category = category,
            durationMinutes = durationMinutes
        )
    )
}

internal fun quickCreateDefaultsForRing(ring: DialRing): Pair<String, String> = when (ring) {
    DialRing.OUTER -> "Calendar hold" to "CALENDAR"
    DialRing.MIDDLE -> "Focus Block" to "WORK"
    DialRing.INNER -> "Routine checkpoint" to "ROUTINE"
    DialRing.CENTER,
    DialRing.OUTSIDE -> "Open Time" to "WORK"
}

internal fun getDayDialTargetIndex(target: Any): Int {
    return when (target) {
        DayDialTab.PLAN -> 0
        DayDialTab.TODAY -> 1
        DayDialTab.FOCUS -> 2
        DayDialTab.INSIGHTS -> 3
        is SidebarPage -> 4 + target.ordinal
        else -> 0
    }
}

internal fun dayDialTransitionDirection(initialState: Any, targetState: Any): Int {
    return getDayDialTargetIndex(targetState).compareTo(getDayDialTargetIndex(initialState))
}

internal fun dayDialPrimaryTabTransitionDirection(
    initialState: DayDialTab,
    targetState: DayDialTab
): ChronosTransitionDirection {
    val delta = dayDialTransitionDirection(initialState, targetState)
    return when {
        delta > 0 -> ChronosTransitionDirection.Forward
        delta < 0 -> ChronosTransitionDirection.Backward
        else -> ChronosTransitionDirection.Neutral
    }
}

internal fun dayDialSidebarPageTransitionDirection(
    initialState: SidebarPage?,
    targetState: SidebarPage?
): ChronosTransitionDirection {
    val initialTarget = initialState ?: DayDialTab.TODAY
    val nextTarget = targetState ?: DayDialTab.TODAY
    val delta = dayDialTransitionDirection(initialTarget, nextTarget)
    return when {
        delta > 0 -> ChronosTransitionDirection.Forward
        delta < 0 -> ChronosTransitionDirection.Backward
        else -> ChronosTransitionDirection.Neutral
    }
}

// iOS-style spring for the primary-tab directional slide; the slide overshoot is what
// gives the switch its gentle bounce (tab bodies deliberately do not scale — see
// ChronosTransitionMotionTest "avoid scaling heavy tab bodies").
private fun dayDialPrimaryTabSlideSpring(): FiniteAnimationSpec<IntOffset> =
    spring(
        dampingRatio = ChronosMotionDefaults.PrimaryTabSpringDampingRatio,
        stiffness = ChronosMotionDefaults.PrimaryTabSpringStiffness,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )

private fun dayDialRouteTransition(
    direction: ChronosTransitionDirection,
    reducedMotion: Boolean,
    slideFraction: Float = ChronosMotionDefaults.PrimaryTabSlideFraction
): ChronosTransitionSet {
    val routeDirection = if (reducedMotion) ChronosTransitionDirection.Neutral else direction
    val routeSlideFraction = if (reducedMotion) 0f else slideFraction
    val fadeDurationMillis = if (reducedMotion) {
        ChronosMotionDefaults.ReducedDurationMillis
    } else {
        ChronosMotionDefaults.PrimaryTabDurationMillis
    }
    val enterDelayMillis = dayDialRouteEnterFadeDelayMillis()
    val enterDurationMillis = (fadeDurationMillis - enterDelayMillis).coerceAtLeast(1)
    val slideEnter = dayDialRouteSlideEnterTransition(
        direction = routeDirection,
        slideFraction = routeSlideFraction
    )
    val slideExit = dayDialRouteSlideExitTransition(
        direction = routeDirection,
        slideFraction = routeSlideFraction
    )
    val enter = slideEnter + fadeIn(
        initialAlpha = dayDialRouteEnterInitialAlpha(reducedMotion),
        animationSpec = tween(
            durationMillis = dayDialRouteEnterFadeDurationMillis(enterDurationMillis),
            delayMillis = enterDelayMillis,
            easing = ChronosMotionDefaults.MaterialStandardEasing
        )
    )
    val exit = slideExit + fadeOut(
        animationSpec = tween(
            durationMillis = dayDialRouteExitFadeDurationMillis(
                (fadeDurationMillis * ChronosMotionDefaults.ExitFadeDurationFraction).roundToInt()
            ),
            easing = ChronosMotionDefaults.ExitEasing
        )
    )
    return ChronosTransitionSet(enter = enter, exit = exit)
}

private fun dayDialRouteSlideEnterTransition(
    direction: ChronosTransitionDirection,
    slideFraction: Float
): EnterTransition {
    if (direction == ChronosTransitionDirection.Neutral || slideFraction == 0f) {
        return EnterTransition.None
    }
    val multiplier = dayDialMoveInTravelMultiplier(direction)
    return slideInHorizontally(
        animationSpec = dayDialPrimaryTabSlideSpring(),
        initialOffsetX = { width -> (width * slideFraction * multiplier).roundToInt() }
    )
}

private fun dayDialRouteSlideExitTransition(
    direction: ChronosTransitionDirection,
    slideFraction: Float
): ExitTransition {
    if (direction == ChronosTransitionDirection.Neutral || slideFraction == 0f) {
        return ExitTransition.None
    }
    val multiplier = dayDialMoveInTravelMultiplier(direction)
    return slideOutHorizontally(
        animationSpec = dayDialPrimaryTabSlideSpring(),
        targetOffsetX = { width -> (-width * slideFraction * multiplier).roundToInt() }
    )
}
