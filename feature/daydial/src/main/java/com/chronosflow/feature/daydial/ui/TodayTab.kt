package com.chronosflow.feature.daydial.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosflow.core.notifications.TaskContextCommandKind
import com.chronosflow.core.notifications.launchAppTarget
import com.chronosflow.core.notifications.launchTaskContextCommand
import com.chronosflow.core.domain.planner.DialRing
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.components.formatDurationLabel
import com.chronosflow.core.ui.motion.ChronosMotionDefaults
import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.core.ui.motion.ChronosTransitionFactory
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.core.ui.theme.categoryColor
import com.chronosflow.feature.daydial.ChronosDial
import com.chronosflow.feature.daydial.DailyReview
import com.chronosflow.feature.daydial.PlannerHapticCue
import com.chronosflow.feature.daydial.TimeBlockUiModel
import com.chronosflow.feature.daydial.TimeRangeUi
import com.chronosflow.feature.daydial.model.DailyActionKind
import com.chronosflow.feature.daydial.model.DailyActionUiModel
import com.chronosflow.feature.daydial.model.DayQuickContextActionUiModel
import com.chronosflow.feature.daydial.model.DayQuickItemKind
import com.chronosflow.feature.daydial.model.DayQuickItemUiModel
import com.chronosflow.feature.daydial.model.DayQuickItemsUiState
import com.chronosflow.feature.daydial.isAllDayCalendarImport
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.rememberPersistentBoolean
import com.chronosflow.feature.daydial.model.resolveDailyAction

private const val DAY_IN_MINUTES = 1440
internal const val DAY_END_MINUTE = DAY_IN_MINUTES
private const val UNSCHEDULED_TIMELINE_MINUTE = DAY_IN_MINUTES + 1

