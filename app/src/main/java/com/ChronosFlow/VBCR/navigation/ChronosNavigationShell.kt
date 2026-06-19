package com.ChronosFlow.VBCR.navigation

import com.ChronosFlow.VBCR.core.ui.components.ChronosIconButton

import android.os.SystemClock
import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ChronosFlow.VBCR.core.ui.shell.ChronosCompactShellBottomClearance
import com.ChronosFlow.VBCR.core.ui.shell.LocalChronosShellBottomInset
import com.ChronosFlow.VBCR.core.ui.shell.LocalChronosShellOverlayController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentActivity
import com.ChronosFlow.VBCR.AppLockViewModel
import com.ChronosFlow.VBCR.ChronosShellState
import com.ChronosFlow.VBCR.core.ui.components.ChronosBackground
import com.ChronosFlow.VBCR.core.ui.components.ChronosPredictiveBackHandlerWithProgress
import com.ChronosFlow.VBCR.core.ui.motion.ChronosMotionDefaults
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionDirection
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionFactory
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionSet
import com.ChronosFlow.VBCR.core.ui.motion.ChronosValueAnimationFactory
import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import com.ChronosFlow.VBCR.core.ui.settings.resolveChronosDarkTheme
import com.ChronosFlow.VBCR.core.ui.theme.ChronosGlassTokens
import com.ChronosFlow.VBCR.core.ui.theme.GlassElevation
import com.ChronosFlow.VBCR.core.ui.theme.GlassTone
import com.ChronosFlow.VBCR.core.ui.theme.LocalChronosHazeState
import com.ChronosFlow.VBCR.core.ui.theme.chronosFrostedGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.ChronosFlow.VBCR.feature.daydial.model.DayDialTab
import androidx.window.core.layout.WindowSizeClass
import kotlin.math.min

private object ChronosShellDefaults {
    val CompactPillHorizontalPadding = 16.dp
    val CompactPillBottomMargin = 16.dp
    val CompactFabSize = 56.dp
    val CompactFabRadius = 18.dp
    val CompactFloatingBarHeight = 72.dp
    val CompactQuickAddMenuGap = 8.dp
    val CompactPillRadius = 32.dp
    const val CompactSelectedScale = 1.02f
    const val QuickAddExpandedRotationDegrees = 45f
    const val CompactNavigationDoubleClickMillis = 300L
    const val CompactNavigationDeferredClickIgnoreMillis = 420L
    val RailPanelWidth = 116.dp
    val RailPanelPadding = 16.dp
}

private object ChronosShellMotion {
    const val NavShellOffsetFraction = 0.5f
}

/** Bounds of a bottom-bar navigation item relative to the bar, used to position the sliding selection pill. */
private data class ChronosNavItemBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

private fun chronosShellSurfaceTransition(
    reducedMotion: Boolean,
    includeScale: Boolean
): ChronosTransitionSet {
    if (reducedMotion) {
        return ChronosTransitionFactory.fadeScale(
            durationMillis = ChronosMotionDefaults.ReducedDurationMillis,
            easing = ChronosMotionDefaults.MaterialStandardEasing,
            direction = ChronosTransitionDirection.Neutral,
            enterScale = 1f,
            exitScale = 1f
        )
    }

    return ChronosTransitionFactory.materialSharedAxisY(
        durationMillis = ChronosMotionDefaults.DefaultDurationMillis,
        easing = ChronosMotionDefaults.MaterialStandardEasing,
        direction = ChronosTransitionDirection.Forward,
        slideFraction = ChronosShellMotion.NavShellOffsetFraction,
        enterScale = if (includeScale) ChronosMotionDefaults.SharedAxisEnterScale else 1f,
        exitScale = if (includeScale) ChronosMotionDefaults.SharedAxisExitScale else 1f
    )
}


private enum class ChronosShellLayout {
    COMPACT,
    ADAPTIVE
}

internal data class ChronosQuickAddAction(
    val label: String,
    val icon: ImageVector,
    // null == the "type a command" quick-capture input rather than a navigation target.
    val route: ChronosRoute?
)