@Composable
private fun TodaySleepPromptCard(
    onLogSleep: () -> Unit,
    modifier: Modifier = Modifier
) {
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "How did you sleep?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Log last night to track energy patterns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onLogSleep) { Text("Log sleep") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayTab(
    timeBlocks: List<TimeBlockUiModel>,
    freeTime: List<TimeRangeUi>,
    currentMinute: Int,
    isViewingToday: Boolean = true,
    isPageActive: Boolean = true,
    selectedBlockId: String?,
    hapticCue: PlannerHapticCue,
    review: DailyReview,
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    missedBlocks: List<TimeBlockUiModel>,
    manualMissedBlockIds: Set<String> = emptySet(),
    compactMode: Boolean,
    compactWindowStart: Int,
    nightStartMinute: Int = 21 * 60,
    nightEndMinute: Int = 7 * 60,
    glassSurfacesEnabled: Boolean = true,
    highContrastEnabled: Boolean = false,
    showRingGuide: Boolean = true,
    habitsFeatureEnabled: Boolean = true,
    medicationFeatureEnabled: Boolean = true,
    reviewFeatureEnabled: Boolean = true,
    onBlockSelected: (String?) -> Unit,
    onBlockDragStarted: (String, Int) -> Unit,
    onBlockMoved: (String, Int) -> Unit,
    onBlockMoveCommitted: (String, Int) -> Unit,
    onBlockResize: (String, Int, Int) -> Unit,
    onBlockResizeCommitted: (String, Int, Int) -> Unit,
    onEmptyAreaSelected: (Int) -> Unit,
    onEmptyRingSelected: (Int, DialRing) -> Unit = { minute, _ -> onEmptyAreaSelected(minute) },
    onDragCancel: () -> Unit,
    onToggleCompactMode: (() -> Unit)? = null,
    onWindowBack: () -> Unit = {},
    onWindowForward: () -> Unit = {},
    onCenterWindowOnNow: () -> Unit = {},
    onStartFocus: (String) -> Unit,
    onCompleteBlock: (String) -> Unit,
    onUndoMissed: (String) -> Unit = {},
    onShowMissed: () -> Unit,
    onOpenPlanTab: () -> Unit,
    onAiStripAction: (String) -> Unit,
    onOpenReview: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    onOpenMedication: () -> Unit = {},
    quickItems: DayQuickItemsUiState = DayQuickItemsUiState(),
    onQuickTaskDone: (String) -> Unit = {},
    onQuickHabitDone: (String) -> Unit = {},
    onQuickMedicationTaken: (String, Int?) -> Unit = { _, _ -> },
    onQuickMedicationMissed: (String, Int?) -> Unit = { _, _ -> },
    onQuickContextActionFailed: (String) -> Unit = {},
    onOpenPlanned: () -> Unit,
    onOpenActual: () -> Unit,
    onOpenMissedRecovery: () -> Unit,
    showSleepPrompt: Boolean = false,
    onLogSleep: () -> Unit = {},
    contentTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp
) {
    val dailyAction = remember(timeBlocks, activeBlock, nextBlock, missedBlocks) {
        resolveDailyAction(timeBlocks, activeBlock, nextBlock, missedBlocks)
    }
    val dialGlassEnabled = glassSurfacesEnabled && !highContrastEnabled
    val showQuickActions = dailyAction?.kind != DailyActionKind.OPEN_TIME &&
        dailyAction?.kind != DailyActionKind.EMPTY_DAY

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val adaptiveWidthClass = dayDialAdaptiveWidthClass()
        val dialHeight = todayDialHeroHeight(
            baseHeight = chronosDialHeight(adaptiveWidthClass),
            widthClass = adaptiveWidthClass
        )
        val supportingPane = adaptiveWidthClass == DayDialWidthClass.EXPANDED
        val pagePadding = ChronosSpacing.Standard
        val bottomContentPadding = dayDialScrollableBottomPadding(
            contentBottomPadding = contentBottomPadding,
            pageBottomPadding = pagePadding
        )

        if (supportingPane) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = pagePadding, end = pagePadding, bottom = pagePadding)
                    .padding(top = contentTopPadding + pagePadding),
                verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Medium)
            ) {
                DayDialPageHeader(
                    title = DayDialTab.TODAY.label,
                    subtitle = dayDialPrimaryPageSubtitle(DayDialTab.TODAY),
                    icon = DayDialTab.TODAY.icon
                )
                if (showSleepPrompt) {
                    TodaySleepPromptCard(onLogSleep = onLogSleep)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Medium)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Medium)
                    ) {
                        TodayDialHero(
                            dialHeight = dialHeight,
                            timeBlocks = timeBlocks,
                            freeTime = freeTime,
                            currentMinute = currentMinute,
                            isViewingToday = isViewingToday,
                            isPageActive = isPageActive,
                            activeBlock = activeBlock,
                            nextBlock = nextBlock,
                            missedBlocks = missedBlocks,
                            review = review,
                            compactMode = compactMode,
                            compactWindowStart = compactWindowStart,
                            nightStartMinute = nightStartMinute,
                            nightEndMinute = nightEndMinute,
                            selectedBlockId = selectedBlockId,
                            hapticCue = hapticCue,
                            glassSurfacesEnabled = dialGlassEnabled,
                            showRingGuide = showRingGuide,
                            onBlockSelected = onBlockSelected,
                            onCompleteBlock = onCompleteBlock,
                            onBlockDragStarted = onBlockDragStarted,
                            onBlockMoved = onBlockMoved,
                            onBlockMoveCommitted = onBlockMoveCommitted,
                            onBlockResize = onBlockResize,
                            onBlockResizeCommitted = onBlockResizeCommitted,
                            onEmptyAreaSelected = onEmptyAreaSelected,
                            onEmptyRingSelected = onEmptyRingSelected,
                            onDragCancel = onDragCancel,
                            onToggleCompactMode = onToggleCompactMode,
                            onWindowBack = onWindowBack,
                            onWindowForward = onWindowForward,
                            onCenterWindowOnNow = onCenterWindowOnNow
                        )
                        TodayDialLegend(showRingGuide = showRingGuide)
                        DailyReviewHeader(
                            review = review,
                            onPlannedClick = onOpenPlanned,
                            onActualClick = onOpenActual,
                            onMissedClick = onOpenMissedRecovery,
                            onOpenReview = onOpenReview,
                            showReviewAction = reviewFeatureEnabled
                        )
                        Spacer(Modifier.height(bottomContentPadding))
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Medium)
                    ) {
                        DayQuickItemsSection(
                            quickItems = quickItems,
                            habitsEnabled = habitsFeatureEnabled,
                            medicationEnabled = medicationFeatureEnabled,
                            onOpenTasks = onOpenTasks,
                            onOpenHabits = onOpenHabits,
                            onOpenMedication = onOpenMedication,
                            onTaskDone = onQuickTaskDone,
                            onHabitDone = onQuickHabitDone,
                            onMedicationTaken = onQuickMedicationTaken,
                            onMedicationMissed = onQuickMedicationMissed,
                            showContextualActions = true,
                            onContextActionFailed = onQuickContextActionFailed
                        )
                        TodayDetailsSections(
                            dailyAction = dailyAction,
                            showQuickActions = showQuickActions,
                            activeBlock = activeBlock,
                            nextBlock = nextBlock,
                            timeBlocks = timeBlocks,
                            selectedBlockId = selectedBlockId,
                            currentMinute = currentMinute,
                            freeTime = freeTime,
                            manualMissedBlockIds = manualMissedBlockIds,
                            highContrastEnabled = highContrastEnabled,
                            onEmptyAreaSelected = onEmptyAreaSelected,
                            onStartFocus = onStartFocus,
                            onCompleteBlock = onCompleteBlock,
                            onUndoMissed = onUndoMissed,
                            onBlockSelected = onBlockSelected,
                            quickItems = quickItems,
                            onQuickTaskDone = onQuickTaskDone,
                            onQuickHabitDone = onQuickHabitDone,
                            onQuickMedicationTaken = onQuickMedicationTaken,
                            onQuickMedicationMissed = onQuickMedicationMissed,
                            onQuickContextActionFailed = onQuickContextActionFailed,
                            onOpenPlanTab = onOpenPlanTab,
                            onShowMissed = onShowMissed,
                            onAiStripAction = onAiStripAction
                        )
                        Spacer(Modifier.height(bottomContentPadding))
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = dayDialScrollableViewportBottomPadding(contentBottomPadding))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = pagePadding),
                    verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Medium)
                ) {
                    Spacer(Modifier.height(contentTopPadding + compactTodayTopSpacer))
                    DayDialPageHeader(
                        title = DayDialTab.TODAY.label,
                        subtitle = dayDialPrimaryPageSubtitle(DayDialTab.TODAY),
                        icon = DayDialTab.TODAY.icon
                    )
                    if (showSleepPrompt) {
                        TodaySleepPromptCard(onLogSleep = onLogSleep)
                    }
                    TodayDialHero(
                        dialHeight = dialHeight,
                        timeBlocks = timeBlocks,
                        freeTime = freeTime,
                        currentMinute = currentMinute,
                        isViewingToday = isViewingToday,
                        isPageActive = isPageActive,
                        activeBlock = activeBlock,
                        nextBlock = nextBlock,
                        missedBlocks = missedBlocks,
                        review = review,
                        compactMode = compactMode,
                        compactWindowStart = compactWindowStart,
                        nightStartMinute = nightStartMinute,
                        nightEndMinute = nightEndMinute,
                        selectedBlockId = selectedBlockId,
                        hapticCue = hapticCue,
                        glassSurfacesEnabled = dialGlassEnabled,
                        showRingGuide = showRingGuide,
                        onBlockSelected = onBlockSelected,
                        onCompleteBlock = onCompleteBlock,
                        onBlockDragStarted = onBlockDragStarted,
                        onBlockMoved = onBlockMoved,
                        onBlockMoveCommitted = onBlockMoveCommitted,
                        onBlockResize = onBlockResize,
                        onBlockResizeCommitted = onBlockResizeCommitted,
                        onEmptyAreaSelected = onEmptyAreaSelected,
                        onEmptyRingSelected = onEmptyRingSelected,
                        onDragCancel = onDragCancel,
                        onToggleCompactMode = onToggleCompactMode,
                        onWindowBack = onWindowBack,
                        onWindowForward = onWindowForward,
                        onCenterWindowOnNow = onCenterWindowOnNow,
                        topPadding = compactTodayHeroTopPadding,
                        canvasInset = compactTodayDialCanvasInset,
                        dialRadiusScale = compactTodayDialRadiusScale
                    )
                    TodayDialLegend(showRingGuide = showRingGuide)
                    DailyReviewHeader(
                        review = review,
                        onPlannedClick = onOpenPlanned,
                        onActualClick = onOpenActual,
                        onMissedClick = onOpenMissedRecovery,
                        onOpenReview = onOpenReview,
                        showReviewAction = reviewFeatureEnabled
                    )
                    dailyAction?.let { action ->
                        val handlers = todayActionHandlers(
                            action, activeBlock, nextBlock, currentMinute,
                            onOpenPlanTab, onEmptyAreaSelected, onStartFocus, onCompleteBlock,
                            onBlockSelected, onShowMissed, onAiStripAction
                        )
                        DailyActionStrip(
                            action = action,
                            onPrimary = handlers.first,
                            onSecondary = handlers.second
                        )
                    }
                    DayQuickItemsSection(
                        quickItems = quickItems,
                        habitsEnabled = habitsFeatureEnabled,
                        medicationEnabled = medicationFeatureEnabled,
                        onOpenTasks = onOpenTasks,
                        onOpenHabits = onOpenHabits,
                        onOpenMedication = onOpenMedication,
                        onTaskDone = onQuickTaskDone,
                        onHabitDone = onQuickHabitDone,
                        onMedicationTaken = onQuickMedicationTaken,
                        onMedicationMissed = onQuickMedicationMissed,
                        showContextualActions = true,
                        onContextActionFailed = onQuickContextActionFailed
                    )
                    TodayNowAndNextSection(
                        activeBlock = activeBlock,
                        nextBlock = nextBlock,
                        timeBlocks = timeBlocks,
                        selectedBlockId = selectedBlockId,
                        currentMinute = currentMinute,
                        freeTime = freeTime,
                        quickItems = quickItems,
                        manualMissedBlockIds = manualMissedBlockIds,
                        highContrastEnabled = highContrastEnabled,
                        showInlineActions = dailyAction == null,
                        onEmptyAreaSelected = onEmptyAreaSelected,
                        onStartFocus = onStartFocus,
                        onCompleteBlock = onCompleteBlock,
                        onUndoMissed = onUndoMissed,
                        onBlockSelected = onBlockSelected,
                        onContextActionFailed = onQuickContextActionFailed,
                        onAiStripAction = onAiStripAction
                    )
                    if (showQuickActions) {
                        TodayQuickActions(onAiStripAction = onAiStripAction)
                    }
                    Spacer(Modifier.height(bottomContentPadding))
                }
            }
        }
    }
}