internal fun quickAddActionsFor(featureFlags: ChronosFeatureFlags): List<ChronosQuickAddAction> = buildList {
    add(
        ChronosQuickAddAction(
            label = "New block",
            icon = Icons.Default.Add,
            route = ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_ADD_BLOCK)
        )
    )
    add(
        ChronosQuickAddAction(
            label = "New task",
            icon = Icons.Default.Checklist,
            route = ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD)
        )
    )
    if (featureFlags.medicationEnabled) {
        add(
            ChronosQuickAddAction(
                label = "New meds",
                icon = Icons.Default.Medication,
                route = ChronosRoute.Medication.createRoute(ChronosRoute.TARGET_ADD)
            )
        )
    }
    if (featureFlags.habitsEnabled) {
        add(
            ChronosQuickAddAction(
                label = "New habit",
                icon = Icons.Default.Favorite,
                route = ChronosRoute.Habits.createRoute(ChronosRoute.TARGET_ADD)
            )
        )
    }
    if (featureFlags.goalsEnabled) {
        add(
            ChronosQuickAddAction(
                label = "New goal",
                icon = Icons.Default.Flag,
                route = ChronosRoute.Goals.createRoute(ChronosRoute.TARGET_ADD)
            )
        )
    }
    if (featureFlags.journalEnabled) {
        add(
            ChronosQuickAddAction(
                label = "Journal entry",
                icon = Icons.Default.EditNote,
                route = ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_JOURNAL)
            )
        )
    }
    if (featureFlags.sleepEnabled) {
        add(
            ChronosQuickAddAction(
                label = "Log sleep",
                icon = Icons.Default.Bedtime,
                route = ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_SLEEP)
            )
        )
    }
    add(
        ChronosQuickAddAction(
            label = "Type a task, med, habit, or goal",
            icon = Icons.Default.Search,
            route = null
        )
    )
}

private fun ChronosQuickAddAction.isCommandInput(): Boolean = route == null

internal fun compactShellOverlayBottomInset(
    navigationBarInset: Dp,
    showNavigationChrome: Boolean
): Dp = if (showNavigationChrome) {
    navigationBarInset + ChronosCompactShellBottomClearance
} else {
    0.dp
}

internal fun compactShellFloatingBarBottomOffset(
    navigationBarInset: Dp,
    showNavigationChrome: Boolean
): Dp = if (showNavigationChrome) {
    navigationBarInset + ChronosShellDefaults.CompactPillBottomMargin
} else {
    0.dp
}

internal fun compactQuickAddMenuBottomOffset(
    navigationBarInset: Dp,
    showNavigationChrome: Boolean
): Dp = if (showNavigationChrome) {
    compactShellFloatingBarBottomOffset(navigationBarInset, showNavigationChrome) +
        ChronosShellDefaults.CompactFloatingBarHeight +
        ChronosShellDefaults.CompactQuickAddMenuGap
} else {
    0.dp
}

@Suppress("UNUSED_PARAMETER")
internal fun compactShellInteractiveBottomInset(
    navigationBarInset: Dp,
    imeInset: Dp,
    quickAddExpanded: Boolean
): Dp {
    // The compact shell is aligned in the resized window when the IME is visible;
    // applying the IME inset again lifts quick-create away from the keyboard.
    return navigationBarInset
}

internal fun shouldNavigateShellDestination(selectedId: String, destinationId: String): Boolean =
    selectedId != destinationId

internal fun dayDialTabForShellTarget(dayTarget: String?): DayDialTab? =
    when (dayTarget) {
        ChronosRoute.Day.TARGET_TODAY, ChronosRoute.Day.TARGET_TODAY_RESET -> DayDialTab.TODAY
        ChronosRoute.Day.TARGET_PLAN -> DayDialTab.PLAN
        ChronosRoute.Day.TARGET_FOCUS_PLANNER -> DayDialTab.FOCUS
        else -> null
    }

internal fun dayTargetForDayDialTab(tab: DayDialTab): String? =
    when (tab) {
        DayDialTab.TODAY -> ChronosRoute.Day.TARGET_TODAY
        DayDialTab.PLAN -> ChronosRoute.Day.TARGET_PLAN
        DayDialTab.FOCUS -> ChronosRoute.Day.TARGET_FOCUS_PLANNER
        DayDialTab.INSIGHTS -> ChronosRoute.Day.TARGET_INSIGHTS
    }


internal fun quickAddDestinationChangeKey(
    destinationId: String,
    dayTarget: String?
): String = "$destinationId:${dayTarget.orEmpty()}"

internal fun shouldCloseQuickAddForDestinationChange(
    previousDestinationKey: String?,
    destinationKey: String,
    quickAddExpanded: Boolean
): Boolean =
    quickAddExpanded && previousDestinationKey != null && previousDestinationKey != destinationKey

internal fun compactNavigationItemTargetScale(selected: Boolean, reducedMotion: Boolean): Float =
    if (selected && !reducedMotion) ChronosShellDefaults.CompactSelectedScale else 1f

internal fun shouldTreatCompactNavigationClickAsDouble(
    previousClickUptimeMillis: Long,
    clickUptimeMillis: Long
): Boolean {
    val elapsedMillis = clickUptimeMillis - previousClickUptimeMillis
    return previousClickUptimeMillis > 0L &&
        elapsedMillis in 0L..ChronosShellDefaults.CompactNavigationDoubleClickMillis
}

internal fun shouldIgnoreCompactNavigationDeferredClick(
    lastImmediateClickUptimeMillis: Long,
    clickUptimeMillis: Long
): Boolean {
    val elapsedMillis = clickUptimeMillis - lastImmediateClickUptimeMillis
    return lastImmediateClickUptimeMillis > 0L &&
        elapsedMillis in 0L..ChronosShellDefaults.CompactNavigationDeferredClickIgnoreMillis
}