@Composable
private fun TodayDialHero(
    dialHeight: androidx.compose.ui.unit.Dp,
    timeBlocks: List<TimeBlockUiModel>,
    freeTime: List<TimeRangeUi>,
    currentMinute: Int,
    isViewingToday: Boolean,
    isPageActive: Boolean,
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    missedBlocks: List<TimeBlockUiModel>,
    manualMissedBlockIds: Set<String> = emptySet(),
    review: DailyReview? = null,
    compactMode: Boolean,
    compactWindowStart: Int,
    nightStartMinute: Int = 21 * 60,
    nightEndMinute: Int = 7 * 60,
    selectedBlockId: String?,
    hapticCue: PlannerHapticCue,
    glassSurfacesEnabled: Boolean,
    showRingGuide: Boolean,
    onBlockSelected: (String?) -> Unit,
    onCompleteBlock: (String) -> Unit,
    onBlockDragStarted: (String, Int) -> Unit,
    onBlockMoved: (String, Int) -> Unit,
    onBlockMoveCommitted: (String, Int) -> Unit,
    onBlockResize: (String, Int, Int) -> Unit,
    onBlockResizeCommitted: (String, Int, Int) -> Unit,
    onEmptyAreaSelected: (Int) -> Unit,
    onEmptyRingSelected: (Int, DialRing) -> Unit,
    onDragCancel: () -> Unit,
    onToggleCompactMode: (() -> Unit)? = null,
    onWindowBack: () -> Unit = {},
    onWindowForward: () -> Unit = {},
    onCenterWindowOnNow: () -> Unit = {},
    topPadding: Dp = chronosDialSectionVerticalPadding,
    bottomPadding: Dp = chronosDialSectionVerticalPadding,
    canvasInset: Dp = chronosDialCanvasInset,
    dialRadiusScale: Float = 1f
) {
    val selectedBlock = timeBlocks.firstOrNull { it.id == selectedBlockId }
    val centerState = remember(currentMinute, selectedBlock, activeBlock, nextBlock, isViewingToday, review, timeBlocks, freeTime) {
        buildDailyDialCenterState(
            currentMinute = currentMinute,
            selectedBlock = selectedBlock,
            activeBlock = activeBlock,
            nextBlock = nextBlock,
            isViewingToday = isViewingToday,
            review = review,
            totalBlocks = timeBlocks.size,
            freeTime = freeTime
        )
    }
    val reduceMotionEnabled = rememberChronosUiSettings().reduceMotionEnabled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = topPadding,
                bottom = bottomPadding,
                start = chronosDialSectionHorizontalPadding,
                end = chronosDialSectionHorizontalPadding
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cross-fading the whole dial between zoom levels avoids interpolating two
        // incompatible angle systems (absolute 24h vs window-relative 12h).
        val zoomTransition = ChronosTransitionFactory.fadeScale(
            durationMillis = if (reduceMotionEnabled) {
                ChronosMotionDefaults.ReducedDurationMillis
            } else {
                ChronosMotionDefaults.DefaultDurationMillis
            },
            easing = ChronosMotionDefaults.MaterialStandardEasing,
            direction = ChronosTransitionDirection.Neutral,
            // Both directions stay <= 1f so the outgoing dial shrinks-and-fades instead of
            // growing past the hero box (which clipped the ring + bottom hour label).
            enterScale = if (reduceMotionEnabled) 1f else 0.94f,
            exitScale = if (reduceMotionEnabled) 1f else 0.94f
        )
        AnimatedContent(
            targetState = compactMode,
            transitionSpec = { zoomTransition.asContentTransform() },
            label = "dialZoomMode",
            modifier = Modifier
                .fillMaxWidth()
                .height(dialHeight)
        ) { windowed ->
            // AnimatedContent measures its contents with loose constraints to learn
            // their sizes, so fillMaxSize would let the dial's aspectRatio expand to
            // the full width and clip the ring top/bottom — pin the height instead.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dialHeight)
            ) {
                ChronosDial(
                    blocks = timeBlocks,
                    freeTimeSegments = freeTime,
                    currentMinute = currentMinute,
                    showNowHand = isViewingToday && isPageActive,
                    compactMode = windowed,
                    compactWindowStart = compactWindowStart,
                    nightStartMinute = nightStartMinute,
                    nightEndMinute = nightEndMinute,
                    selectedBlockId = selectedBlockId,
                    activeBlockId = activeBlock?.id,
                    upcomingBlockId = nextBlock?.id,
                    missedBlockIds = missedBlocks.map { it.id }.toSet(),
                    hapticCue = hapticCue,
                    onBlockSelected = onBlockSelected,
                    onInnerRingBlockActivated = onCompleteBlock,
                    onBlockDragStarted = onBlockDragStarted,
                    onBlockMoved = onBlockMoved,
                    onBlockMoveCommitted = onBlockMoveCommitted,
                    onBlockResize = onBlockResize,
                    onBlockResizeCommitted = onBlockResizeCommitted,
                    onEmptyAreaLongPress = onEmptyAreaSelected,
                    onEmptyRingLongPress = onEmptyRingSelected,
                    onDragEnd = onDragCancel,
                    glassSurfacesEnabled = glassSurfacesEnabled,
                    showRingGuide = showRingGuide,
                    canvasInset = canvasInset,
                    dialRadiusScale = dialRadiusScale,
                    modifier = Modifier.fillMaxSize()
                )
                DailyDialCenterOverlay(
                    state = centerState,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )
            }
        }
        if (onToggleCompactMode != null) {
            // The bottom (6 o'clock) hour label is drawn outside the ring, ~25dp below
            // the dial's box edge, so reserve enough room for it to sit clear of the
            // 12h/24h controls instead of clipping the dial.
            Spacer(Modifier.height(28.dp))
            DialZoomControls(
                compactMode = compactMode,
                compactWindowStart = compactWindowStart,
                currentMinute = currentMinute,
                isViewingToday = isViewingToday,
                onToggleCompactMode = onToggleCompactMode,
                onWindowBack = onWindowBack,
                onWindowForward = onWindowForward,
                onCenterWindowOnNow = onCenterWindowOnNow
            )
        }
    }
}

/**
 * Zoom controls under the dial: 24h/12h toggle plus, when zoomed, half-window
 * paging and a "Now" shortcut whenever the current time is outside the window.
 */
@Composable
private fun DialZoomControls(
    compactMode: Boolean,
    compactWindowStart: Int,
    currentMinute: Int,
    isViewingToday: Boolean,
    onToggleCompactMode: () -> Unit,
    onWindowBack: () -> Unit,
    onWindowForward: () -> Unit,
    onCenterWindowOnNow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = compactMode,
            onClick = onToggleCompactMode,
            label = { Text(if (compactMode) "12h" else "24h") },
            modifier = Modifier.semantics {
                contentDescription = dialZoomToggleActionLabel(compactMode)
            }
        )
        if (compactMode) {
            IconButton(
                onClick = onWindowBack,
                modifier = Modifier.semantics { contentDescription = "Show earlier hours" }
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null)
            }
            Text(
                text = dialZoomWindowLabel(compactWindowStart),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = onWindowForward,
                modifier = Modifier.semantics { contentDescription = "Show later hours" }
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
            if (isViewingToday && !dialZoomMinuteInWindow(currentMinute, compactWindowStart)) {
                TextButton(onClick = onCenterWindowOnNow) { Text("Now") }
            }
        }
    }
}

internal fun dialZoomToggleActionLabel(compactMode: Boolean): String =
    if (compactMode) "Switch to 24 hour dial" else "Zoom dial to 12 hours"

internal fun dialZoomWindowLabel(windowStart: Int): String =
    "${formatMinute(windowStart)} – ${formatMinute((windowStart + 720) % DAY_IN_MINUTES)}"

internal fun dialZoomMinuteInWindow(minute: Int, windowStart: Int): Boolean =
    ((minute - windowStart + DAY_IN_MINUTES) % DAY_IN_MINUTES) < 720