internal fun quickAddIconRotationDegrees(expanded: Boolean, reducedMotion: Boolean): Float =
    if (expanded && !reducedMotion) ChronosShellDefaults.QuickAddExpandedRotationDegrees else 0f

internal enum class ChronosShellHapticCue {
    NavigationTick,
    QuickAddToggle,
    QuickAddAction
}

internal fun shouldPerformShellHaptic(cue: ChronosShellHapticCue, reducedMotion: Boolean): Boolean =
    when (cue) {
        ChronosShellHapticCue.NavigationTick -> !reducedMotion
        ChronosShellHapticCue.QuickAddToggle,
        ChronosShellHapticCue.QuickAddAction -> true
    }

internal fun signedQuickAddBackProgress(progress: Float, swipeEdge: Int): Float {
    val clampedProgress = progress.coerceIn(0f, 1f)
    return if (swipeEdge == BackEventCompat.EDGE_RIGHT) {
        -clampedProgress
    } else {
        clampedProgress
    }
}

private fun signedQuickAddBackProgress(backEvent: BackEventCompat): Float =
    signedQuickAddBackProgress(
        progress = backEvent.progress,
        swipeEdge = backEvent.swipeEdge
    )

internal data class QuickCaptureInputState(
    val canSubmit: Boolean,
    val isError: Boolean,
    val helperText: String?,
    val submitContentDescription: String
)

internal fun quickCaptureInputState(
    capture: String,
    previewLabel: String?,
    rejectedCapture: String?
): QuickCaptureInputState {
    val normalizedCapture = capture.trim()
    val hasRejectedCapture = rejectedCapture != null
    return QuickCaptureInputState(
        canSubmit = previewLabel != null,
        isError = hasRejectedCapture,
        helperText = when {
            hasRejectedCapture -> "Add more detail for a task, med, or habit."
            previewLabel != null -> "Ready to add $previewLabel."
            normalizedCapture.isNotBlank() -> "Keep typing for a task, med, or habit."
            else -> null
        },
        submitContentDescription = previewLabel?.let { "Quick add $it" } ?: "Quick add typed command"
    )
}