@Composable
private fun TodayDetailsSections(
    dailyAction: DailyActionUiModel?,
    showQuickActions: Boolean,
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    timeBlocks: List<TimeBlockUiModel>,
    selectedBlockId: String?,
    currentMinute: Int,
    freeTime: List<TimeRangeUi>,
    manualMissedBlockIds: Set<String>,
    highContrastEnabled: Boolean,
    onEmptyAreaSelected: (Int) -> Unit,
    onStartFocus: (String) -> Unit,
    onCompleteBlock: (String) -> Unit,
    onUndoMissed: (String) -> Unit = {},
    onBlockSelected: (String?) -> Unit,
    quickItems: DayQuickItemsUiState = DayQuickItemsUiState(),
    onQuickTaskDone: (String) -> Unit = {},
    onQuickHabitDone: (String) -> Unit = {},
    onQuickMedicationTaken: (String, Int?) -> Unit = { _, _ -> },
    onQuickMedicationMissed: (String, Int?) -> Unit = { _, _ -> },
    onQuickContextActionFailed: (String) -> Unit = {},
    onOpenPlanTab: () -> Unit,
    onShowMissed: () -> Unit,
    onAiStripAction: (String) -> Unit
) {
    dailyAction?.let { action ->
        val handlers = todayActionHandlers(
            action, activeBlock, nextBlock, currentMinute,
            onOpenPlanTab, onEmptyAreaSelected, onStartFocus, onCompleteBlock,
            onBlockSelected, onShowMissed, onAiStripAction
        )
        DailyActionStrip(
            action = action,
            onPrimary = handlers.first,
            onSecondary = handlers.second
        )
    }
    TodayNowAndNextSection(
        activeBlock = activeBlock,
        nextBlock = nextBlock,
        timeBlocks = timeBlocks,
        selectedBlockId = selectedBlockId,
        currentMinute = currentMinute,
        freeTime = freeTime,
        quickItems = quickItems,
        manualMissedBlockIds = manualMissedBlockIds,
        highContrastEnabled = highContrastEnabled,
        showInlineActions = dailyAction == null,
        onEmptyAreaSelected = onEmptyAreaSelected,
        onStartFocus = onStartFocus,
        onCompleteBlock = onCompleteBlock,
        onUndoMissed = onUndoMissed,
        onBlockSelected = onBlockSelected,
        onContextActionFailed = onQuickContextActionFailed,
        onAiStripAction = onAiStripAction
    )
    TodayDetailedTimelineSection(
        timeBlocks = timeBlocks,
        quickItems = quickItems,
        onBlockSelected = onBlockSelected,
        onTaskDone = onQuickTaskDone,
        onHabitDone = onQuickHabitDone,
        onMedicationTaken = onQuickMedicationTaken,
        onMedicationMissed = onQuickMedicationMissed,
        onContextActionFailed = onQuickContextActionFailed
    )
    if (showQuickActions) {
        TodayQuickActions(onAiStripAction = onAiStripAction)
    }
}

private data class TodayTimelineItem(
    val id: String,
    val title: String,
    val timeLabel: String,
    val sourceLabel: String,
    val detail: String,
    val statusText: String,
    val isDone: Boolean,
    val sortMinute: Int,
    val accentColor: Color,
    val primaryActionLabel: String? = null,
    val onPrimaryAction: (() -> Unit)? = null,
    val secondaryActionLabel: String? = null,
    val onSecondaryAction: (() -> Unit)? = null,
    val contextAction: DayQuickContextActionUiModel? = null,
    val onRowClick: (() -> Unit)? = null
)

private fun buildTodayTimelineItems(
    timeBlocks: List<TimeBlockUiModel>,
    quickItems: DayQuickItemsUiState,
    onBlockSelected: (String?) -> Unit,
    onTaskDone: (String) -> Unit,
    onHabitDone: (String) -> Unit,
    onMedicationTaken: (String, Int?) -> Unit,
    onMedicationMissed: (String, Int?) -> Unit
): List<TodayTimelineItem> = buildList {
    addAll(timeBlocks.map { block ->
        val startMinute = if (block.isAllDayCalendarImport()) 0 else block.startMinuteOfDay
        val rangeLabel = if (block.isAllDayCalendarImport()) {
            "All day"
        } else {
            "${formatMinute(block.startMinuteOfDay)} – ${formatMinute(block.startMinuteOfDay + block.durationMinutes)}"
        }
        TodayTimelineItem(
            id = "block:${block.id}",
            title = block.title.ifBlank { if (block.isAllDayCalendarImport()) "All-day calendar note" else "Focus block" },
            timeLabel = rangeLabel,
            sourceLabel = timelineSourceLabel(block),
            detail = block.category,
            statusText = if (block.isAllDayCalendarImport()) "Calendar item" else block.category.replaceFirstChar { it.uppercase() },
            isDone = false,
            sortMinute = startMinute,
            accentColor = categoryColor(block.category),
            onRowClick = { onBlockSelected(block.id) }
        )
    })
    addAll(quickItems.tasks.map { item ->
        item.toTimelineItem(
            kind = DayQuickItemKind.TASK,
            primaryActionLabel = if (item.isDone) "Done" else "Complete",
            onPrimaryAction = { onTaskDone(item.id) }
        )
    })
    addAll(quickItems.habits.map { item ->
        item.toTimelineItem(
            kind = DayQuickItemKind.HABIT,
            primaryActionLabel = if (item.isDone) "Done" else "Complete",
            onPrimaryAction = { onHabitDone(item.id) }
        )
    })
    addAll(quickItems.medications.map { item ->
        item.toTimelineItem(
            kind = DayQuickItemKind.MEDICATION,
            primaryActionLabel = if (item.isDone) "Taken" else "Taken",
            secondaryActionLabel = if (item.isDone) "Missed" else "Missed",
            onPrimaryAction = {
                onMedicationTaken(item.baseMedicationPlanId(), item.scheduledMinuteOfDay)
            },
            onSecondaryAction = {
                onMedicationMissed(item.baseMedicationPlanId(), item.scheduledMinuteOfDay)
            }
        )
    })
}.sortedWith(
    compareBy<TodayTimelineItem> { it.sortMinute }
        .thenBy { it.sourceLabel }
        .thenBy { it.title.lowercase() }
)