@Composable
internal fun ChronosNavigationShell(
    navState: ChronosNavigationState,
    currentSection: String?,
    currentDayTarget: String?,
    shellState: ChronosShellState,
    onOpenCommandPalette: () -> Unit,
    onQuickCapturePreview: (String) -> String?,
    onQuickCaptureCommand: (String) -> Boolean,
    onOpenMedication: () -> Unit,
    activity: FragmentActivity,
    appLockViewModel: AppLockViewModel,
    modifier: Modifier = Modifier
) {
    val layout = rememberChronosShellLayout()
    val uiSettings = rememberChronosUiSettings()
    val darkTheme = resolveChronosDarkTheme(uiSettings.appearanceMode)
    val highContrastEnabled = uiSettings.highContrastEnabled
    val featureFlags = uiSettings.featureFlags
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val statusBarInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val showNavigationChrome = currentSection != ChronosRoute.Focus.section
    // Single source of truth for the Day tab: navState.requestedDayTarget, passed in as
    // currentDayTarget. Nav3's selectDayTarget is synchronous, so the previous optimistic
    // localDayTarget state machine (and its companion SideEffect/LaunchedEffect) is no longer
    // needed — selecting a tab updates navState immediately and recomposes with the new value.
    // navState.dayTargetGeneration is bumped on every tab selection (including a re-tap of the
    // current tab), which drives DayDialScreen to re-apply the target for the Today double-tap reset.
    val renderedDayTarget = currentDayTarget
    val requestedPrimaryTab = dayDialTabForShellTarget(renderedDayTarget)
    val currentDestination = ChronosRoute.shellDestinationFor(currentSection, renderedDayTarget, featureFlags)
    val quickAddActions = quickAddActionsFor(featureFlags)
    var quickAddExpanded by rememberSaveable { mutableStateOf(false) }
    var quickAddBackProgress by remember { mutableFloatStateOf(0f) }
    val setQuickAddExpanded: (Boolean) -> Unit = { expanded ->
        if (!expanded) {
            quickAddBackProgress = 0f
        }
        quickAddExpanded = expanded
    }
    val closeQuickAdd = { setQuickAddExpanded(false) }
    val quickAddDestinationKey = quickAddDestinationChangeKey(currentDestination.id, renderedDayTarget)
    var lastQuickAddDestinationKey by rememberSaveable { mutableStateOf(quickAddDestinationKey) }
    LaunchedEffect(quickAddDestinationKey) {
        if (
            shouldCloseQuickAddForDestinationChange(
                previousDestinationKey = lastQuickAddDestinationKey,
                destinationKey = quickAddDestinationKey,
                quickAddExpanded = quickAddExpanded
            )
        ) {
            closeQuickAdd()
        }
        lastQuickAddDestinationKey = quickAddDestinationKey
    }
    val shellOverlayController = LocalChronosShellOverlayController.current
    val suppressBottomChrome = shellOverlayController?.suppressBottomChrome == true
    val isQuickAddChromeVisible = showNavigationChrome && !suppressBottomChrome
    val bottomChromeTransition = chronosShellSurfaceTransition(
        reducedMotion = uiSettings.reduceMotionEnabled,
        includeScale = false
    )
    val quickAddInteractiveBottomInset = compactShellInteractiveBottomInset(
        navigationBarInset = navigationBarInset,
        imeInset = imeInset,
        quickAddExpanded = quickAddExpanded
    )
    val quickAddBarBottomOffset = compactShellFloatingBarBottomOffset(
        navigationBarInset = quickAddInteractiveBottomInset,
        showNavigationChrome = isQuickAddChromeVisible
    )
    val quickAddMenuBottomOffset = compactQuickAddMenuBottomOffset(
        navigationBarInset = quickAddInteractiveBottomInset,
        showNavigationChrome = isQuickAddChromeVisible
    )
    val contentBottomPadding = if (layout == ChronosShellLayout.COMPACT) {
        compactShellOverlayBottomInset(
            navigationBarInset = navigationBarInset,
            showNavigationChrome = isQuickAddChromeVisible
        )
    } else {
        0.dp
    }
    val haptic = LocalHapticFeedback.current
    val onDayPrimaryTabSelected: (DayDialTab) -> Unit = { tab ->
        closeQuickAdd()
        navState.selectDayTarget(dayTargetForDayDialTab(tab))
    }
    val onShellDestinationSelected: (ChronosRoute.ShellDestination) -> Unit = shellNavigation@ { destination ->
        closeQuickAdd()
        if (!shouldNavigateShellDestination(currentDestination.id, destination.id)) {
            return@shellNavigation
        }
        if (shouldPerformShellHaptic(
                ChronosShellHapticCue.NavigationTick,
                uiSettings.reduceMotionEnabled
            )
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        // navigate() routes Day targets (Plan/Today/Focus/Review) in-place via selectDayTarget
        // and switches sections for everything else.
        navState.navigate(destination.route)
    }
    val onShellDestinationDoubleClick: (ChronosRoute.ShellDestination) -> Unit = { destination ->
        if (destination.id == ChronosRoute.SHELL_TODAY) {
            // Double-tapping Today snaps the dial back to the current date
            // (TARGET_TODAY_RESET), even when arriving from another section or
            // after browsing to another day.
            navState.selectDayTarget(ChronosRoute.Day.TARGET_TODAY_RESET)
        }
    }
    val onQuickAddActionSelected: (ChronosQuickAddAction) -> Unit = { action ->
        closeQuickAdd()
        action.route?.let(navState::navigate)
    }
    val onQuickCaptureSubmitted: (String) -> Boolean = { capture ->
        val handled = onQuickCaptureCommand(capture)
        if (handled) {
            closeQuickAdd()
        }
        handled
    }

    LaunchedEffect(isQuickAddChromeVisible) {
        if (!isQuickAddChromeVisible) {
            closeQuickAdd()
        }
    }

    ChronosPredictiveBackHandlerWithProgress(
        enabled = quickAddExpanded && isQuickAddChromeVisible,
        onBackStarted = { backEvent -> quickAddBackProgress = signedQuickAddBackProgress(backEvent) },
        onBackProgressed = { backEvent -> quickAddBackProgress = signedQuickAddBackProgress(backEvent) },
        onBackCancelled = { quickAddBackProgress = 0f },
        onBackInvoked = closeQuickAdd
    )

    val shellHazeState = rememberHazeState()

    CompositionLocalProvider(
        LocalChronosShellBottomInset provides contentBottomPadding,
        LocalChronosHazeState provides shellHazeState
    ) {
        ChronosBackground(
            modifier = modifier.fillMaxSize(),
            darkTheme = darkTheme,
            highContrast = highContrastEnabled
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (layout) {
                    ChronosShellLayout.COMPACT -> {
                        ChronosNavDisplay(
                            navState = navState,
                            contentPadding = PaddingValues(),
                            onOpenCommandPalette = onOpenCommandPalette,
                            onOpenMedication = onOpenMedication,
                            featureFlags = featureFlags,
                            requestedPrimaryTab = requestedPrimaryTab,
                            shellDayTarget = renderedDayTarget,
                            dayLaunchTargetGeneration = navState.dayTargetGeneration,
                            reducedMotion = uiSettings.reduceMotionEnabled,
                            onDayPrimaryTabSelected = onDayPrimaryTabSelected,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 0.dp)
                                .hazeSource(shellHazeState)
                        )
                        AnimatedVisibility(
                            visible = isQuickAddChromeVisible,
                            enter = bottomChromeTransition.enter,
                            exit = bottomChromeTransition.exit,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                ChronosQuickAddMenu(
                                    actions = quickAddActions,
                                    expanded = quickAddExpanded,
                                    reducedMotion = uiSettings.reduceMotionEnabled,
                                    backProgress = quickAddBackProgress,
                                    onActionSelected = onQuickAddActionSelected,
                                    onQuickCapturePreview = onQuickCapturePreview,
                                    onQuickCapture = onQuickCaptureSubmitted,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .zIndex(2f)
                                        .padding(
                                            end = ChronosShellDefaults.CompactPillHorizontalPadding,
                                            bottom = quickAddMenuBottomOffset
                                        )
                                )
                                ChronosCompactFloatingBottomBar(
                                    destinations = ChronosRoute.compactShellDestinations(featureFlags),
                                    selectedId = currentDestination.id,
                                    shellState = shellState,
                                    highContrastEnabled = highContrastEnabled,
                                    reducedMotion = uiSettings.reduceMotionEnabled,
                                    quickAddExpanded = quickAddExpanded,
                                    onQuickAddClick = { setQuickAddExpanded(!quickAddExpanded) },
                                    onNavigate = onShellDestinationSelected,
                                    onDoubleClick = onShellDestinationDoubleClick,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(
                                            start = ChronosShellDefaults.CompactPillHorizontalPadding,
                                            end = ChronosShellDefaults.CompactPillHorizontalPadding,
                                            bottom = quickAddBarBottomOffset
                                        )
                                        .zIndex(1f)
                                )
                            }
                        }
                    }

                    ChronosShellLayout.ADAPTIVE -> {
                        if (showNavigationChrome) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    ChronosAdaptiveNavigationRail(
                                        destinations = ChronosRoute.expandedShellDestinations(featureFlags),
                                        selectedId = currentDestination.id,
                                        shellState = shellState,
                                        highContrastEnabled = highContrastEnabled,
                                        onNavigate = onShellDestinationSelected,
                                        onDoubleClick = onShellDestinationDoubleClick,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(
                                                start = ChronosShellDefaults.RailPanelPadding,
                                                top = statusBarInset + ChronosShellDefaults.RailPanelPadding,
                                                bottom = navigationBarInset + ChronosShellDefaults.RailPanelPadding
                                            )
                                    )
                                    ChronosNavDisplay(
                                        navState = navState,
                                        contentPadding = PaddingValues(),
                                        onOpenCommandPalette = onOpenCommandPalette,
                                        onOpenMedication = onOpenMedication,
                                        featureFlags = featureFlags,
                                        requestedPrimaryTab = requestedPrimaryTab,
                                        shellDayTarget = renderedDayTarget,
                                        dayLaunchTargetGeneration = navState.dayTargetGeneration,
                                        reducedMotion = uiSettings.reduceMotionEnabled,
                                        onDayPrimaryTabSelected = onDayPrimaryTabSelected,
                                        modifier = Modifier
                                            .weight(1f)
                                            .hazeSource(shellHazeState)
                                    )
                                }
                                if (isQuickAddChromeVisible) {
                                    ChronosQuickAddFab(
                                        actions = quickAddActions,
                                        expanded = quickAddExpanded,
                                        reducedMotion = uiSettings.reduceMotionEnabled,
                                        backProgress = quickAddBackProgress,
                                        onExpandedChange = setQuickAddExpanded,
                                        onActionSelected = onQuickAddActionSelected,
                                        onQuickCapturePreview = onQuickCapturePreview,
                                        onQuickCapture = onQuickCaptureSubmitted,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(
                                                end = ChronosShellDefaults.CompactPillHorizontalPadding,
                                                bottom = quickAddBarBottomOffset
                                            )
                                    )
                                }
                            }
                        } else {
                            ChronosNavDisplay(
                                navState = navState,
                                contentPadding = PaddingValues(),
                                onOpenCommandPalette = onOpenCommandPalette,
                                onOpenMedication = onOpenMedication,
                                featureFlags = featureFlags,
                                requestedPrimaryTab = requestedPrimaryTab,
                                shellDayTarget = renderedDayTarget,
                                dayLaunchTargetGeneration = navState.dayTargetGeneration,
                                reducedMotion = uiSettings.reduceMotionEnabled,
                                onDayPrimaryTabSelected = onDayPrimaryTabSelected,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronosCompactFloatingBottomBar(
    destinations: List<ChronosRoute.ShellDestination>,
    selectedId: String,
    shellState: ChronosShellState,
    highContrastEnabled: Boolean,
    reducedMotion: Boolean,
    quickAddExpanded: Boolean,
    onQuickAddClick: () -> Unit,
    onNavigate: (ChronosRoute.ShellDestination) -> Unit,
    modifier: Modifier = Modifier,
    onDoubleClick: (ChronosRoute.ShellDestination) -> Unit = {}
) {
    val glassBar = rememberChronosUiSettings().glassSurfacesEnabled && !highContrastEnabled
    val frosted = glassBar && LocalChronosHazeState.current != null
    val barShape = RoundedCornerShape(ChronosShellDefaults.CompactPillRadius)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ChronosShellDefaults.CompactFloatingBarHeight)
            .then(
                if (frosted) {
                    Modifier.chronosFrostedGlass(barShape, GlassTone.PROMINENT, GlassElevation.MEDIUM)
                } else {
                    Modifier
                }
            ),
        shape = barShape,
        color = when {
            frosted -> Color.Transparent
            highContrastEnabled -> MaterialTheme.colorScheme.surfaceContainerHigh
            glassBar -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = GlassTone.PROMINENT.alpha)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        },
        border = if (glassBar && !frosted) {
            BorderStroke(
                GlassElevation.MEDIUM.borderWidth,
                ChronosGlassTokens.borderBrush(MaterialTheme.colorScheme.primary)
            )
        } else {
            null
        },
        tonalElevation = if (highContrastEnabled || frosted) 0.dp else 8.dp,
        shadowElevation = if (highContrastEnabled || frosted) 0.dp else 10.dp
    ) {
        var barCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val itemBounds = remember { mutableStateMapOf<Int, ChronosNavItemBounds>() }
        val selectedIndex = destinations.indexOfFirst { it.id == selectedId }
        val selectedBounds = itemBounds[selectedIndex]
        // A single rounded pill that glides between tabs (iOS-style) instead of each tab
        // cross-fading its own background independently.
        val indicatorX by animateFloatAsState(
            targetValue = selectedBounds?.x ?: 0f,
            animationSpec = ChronosValueAnimationFactory.navIndicator(reducedMotion),
            label = "navIndicatorX"
        )
        val indicatorWidth by animateFloatAsState(
            targetValue = selectedBounds?.width ?: 0f,
            animationSpec = ChronosValueAnimationFactory.navIndicator(reducedMotion),
            label = "navIndicatorWidth"
        )
        val indicatorColor = MaterialTheme.colorScheme.primaryContainer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .onGloballyPositioned { barCoordinates = it }
                // The selection pill is drawn (not laid out) so both its glide and width
                // animate entirely in the draw phase — GPU-composited on the bar's render
                // node with no per-frame recomposition or relayout.
                .drawBehind {
                    val bounds = selectedBounds ?: return@drawBehind
                    val width = indicatorWidth
                    if (width <= 0f) return@drawBehind
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(indicatorX, bounds.y),
                        size = Size(width, bounds.height),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                }
        ) {
            val dayDialViewedDate = LocalChronosShellOverlayController.current?.dayDialViewedDate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, destination ->
                    ChronosCompactNavigationItem(
                        destination = destination,
                        selected = selectedId == destination.id,
                        badgeValue = badgeValueFor(destination.id, shellState, dayDialViewedDate),
                        reducedMotion = reducedMotion,
                        onClick = { onNavigate(destination) },
                        onDoubleClick = { onDoubleClick(destination) },
                        onBoundsChanged = { bounds -> itemBounds[index] = bounds },
                        barCoordinates = { barCoordinates },
                        modifier = Modifier.weight(1f)
                    )
                }
                ChronosQuickAddButton(
                    expanded = quickAddExpanded,
                    reducedMotion = reducedMotion,
                    onClick = onQuickAddClick
                )
            }
        }
    }
}