private fun DayQuickItemUiModel.toTimelineItem(
    kind: DayQuickItemKind,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
): TodayTimelineItem {
    val scheduledMinute = scheduledMinuteOfDay ?: UNSCHEDULED_TIMELINE_MINUTE
    val timeLabel = scheduledMinuteOfDay?.let { formatMinute(it) } ?: "Unscheduled"
    return TodayTimelineItem(
        id = "quick:$id",
        title = title.ifBlank { "Unnamed item" },
        timeLabel = timeLabel,
        sourceLabel = when (kind) {
            DayQuickItemKind.TASK -> "Task"
            DayQuickItemKind.HABIT -> "Habit"
            DayQuickItemKind.MEDICATION -> "Medication"
        },
        detail = detail,
        statusText = if (isDone) "Completed" else status,
        isDone = isDone,
        sortMinute = scheduledMinute,
        accentColor = timelineQuickItemColor(kind),
        primaryActionLabel = primaryActionLabel,
        onPrimaryAction = onPrimaryAction,
        secondaryActionLabel = secondaryActionLabel,
        onSecondaryAction = onSecondaryAction?.takeIf { !isDone },
        contextAction = contextAction,
        onRowClick = null
    )
}

private fun DayQuickItemUiModel.baseMedicationPlanId(): String =
    id.substringBefore(":")

private fun timelineSourceLabel(block: TimeBlockUiModel): String = when {
    block.isAllDayCalendarImport() -> "All-day calendar"
    block.calendarEventId != null -> "Calendar"
    block.taskId != null -> "Task"
    block.habitId != null -> "Habit"
    block.medicationPlanId != null -> "Medication"
    else -> "Planned"
}

private fun timelineQuickItemColor(kind: DayQuickItemKind): Color = when (kind) {
    DayQuickItemKind.TASK -> Color(0xFF2F6BEA)
    DayQuickItemKind.HABIT -> Color(0xFF5DAA54)
    DayQuickItemKind.MEDICATION -> Color(0xFFD17A2A)
}

internal fun todayContextActionForBlock(
    block: TimeBlockUiModel,
    quickItems: DayQuickItemsUiState
): DayQuickContextActionUiModel? = when {
    block.taskId != null -> quickItems.tasks.firstOrNull { it.id == block.taskId }?.contextAction
    block.habitId != null -> quickItems.habits.firstOrNull { it.id == block.habitId }?.contextAction
    else -> null
}

@Composable
private fun TodayDetailedTimelineSection(
    timeBlocks: List<TimeBlockUiModel>,
    quickItems: DayQuickItemsUiState,
    onBlockSelected: (String?) -> Unit,
    onTaskDone: (String) -> Unit,
    onHabitDone: (String) -> Unit,
    onMedicationTaken: (String, Int?) -> Unit,
    onMedicationMissed: (String, Int?) -> Unit,
    onContextActionFailed: (String) -> Unit
) {
    val context = LocalContext.current
    val timelineItems = remember(timeBlocks, quickItems) {
        buildTodayTimelineItems(
            timeBlocks = timeBlocks,
            quickItems = quickItems,
            onBlockSelected = onBlockSelected,
            onTaskDone = onTaskDone,
            onHabitDone = onHabitDone,
            onMedicationTaken = onMedicationTaken,
            onMedicationMissed = onMedicationMissed
        )
    }

    ChronosSectionTitle(title = "Detailed calendar")
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        if (timelineItems.isEmpty()) {
            Text(
                text = "No calendar items or scheduled tasks yet for this date.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                timelineItems.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    TodayDetailedTimelineItemRow(
                        item = item,
                        onContextAction = { action ->
                            val launched = launchTodayTimelineContextAction(context, action)
                            if (!launched) {
                                onContextActionFailed("No app can open ${action.label}")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayDetailedTimelineItemRow(
    item: TodayTimelineItem,
    onContextAction: (DayQuickContextActionUiModel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
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
                    text = "${item.sourceLabel} · ${item.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                ) {
                    if (item.primaryActionLabel != null) {
                        Button(
                            onClick = item.onPrimaryAction ?: {},
                            enabled = !item.isDone && item.onPrimaryAction != null,
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
                    item.contextAction?.let { action ->
                        TodayTimelineContextActionPill(
                            action = action,
                            onClick = { onContextAction(action) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTimelineContextActionPill(
    action: DayQuickContextActionUiModel,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(action.label) },
        leadingIcon = {
            Icon(
                imageVector = todayTimelineContextActionIcon(action),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = Modifier.semantics {
            contentDescription = action.contentDescription
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

private fun launchTodayTimelineContextAction(context: Context, action: DayQuickContextActionUiModel): Boolean {
    action.taskCommand?.let { command -> return launchTaskContextCommand(context, command) }
    action.appLaunchTarget?.let { target -> return launchAppTarget(context, target) }
    return false
}

private fun todayTimelineContextActionIcon(action: DayQuickContextActionUiModel): ImageVector {
    return when (action.taskCommand?.kind) {
        TaskContextCommandKind.CALL -> Icons.Default.Call
        TaskContextCommandKind.EMAIL -> Icons.Default.Email
        TaskContextCommandKind.LINK -> Icons.Default.Link
        TaskContextCommandKind.MAP -> Icons.Default.Map
        TaskContextCommandKind.APP -> Icons.Default.Apps
        TaskContextCommandKind.IMAGE -> Icons.Default.Image
        TaskContextCommandKind.FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
        TaskContextCommandKind.SCHEDULE,
        TaskContextCommandKind.EDIT,
        TaskContextCommandKind.COMPLETE -> Icons.Default.Check
        null -> Icons.Default.Apps
    }
}

private fun todayActionHandlers(
    action: DailyActionUiModel,
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    currentMinute: Int,
    onOpenPlanTab: () -> Unit,
    onEmptyAreaSelected: (Int) -> Unit,
    onStartFocus: (String) -> Unit,
    onCompleteBlock: (String) -> Unit,
    onBlockSelected: (String?) -> Unit,
    onShowMissed: () -> Unit,
    onAiStripAction: (String) -> Unit
): Pair<() -> Unit, (() -> Unit)?> = dailyActionPrimaryHandler(
    action = action,
    activeBlockId = activeBlock?.id,
    nextBlockId = nextBlock?.id,
    onPlanDay = onOpenPlanTab,
    onAddBlock = onEmptyAreaSelected,
    currentMinute = currentMinute,
    onStartFocus = onStartFocus,
    onCompleteBlock = onCompleteBlock,
    onStartNext = onStartFocus,
    onPrepareNext = onBlockSelected,
    onReviewMissed = onShowMissed,
    onFillGaps = { onAiStripAction("Fill gaps") }
) to dailyActionSecondaryHandler(
    action = action,
    activeBlockId = activeBlock?.id,
    nextBlockId = nextBlock?.id,
    onAddBlock = onEmptyAreaSelected,
    currentMinute = currentMinute,
    onCompleteBlock = onCompleteBlock,
    onPrepareNext = onBlockSelected,
    onFillGaps = { onAiStripAction("Fill gaps") },
    onReflowDay = { onAiStripAction("Rebalance") }
)

@Composable
private fun TodayNowAndNextSection(
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    timeBlocks: List<TimeBlockUiModel>,
    selectedBlockId: String?,
    currentMinute: Int,
    freeTime: List<TimeRangeUi>,
    quickItems: DayQuickItemsUiState,
    manualMissedBlockIds: Set<String>,
    highContrastEnabled: Boolean,
    showInlineActions: Boolean,
    onEmptyAreaSelected: (Int) -> Unit,
    onStartFocus: (String) -> Unit,
    onCompleteBlock: (String) -> Unit,
    onUndoMissed: (String) -> Unit = {},
    onBlockSelected: (String?) -> Unit,
    onContextActionFailed: (String) -> Unit,
    onAiStripAction: (String) -> Unit
) {
    val context = LocalContext.current
    val currentBlock = activeBlock ?: timeBlocks.find { it.id == selectedBlockId }
    val nextBlockToShow = nextBlock?.takeIf { it.id != currentBlock?.id }
    val showNext = nextBlockToShow != null
    val currentContextAction = currentBlock?.let { todayContextActionForBlock(it, quickItems) }
    val nextContextAction = nextBlockToShow?.let { todayContextActionForBlock(it, quickItems) }
    val onContextAction: (DayQuickContextActionUiModel) -> Unit = { action ->
        val launched = launchTodayTimelineContextAction(context, action)
        if (!launched) {
            onContextActionFailed("No app can open ${action.label}")
        }
    }

    ChronosSectionTitle(title = if (showNext) "Now & next" else "Now")

    ChronosListCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (currentBlock == null) { { onEmptyAreaSelected(currentMinute) } } else null
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
            if (currentBlock != null) {
                TodayNowBlockContent(
                    block = currentBlock,
                    currentMinute = currentMinute,
                    isManuallyMissed = currentBlock.id in manualMissedBlockIds,
                    highContrastEnabled = highContrastEnabled,
                    showInlineActions = showInlineActions,
                    contextAction = currentContextAction,
                    onStartFocus = onStartFocus,
                    onCompleteBlock = onCompleteBlock,
                    onUndoMissed = onUndoMissed,
                    onContextAction = onContextAction
                )
            } else {
                TodayOpenTimeContent(
                    nextBlock = nextBlock,
                    currentMinute = currentMinute,
                    freeTime = freeTime,
                    showInlineActions = showInlineActions,
                    onAiStripAction = onAiStripAction
                )
            }

            if (nextBlockToShow != null) {
                if (currentBlock != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                TodayNextBlockRow(
                    nextBlock = nextBlockToShow,
                    currentMinute = currentMinute,
                    contextAction = nextContextAction,
                    onBlockSelected = onBlockSelected,
                    onContextAction = onContextAction
                )
            }
        }
    }
}

@Composable
private fun TodayNowBlockContent(
    block: TimeBlockUiModel,
    currentMinute: Int,
    isManuallyMissed: Boolean,
    highContrastEnabled: Boolean,
    showInlineActions: Boolean,
    contextAction: DayQuickContextActionUiModel?,
    onStartFocus: (String) -> Unit,
    onCompleteBlock: (String) -> Unit,
    onUndoMissed: (String) -> Unit = {},
    onContextAction: (DayQuickContextActionUiModel) -> Unit
) {
    val accentColor = categoryColor(block.category)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${formatMinute(block.startMinuteOfDay)} – ${formatMinute(block.startMinuteOfDay + block.durationMinutes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isManuallyMissed) {
                Text(
                    text = "Marked missed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = { onUndoMissed(block.id) },
                    modifier = Modifier.semantics {
                        contentDescription = todayUndoMissedActionLabel(block)
                    }
                ) {
                    Text("Undo missed")
                }
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(ChronosSpacing.Micro))
                .background(
                    if (highContrastEnabled) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        accentColor.copy(alpha = 0.15f)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = block.category.lowercase().replaceFirstChar { it.uppercase() },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highContrastEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    accentColor
                }
            )
        }
    }

    val timeLeft = remainingMinutesInBlock(block, currentMinute)
    val totalMinutes = block.durationMinutes.coerceAtLeast(1)
    val progress = (timeLeft.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)

    LinearProgressIndicator(
        progress = { 1f - progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primaryContainer
    )
    Text(
        text = "${formatDurationLabel(timeLeft)} left",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )

    contextAction?.let { action ->
        TodayTimelineContextActionPill(
            action = action,
            onClick = { onContextAction(action) }
        )
    }

    if (showInlineActions) {
        Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            Button(
                onClick = { onStartFocus(block.id) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1.2f)
                    .semantics {
                        contentDescription = todayStartFocusActionLabel(block)
                    },
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Start focus", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { onCompleteBlock(block.id) },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = todayCompleteBlockActionLabel(block)
                    },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Complete", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TodayOpenTimeContent(
    nextBlock: TimeBlockUiModel?,
    currentMinute: Int,
    freeTime: List<TimeRangeUi>,
    showInlineActions: Boolean,
    onAiStripAction: (String) -> Unit
) {
    val nextStartMinute = nextBlock?.startMinuteOfDay
    val openWindowEndMinute = freeTime.firstOrNull()?.endMinute
    val minutesUntilNext = todayOpenTimeMinutesUntil(
        nextStartMinute = nextStartMinute,
        currentMinute = currentMinute,
        openWindowEndMinute = openWindowEndMinute
    ) ?: 0
    val fillGapTargetMinute = nextStartMinute ?: openWindowEndMinute ?: (DAY_END_MINUTE - 1)
    Text(
        text = "Open time",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = todayOpenTimeSummaryLabel(
            nextStartMinute = nextStartMinute,
            currentMinute = currentMinute,
            openWindowEndMinute = openWindowEndMinute
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (showInlineActions) {
        OutlinedButton(
            onClick = { onAiStripAction("Fill gaps") },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = todayOpenTimeFillGapActionLabel(fillGapTargetMinute, minutesUntilNext)
                },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Fill gap", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TodayNextBlockRow(
    nextBlock: TimeBlockUiModel,
    currentMinute: Int,
    contextAction: DayQuickContextActionUiModel?,
    onBlockSelected: (String?) -> Unit,
    onContextAction: (DayQuickContextActionUiModel) -> Unit
) {
    val nextAccentColor = categoryColor(nextBlock.category)
    val gapFromNow = ((nextBlock.startMinuteOfDay - currentMinute + DAY_IN_MINUTES) % DAY_IN_MINUTES)
    Column(
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = todayNextBlockActionLabel(nextBlock),
                    role = Role.Button,
                    onClick = { onBlockSelected(nextBlock.id) }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(nextAccentColor)
            )
            Spacer(modifier = Modifier.width(ChronosSpacing.Standard))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Next · ${nextBlock.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatMinute(nextBlock.startMinuteOfDay)} – ${formatMinute(nextBlock.startMinuteOfDay + nextBlock.durationMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "in ${formatDurationLabel(gapFromNow)}",
                style = MaterialTheme.typography.labelMedium,
                color = nextAccentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        contextAction?.let { action ->
            TodayTimelineContextActionPill(
                action = action,
                onClick = { onContextAction(action) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayQuickActions(onAiStripAction: (String) -> Unit) {
    ChronosSectionTitle(title = "Quick actions")
    val suggestions = listOf(
        "Fill gap" to "Fill gaps",
        "Rebalance" to "Rebalance",
        "Add breaks" to "Add breaks",
        "Protect focus" to "Protect focus"
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        suggestions.forEach { (display, actionLabel) ->
            AssistChip(
                onClick = { onAiStripAction(actionLabel) },
                label = { Text(display) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

internal fun todayStartFocusActionLabel(block: TimeBlockUiModel): String =
    "Start focus for ${block.title}"

internal fun todayCompleteBlockActionLabel(block: TimeBlockUiModel): String =
    "Complete ${block.title}"

internal fun todayUndoMissedActionLabel(block: TimeBlockUiModel): String =
    "Undo missed mark for ${block.title}"

internal fun todayNextBlockActionLabel(block: TimeBlockUiModel): String =
    "Open next block ${block.title}"

internal fun todayOpenTimeAddBlockActionLabel(currentMinute: Int): String =
    "Add block at ${formatMinute(currentMinute)}"

internal fun todayOpenTimeFillGapActionLabel(nextStartMinute: Int, minutesUntilNext: Int): String =
    "Fill $minutesUntilNext minute gap until ${formatMinute(nextStartMinute)}"

internal fun todayOpenTimeSummaryLabel(
    nextStartMinute: Int?,
    currentMinute: Int,
    openWindowEndMinute: Int?
): String {
    val minutesUntilNext = todayOpenTimeMinutesUntil(
        nextStartMinute = nextStartMinute,
        currentMinute = currentMinute,
        openWindowEndMinute = openWindowEndMinute
    )
    if (nextStartMinute != null) {
        return "Free until ${formatMinute(nextStartMinute)} · ${formatDurationLabel(minutesUntilNext ?: 0)}"
    }
    return if (minutesUntilNext != null && minutesUntilNext > 0) {
        "Free for the rest of the day · ${formatDurationLabel(minutesUntilNext)}"
    } else {
        "Free for the rest of the day"
    }
}

private fun todayOpenTimeMinutesUntil(
    nextStartMinute: Int?,
    currentMinute: Int,
    openWindowEndMinute: Int?
): Int? {
    val targetMinute = nextStartMinute ?: openWindowEndMinute ?: return null
    return (targetMinute - currentMinute + DAY_IN_MINUTES) % DAY_IN_MINUTES
}

private fun formatMinute(minute: Int): String {
    val normalized = ((minute % DAY_IN_MINUTES) + DAY_IN_MINUTES) % DAY_IN_MINUTES
    val hour = normalized / 60
    val m = normalized % 60
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return "%d:%02d %s".format(displayHour, m, suffix)
}

/**
 * Compact ring legend shown directly under the dial, with a one-time
 * dismissible tip explaining the rings for first-time users.
 */
@Composable
private fun TodayDialLegend(showRingGuide: Boolean) {
    if (!showRingGuide) return
    var legendTipSeen by rememberPersistentBoolean("ring_legend_tip_seen", false)
    DailyDialInlineLegend()
    if (!legendTipSeen) {
        DailyDialLegendTip(onDismiss = { legendTipSeen = true })
    }
}