@Composable
private fun ChronosQuickAddFab(
    actions: List<ChronosQuickAddAction>,
    expanded: Boolean,
    reducedMotion: Boolean,
    backProgress: Float,
    onExpandedChange: (Boolean) -> Unit,
    onActionSelected: (ChronosQuickAddAction) -> Unit,
    onQuickCapturePreview: (String) -> String?,
    onQuickCapture: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ChronosQuickAddMenu(
            actions = actions,
            expanded = expanded,
            reducedMotion = reducedMotion,
            backProgress = backProgress,
            onActionSelected = onActionSelected,
            onQuickCapturePreview = onQuickCapturePreview,
            onQuickCapture = onQuickCapture
        )
        ChronosQuickAddButton(
            expanded = expanded,
            reducedMotion = reducedMotion,
            onClick = { onExpandedChange(!expanded) }
        )
    }
}

@Composable
private fun ChronosQuickAddMenu(
    actions: List<ChronosQuickAddAction>,
    expanded: Boolean,
    reducedMotion: Boolean,
    backProgress: Float = 0f,
    onActionSelected: (ChronosQuickAddAction) -> Unit,
    onQuickCapturePreview: (String) -> String?,
    onQuickCapture: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val quickAddMenuTransition = chronosShellSurfaceTransition(
        reducedMotion = reducedMotion,
        includeScale = true
    )
    AnimatedVisibility(
        visible = expanded,
        enter = quickAddMenuTransition.enter,
        exit = quickAddMenuTransition.exit,
        modifier = modifier
    ) {
        val menuShape = RoundedCornerShape(28.dp)
        val frostedMenu = rememberChronosUiSettings().glassSurfacesEnabled &&
            LocalChronosHazeState.current != null
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    translationX = size.width * backProgress
                }
                .then(
                    if (frostedMenu) {
                        Modifier.chronosFrostedGlass(menuShape, GlassTone.PROMINENT, GlassElevation.MEDIUM)
                    } else {
                        Modifier
                    }
                ),
            shape = menuShape,
            color = if (frostedMenu) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (frostedMenu) 0.dp else 6.dp,
            shadowElevation = if (frostedMenu) 0.dp else 8.dp
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 260.dp, max = 320.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                actions.forEach { action ->
                    if (action.isCommandInput()) {
                        ChronosQuickCaptureInput(
                            action = action,
                            reducedMotion = reducedMotion,
                            previewFor = onQuickCapturePreview,
                            onSubmit = onQuickCapture
                        )
                    } else {
                        ChronosQuickAddPill(
                            action = action,
                            reducedMotion = reducedMotion,
                            onClick = { onActionSelected(action) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronosQuickAddButton(
    expanded: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val iconRotation by animateFloatAsState(
        targetValue = quickAddIconRotationDegrees(expanded, reducedMotion),
        animationSpec = ChronosValueAnimationFactory.quickAddRotation(reducedMotion),
        label = "quickAddIconRotation"
    )
    FloatingActionButton(
        onClick = {
            if (shouldPerformShellHaptic(ChronosShellHapticCue.QuickAddToggle, reducedMotion)) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onClick()
        },
        modifier = modifier.size(ChronosShellDefaults.CompactFabSize),
        shape = RoundedCornerShape(ChronosShellDefaults.CompactFabRadius),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 10.dp,
            pressedElevation = 14.dp,
            focusedElevation = 12.dp,
            hoveredElevation = 12.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = if (expanded) "Close quick create" else "Open quick create",
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { rotationZ = iconRotation }
        )
    }
}

@Composable
private fun ChronosQuickAddPill(
    action: ChronosQuickAddAction,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            if (shouldPerformShellHaptic(ChronosShellHapticCue.QuickAddAction, reducedMotion)) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            }
            onClick()
        },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChronosQuickCaptureInput(
    action: ChronosQuickAddAction,
    reducedMotion: Boolean,
    previewFor: (String) -> String?,
    onSubmit: (String) -> Boolean
) {
    var text by rememberSaveable { mutableStateOf("") }
    var rejectedCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val capture = text.trim()
    val previewLabel = if (capture.isBlank()) null else previewFor(capture)
    val inputState = quickCaptureInputState(
        capture = capture,
        previewLabel = previewLabel,
        rejectedCapture = rejectedCapture
    )
    val submit = {
        if (capture.isNotEmpty()) {
            if (shouldPerformShellHaptic(ChronosShellHapticCue.QuickAddAction, reducedMotion)) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            }
            if (onSubmit(capture)) {
                text = ""
                rejectedCapture = null
            } else {
                rejectedCapture = capture
            }
        }
    }

    TextField(
        value = text,
        onValueChange = {
            text = it
            rejectedCapture = null
        },
        modifier = Modifier
            .fillMaxWidth(),
        singleLine = true,
        isError = inputState.isError,
        shape = RoundedCornerShape(26.dp),
        textStyle = MaterialTheme.typography.labelLarge.copy(
            color = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        leadingIcon = {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            ChronosIconButton(
                onClick = submit,
                enabled = inputState.canSubmit
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = inputState.submitContentDescription
                )
            }
        },
        placeholder = {
            Text(
                text = "Start typing your command",
                style = MaterialTheme.typography.labelLarge
            )
        },
        supportingText = {
            if (inputState.helperText != null) {
                Text(text = inputState.helperText, style = MaterialTheme.typography.labelSmall)
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun ChronosCompactNavigationItem(
    destination: ChronosRoute.ShellDestination,
    selected: Boolean,
    badgeValue: String?,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    onBoundsChanged: (ChronosNavItemBounds) -> Unit,
    barCoordinates: () -> LayoutCoordinates?,
    modifier: Modifier = Modifier,
    onDoubleClick: () -> Unit = {}
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ChronosValueAnimationFactory.selection(reducedMotion),
        label = "compactNavContent"
    )
    val itemScale by animateFloatAsState(
        targetValue = compactNavigationItemTargetScale(selected, reducedMotion),
        animationSpec = ChronosValueAnimationFactory.navigationChromeScale(reducedMotion),
        label = "compactNavScale"
    )

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var lastClickUptimeMillis by remember { mutableLongStateOf(0L) }
    var lastImmediateClickUptimeMillis by remember { mutableLongStateOf(0L) }
    val handleNavigationClick: (Long) -> Unit = { clickUptimeMillis ->
        val doubleClick = shouldTreatCompactNavigationClickAsDouble(
            previousClickUptimeMillis = lastClickUptimeMillis,
            clickUptimeMillis = clickUptimeMillis
        )
        lastClickUptimeMillis = clickUptimeMillis
        onClick()
        if (doubleClick) {
            onDoubleClick()
        }
    }

    Surface(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val barCoords = barCoordinates() ?: return@onGloballyPositioned
                val position = barCoords.localPositionOf(coordinates, Offset.Zero)
                onBoundsChanged(
                    ChronosNavItemBounds(
                        x = position.x,
                        y = position.y,
                        width = coordinates.size.width.toFloat(),
                        height = coordinates.size.height.toFloat()
                    )
                )
            }
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .pointerInput(onClick, onDoubleClick) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val clickUptimeMillis = SystemClock.uptimeMillis()
                    lastImmediateClickUptimeMillis = clickUptimeMillis
                    down.consume()
                    handleNavigationClick(clickUptimeMillis)
                    waitForUpOrCancellation()?.consume()
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    val clickUptimeMillis = SystemClock.uptimeMillis()
                    if (shouldIgnoreCompactNavigationDeferredClick(
                        lastImmediateClickUptimeMillis = lastImmediateClickUptimeMillis,
                        clickUptimeMillis = clickUptimeMillis
                    )) {
                        return@clickable
                    }
                    handleNavigationClick(clickUptimeMillis)
                }
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BadgedBox(
                badge = {
                    ChronosAnimatedBadge(badgeValue = badgeValue, reducedMotion = reducedMotion)
                }
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChronosAnimatedBadge(badgeValue: String?, reducedMotion: Boolean) {
    AnimatedVisibility(
        visible = badgeValue != null,
        enter = scaleIn(ChronosValueAnimationFactory.pressScale(reducedMotion)) +
            fadeIn(ChronosValueAnimationFactory.selection(reducedMotion)),
        exit = scaleOut(ChronosValueAnimationFactory.pressScale(reducedMotion)) +
            fadeOut(ChronosValueAnimationFactory.selection(reducedMotion))
    ) {
        Badge { Text(badgeValue.orEmpty()) }
    }
}

@Composable
private fun ChronosAdaptiveNavigationRail(
    destinations: List<ChronosRoute.ShellDestination>,
    selectedId: String,
    shellState: ChronosShellState,
    highContrastEnabled: Boolean,
    onNavigate: (ChronosRoute.ShellDestination) -> Unit,
    modifier: Modifier = Modifier,
    onDoubleClick: (ChronosRoute.ShellDestination) -> Unit = {}
) {
    Surface(
        modifier = modifier.width(ChronosShellDefaults.RailPanelWidth),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (highContrastEnabled) 0.dp else 4.dp,
        shadowElevation = if (highContrastEnabled) 0.dp else 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Navigate",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            destinations.forEach { destination ->
                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                NavigationRailItem(
                    selected = selectedId == destination.id,
                    onClick = { /* Handled by combinedClickable below */ },
                    modifier = Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = { onNavigate(destination) },
                        onDoubleClick = { onDoubleClick(destination) }
                    ),
                    icon = {
                        BadgedBox(
                            badge = {
                                val dayDialViewedDate =
                                    LocalChronosShellOverlayController.current?.dayDialViewedDate
                                badgeValueFor(destination.id, shellState, dayDialViewedDate)?.let {
                                    Badge { Text(it) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        }
                    },
                    label = {
                        Text(
                            text = destination.label,
                            textAlign = TextAlign.Center
                        )
                    }
                )
            }
        }
    }
}

private fun badgeValueFor(
    destinationId: String,
    shellState: ChronosShellState,
    dayDialViewedDate: java.time.LocalDate? = null
): String? {
    return when (destinationId) {
        ChronosRoute.SHELL_TODAY -> todayBadgeValue(
            viewedDate = dayDialViewedDate,
            today = java.time.LocalDate.now(),
            missedCount = shellState.missedBlocksCount
        )
        ChronosRoute.SHELL_FOCUS -> if (shellState.focusActive) "•" else null
        ChronosRoute.SHELL_REVIEW -> numericBadge(shellState.unreadInsightsCount)
        else -> null
    }
}

/**
 * The viewed-date indicator outranks the missed-count badge: when the dial shows a
 * date other than today, the badge shows that day-of-month so the off-today state
 * is visible from the bottom bar.
 */
internal fun todayBadgeValue(
    viewedDate: java.time.LocalDate?,
    today: java.time.LocalDate,
    missedCount: Int
): String? =
    if (viewedDate != null && viewedDate != today) {
        viewedDate.dayOfMonth.toString()
    } else {
        numericBadge(missedCount)
    }

private fun numericBadge(count: Int): String? {
    return when {
        count <= 0 -> null
        count > 99 -> "99+"
        else -> min(count, 99).toString()
    }
}

@Composable
private fun rememberChronosShellLayout(): ChronosShellLayout {
    val adaptiveInfo = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)
    return if (adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        ChronosShellLayout.ADAPTIVE
    } else {
        ChronosShellLayout.COMPACT
    }
}
