package com.chronosflow.feature.daydial.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosflow.core.ai.AssistNarrative
import com.chronosflow.core.ai.FocusAssistSource
import com.chronosflow.core.ai.FocusNextBlockSuggestion
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.genai.GenAiAssistCopy
import com.chronosflow.core.ai.genai.GenAiRuntimeStatus
import com.chronosflow.core.domain.model.MoodEnergyCheckIn
import com.chronosflow.feature.daydial.latestFocusMoodAccent
import com.chronosflow.core.ui.components.ChronosEmptyState
import com.chronosflow.core.ui.components.ChronosWarningBanner
import com.chronosflow.core.ui.components.FocusGlassCard
import com.chronosflow.core.ui.components.FocusMissedBadge
import com.chronosflow.core.ui.components.FocusCategoryChip
import com.chronosflow.core.ui.components.FocusSessionIconButton
import com.chronosflow.core.ui.motion.ChronosValueAnimationFactory
import com.chronosflow.core.ui.components.FocusTimerRing
import com.chronosflow.core.ui.components.formatDurationLabel
import com.chronosflow.core.ui.components.formatFocusCountdown
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.core.ui.theme.categoryColor
import com.chronosflow.core.ui.theme.rememberFocusTimerAccent
import com.chronosflow.feature.daydial.FocusExecutionState
import com.chronosflow.feature.daydial.FocusExecutionStatus
import com.chronosflow.feature.daydial.TimeBlockUiModel
import com.chronosflow.feature.daydial.model.FocusPhaseKind
import com.chronosflow.feature.daydial.model.FocusPhasePlanner
import com.chronosflow.feature.daydial.isAllDayCalendarImport
import com.chronosflow.feature.daydial.model.DayDialTab
import kotlin.math.roundToInt

internal data class FocusTabBriefingUiState(
    val window: String,
    val pace: String,
    val nextStep: FocusTabNextStepUiState
)

internal data class FocusTabNextStepUiState(
    val label: String,
    val urgent: Boolean
)

internal data class FocusTabDraftUiState(
    val title: String,
    val message: String
)

private val FocusCaptureWhitespaceRegex = Regex("\\s+")
private val FocusCaptureIntentPrefixRegex = Regex(
    pattern = "^(?:focus|deep work|work on)(?:\\s+(?:on|for))?[:\\-]?\\s+",
    option = RegexOption.IGNORE_CASE
)
private val FocusCaptureDurationRegex = Regex(
    pattern = "\\b(?:for\\s+)?(?:a\\s+|an\\s+)?(\\d+(?:\\.\\d+)?)\\s*-?\\s*(minutes?|mins?|min|m|hours?|hrs?|hr|h)\\b",
    option = RegexOption.IGNORE_CASE
)
private val FocusCaptureCasualDurationRegex = Regex(
    pattern = "\\b(?:for\\s+)?(?:(?:a|an)\\s+hour|half\\s+an?\\s+hour|a\\s+half\\s+hour)\\b",
    option = RegexOption.IGNORE_CASE
)
private val FocusCaptureClockTimeRegex = Regex(
    pattern = "\\b(?:at|from|starting\\s+at|start\\s+at)\\s+(?:(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)|(\\d{1,2}):(\\d{2}))\\b",
    option = RegexOption.IGNORE_CASE
)
private val FocusCaptureNamedTimeRegex = Regex(
    pattern = "\\b(?:at|from|starting\\s+at|start\\s+at)\\s+(noon|midnight)\\b",
    option = RegexOption.IGNORE_CASE
)
private val FocusCaptureLeadingConnectorRegex = Regex(
    pattern = "^(?:on|for|to)\\s+",
    option = RegexOption.IGNORE_CASE
)
private val FocusCaptureBoundaryPunctuationRegex = Regex("^[\\s:,-]+|[\\s:,-]+$")

internal data class FocusTabBriefingBlock(
    val startMinuteOfDay: Int,
    val durationMinutes: Int
)

@Composable
internal fun FocusTab(
    selectedBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    initialFocusCapture: String? = null,
    privacyMode: PrivacyMode,
    focusSession: FocusExecutionState,
    remainingSeconds: Long,
    elapsedSeconds: Long,
    reduceMotionEnabled: Boolean = false,
    highContrastEnabled: Boolean = false,
    onStart: (String) -> Unit,
    onStartWithBreaks: (String, Int, Int) -> Unit = { id, _, _ -> onStart(id) },
    onAdvancePhase: () -> Unit = {},
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onExtend: () -> Unit,
    onShorten: () -> Unit,
    onAdjustByMinutes: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlanTab: () -> Unit = {},
    onPlanCapturedFocus: (String) -> Unit = { onOpenPlanTab() },
    onEndDay: () -> Unit,
    sessionResumedBanner: String? = null,
    onDismissSessionResumedBanner: () -> Unit = {},
    moodEnergyCheckIns: List<MoodEnergyCheckIn> = emptyList(),
    moodCheckInCoaching: AssistNarrative? = null,
    focusGuidance: AssistNarrative? = null,
    focusNextBlockSuggestion: FocusNextBlockSuggestion? = null,
    onRequestNextFocusSuggestion: () -> Unit = {},
    onRefreshFocusGuidance: (Long) -> Unit = {},
    onClearFocusGuidance: () -> Unit = {},
    genAiRuntimeStatus: GenAiRuntimeStatus = GenAiRuntimeStatus(),
    cachedMoodScore: Int? = null,
    cachedEnergyScore: Int? = null,
    linkedBlockManuallyMissed: Boolean = false,
    onSaveMoodEnergyCheckIn: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    contentTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp
) {
    val scrollState = rememberScrollState()
    val sessionActive = isFocusSessionActiveForTab(focusSession)
    val showSessionUi = sessionActive
    val selectedReadyBlock = focusTabReadyBlock(selectedBlock, sessionActive)
    val allDayCalendarNoteBlock = focusTabAllDayCalendarNoteBlock(selectedBlock, sessionActive)
    val nextFocusBlock = focusTabNextFocusableBlock(nextBlock)
    val captureDraft = focusTabDraftUiState(initialFocusCapture, selectedReadyBlock, sessionActive)
    val showBlockReady = selectedReadyBlock != null
    val briefing = focusTabBriefingUiState(
        currentBlock = selectedReadyBlock ?: selectedBlock?.takeIf { showSessionUi && !it.isAllDayCalendarImport() },
        nextBlock = nextFocusBlock,
        focusSession = focusSession,
        sessionActive = showSessionUi,
        remainingSeconds = remainingSeconds,
        elapsedSeconds = elapsedSeconds
    )
    val (moodScore, energyScore) = latestFocusMoodAccent(
        checkIns = moodEnergyCheckIns,
        blockId = selectedBlock?.id ?: focusSession.blockId,
        cachedMoodScore = cachedMoodScore,
        cachedEnergyScore = cachedEnergyScore
    )
    LaunchedEffect(sessionActive, selectedReadyBlock?.id) {
        if (!sessionActive) onRequestNextFocusSuggestion()
    }
    LaunchedEffect(sessionActive, focusSession.status, focusSession.blockId) {
        if (sessionActive) {
            onRefreshFocusGuidance(remainingSeconds)
        } else {
            onClearFocusGuidance()
        }
    }
    val pagePadding = ChronosSpacing.Standard
    val bottomContentPadding = dayDialScrollableBottomPadding(
        contentBottomPadding = contentBottomPadding,
        pageBottomPadding = pagePadding
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = pagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Medium)
    ) {
        // The tab scrolls full-bleed under the floating glass bar, so the bar
        // inset arrives as scroll padding rather than a hard layout edge.
        Spacer(Modifier.height(contentTopPadding + pagePadding))
        DayDialPageHeader(
            title = DayDialTab.FOCUS.label,
            subtitle = dayDialPrimaryPageSubtitle(DayDialTab.FOCUS),
            icon = DayDialTab.FOCUS.icon
        )
        if (showBlockReady) {
            val block = selectedReadyBlock!!
            val blockColor = rememberFocusTimerAccent(
                blockCategory = block.category,
                blockId = block.id,
                moodScore = moodScore,
                energyScore = energyScore
            )
            FocusGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
                ) {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatFocusMinute(block.startMinuteOfDay)} – ${formatFocusMinute(block.startMinuteOfDay + block.durationMinutes)} · ${formatDurationLabel(block.durationMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    FocusCategoryChip(
                        category = block.category,
                        highContrastEnabled = highContrastEnabled
                    )
                    if (linkedBlockManuallyMissed) {
                        FocusMissedBadge()
                    }
                    var splitOptionIndex by rememberSaveable(block.id) { mutableIntStateOf(0) }
                    FocusSplitSelector(
                        blockDurationMinutes = block.durationMinutes,
                        selectedIndex = splitOptionIndex,
                        onSelect = { splitOptionIndex = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val option = FocusSplitOptions[splitOptionIndex]
                            onStartWithBreaks(block.id, option.workMinutes, option.breakMinutes)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start focus", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else if (allDayCalendarNoteBlock != null) {
            ChronosEmptyState(
                title = "All-day calendar note",
                message = focusTabAllDayCalendarNoteMessage(allDayCalendarNoteBlock),
                modifier = Modifier.fillMaxWidth(),
                action = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                    ) {
                        Button(
                            onClick = onOpenPlanTab,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Plan focus block", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Focus settings", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            )
        } else if (!showSessionUi) {
            ChronosEmptyState(
                title = captureDraft?.title ?: if (nextFocusBlock != null) "Next block ready" else "Ready to focus",
                message = captureDraft?.message ?: if (nextFocusBlock != null) {
                        nextFocusBlock.title
                    } else {
                        "Plan your day, then start a focus session from a block."
                    },
                modifier = Modifier.fillMaxWidth(),
                action = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                    ) {
                        if (captureDraft != null) {
                            Button(
                                onClick = { onPlanCapturedFocus(captureDraft.message) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Plan captured focus", fontWeight = FontWeight.SemiBold)
                            }
                        } else if (nextFocusBlock != null) {
                            Button(
                                onClick = { onStart(nextFocusBlock.id) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start next: ${nextFocusBlock.title}", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Button(
                                onClick = onOpenPlanTab,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Plan day", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Focus settings", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            )
        } else {
            sessionResumedBanner?.let { message ->
                ChronosWarningBanner(
                    title = "Session resumed",
                    message = message,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onDismissSessionResumedBanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = focusSessionResumedDismissActionLabel()
                        }
                ) {
                    Text("Dismiss")
                }
            }
            val activeTitle = focusTabActiveTitle(selectedBlock?.title, focusSession)
            val isSplit = focusSession.isSplitSession
            val onBreak = focusSession.isOnBreak
            val awaitingAdvance = focusSession.awaitingPhaseAdvance
            val phaseMinutes = focusSession.currentPhase?.durationMinutes
            val totalSeconds = when {
                phaseMinutes != null -> (phaseMinutes * 60).coerceAtLeast(1)
                selectedBlock != null -> (selectedBlock.durationMinutes * 60).coerceAtLeast(1)
                else -> 1500
            }
            val progressFraction = (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
            val blockAccent = rememberFocusTimerAccent(
                blockCategory = selectedBlock?.category,
                blockId = selectedBlock?.id,
                moodScore = moodScore,
                energyScore = energyScore
            )
            val ringAccent = if (onBreak) MaterialTheme.colorScheme.tertiary else blockAccent

            Text(
                text = activeTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isSplit) {
                FocusPhaseIndicator(
                    focusSession = focusSession,
                    highContrastEnabled = highContrastEnabled
                )
            } else {
                selectedBlock?.category?.let { category ->
                    FocusCategoryChip(
                        category = category,
                        highContrastEnabled = highContrastEnabled
                    )
                }
            }
            if (linkedBlockManuallyMissed) {
                FocusMissedBadge()
            }

            FocusTimerRing(
                timeLabel = formatFocusCountdown(remainingSeconds),
                remainingFraction = progressFraction,
                reduceMotionEnabled = reduceMotionEnabled,
                highContrastEnabled = highContrastEnabled,
                accentColor = ringAccent
            )

            if (awaitingAdvance) {
                FocusPhaseBoundaryControls(
                    focusSession = focusSession,
                    onAdvancePhase = onAdvancePhase,
                    onFinish = onFinish
                )
            } else {

            Row(
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (shouldShowFocusTabSkipAction(focusSession)) {
                    FocusSessionIconButton(
                        onClick = onSkip,
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Skip linked block session"
                    )
                }

                val playPauseContainer by animateColorAsState(
                    targetValue = if (focusSession.status == FocusExecutionStatus.RUNNING) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    animationSpec = ChronosValueAnimationFactory.stateChange(reduceMotionEnabled),
                    label = "playPauseContainer"
                )
                val playPauseIconColor by animateColorAsState(
                    targetValue = if (focusSession.status == FocusExecutionStatus.RUNNING) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    animationSpec = ChronosValueAnimationFactory.stateChange(reduceMotionEnabled),
                    label = "playPauseIcon"
                )

                FocusSessionIconButton(
                    onClick = {
                        if (focusSession.status == FocusExecutionStatus.RUNNING) onPause() else onResume()
                    },
                    icon = if (focusSession.status == FocusExecutionStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = focusTabPrimaryControlContentDescription(focusSession.status),
                    containerColor = playPauseContainer,
                    contentColor = playPauseIconColor,
                    modifier = Modifier.size(72.dp),
                    iconSize = 36.dp
                )

                FocusSessionIconButton(
                    onClick = onFinish,
                    icon = Icons.Default.Check,
                    contentDescription = "Finish session",
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onAdjustByMinutes(5) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = focusSessionAdjustmentActionLabel(5)
                        }
                ) { Text("+5m", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = { onAdjustByMinutes(-5) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = focusSessionAdjustmentActionLabel(-5)
                        }
                ) { Text("-5m", fontWeight = FontWeight.SemiBold) }
            }
            } // end awaitingAdvance else

            if (!highContrastEnabled) {
                Text(
                    text = "Protection",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
            ) {
                StatusChipItem(
                    icon = Icons.Outlined.Shield,
                    label = focusPrivacyModeLabel(privacyMode),
                    onClick = onOpenSettings,
                    highContrastEnabled = highContrastEnabled,
                    modifier = Modifier.weight(1f)
                )
                StatusChipItem(
                    icon = Icons.Outlined.NotificationsOff,
                    label = "Distractions blocked",
                    onClick = onOpenSettings,
                    highContrastEnabled = highContrastEnabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        briefing?.let {
            FocusSessionBriefingCard(
                briefing = it,
                highContrastEnabled = highContrastEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showSessionUi) {
            focusGuidance?.let { narrative ->
                FocusGuidanceCard(
                    narrative = narrative,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!showSessionUi && !showBlockReady && focusNextBlockSuggestion != null) {
            FocusNextBlockAssistCard(
                suggestion = focusNextBlockSuggestion,
                onStart = onStart,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (nextFocusBlock != null && (showSessionUi || showBlockReady)) {
            val nextAccent = categoryColor(nextFocusBlock.category)
            Text(
                text = "Upcoming",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            FocusGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(48.dp)
                            .background(nextAccent)
                    )
                    Spacer(modifier = Modifier.width(ChronosSpacing.Standard))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nextFocusBlock.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Starts ${formatFocusMinute(nextFocusBlock.startMinuteOfDay)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (
                            focusNextBlockSuggestion?.id == nextFocusBlock.id &&
                            focusNextBlockSuggestion.reason.isNotBlank()
                        ) {
                            Text(
                                text = focusNextBlockSuggestion.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        if (showSessionUi) {
            OutlinedButton(
                onClick = onEndDay,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text("End session early", fontWeight = FontWeight.SemiBold)
            }
        }

        if (showSessionUi) {
            MoodEnergyCheckInCard(
                onSave = onSaveMoodEnergyCheckIn,
                privacyMode = privacyMode,
                genAiRuntimeStatus = genAiRuntimeStatus,
                coaching = moodCheckInCoaching,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(bottomContentPadding))
    }
}

@Composable
private fun FocusGuidanceCard(
    narrative: AssistNarrative,
    modifier: Modifier = Modifier
) {
    FocusGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
        ) {
            Text(
                text = "Focus coach",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = narrative.headline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = narrative.nextStep,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = GenAiAssistCopy.assistSourceLabel(narrative.source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FocusNextBlockAssistCard(
    suggestion: FocusNextBlockSuggestion,
    onStart: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FocusGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            Text(
                text = "Suggested next focus",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${suggestion.title} · ${formatFocusMinute(suggestion.startMinuteOfDay)} · ${suggestion.durationMinutes}m",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (suggestion.reason.isNotBlank()) {
                Text(
                    text = suggestion.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = focusAssistSourceLabel(suggestion.source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = { onStart(suggestion.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start focus", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun focusAssistSourceLabel(source: FocusAssistSource): String = when (source) {
    FocusAssistSource.GEMINI_NANO -> "Gemini Nano"
    FocusAssistSource.CLOUD_GEMINI -> "Cloud Gemini"
    FocusAssistSource.LOCAL -> "Local"
}

@Composable
private fun FocusSessionBriefingCard(
    briefing: FocusTabBriefingUiState,
    highContrastEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    FocusGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
        ) {
            Text(
                text = "Session brief",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FocusBriefingRow(
                label = "Window",
                value = briefing.window,
                highContrastEnabled = highContrastEnabled
            )
            FocusBriefingRow(
                label = "Pace",
                value = briefing.pace,
                highContrastEnabled = highContrastEnabled
            )
            FocusBriefingRow(
                label = "After",
                value = briefing.nextStep.label,
                urgent = briefing.nextStep.urgent,
                highContrastEnabled = highContrastEnabled
            )
        }
    }
}

@Composable
private fun FocusBriefingRow(
    label: String,
    value: String,
    highContrastEnabled: Boolean,
    urgent: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Standard),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highContrastEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatusChipItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highContrastEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (highContrastEnabled) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = ChronosSpacing.Compact, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFocusMinute(minute: Int): String {
    val normalized = ((minute % 1440) + 1440) % 1440
    val hour = normalized / 60
    val min = normalized % 60
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return "%d:%02d %s".format(displayHour, min, suffix)
}

internal fun isFocusSessionActiveForTab(focusSession: FocusExecutionState): Boolean =
    focusSession.status == FocusExecutionStatus.RUNNING ||
        focusSession.status == FocusExecutionStatus.PAUSED

internal fun shouldShowFocusTabSkipAction(focusSession: FocusExecutionState): Boolean =
    isFocusSessionActiveForTab(focusSession) && focusSession.blockId != null

internal fun focusTabReadyBlock(
    selectedBlock: TimeBlockUiModel?,
    sessionActive: Boolean
): TimeBlockUiModel? =
    selectedBlock?.takeUnless { sessionActive || it.isAllDayCalendarImport() }

internal fun focusTabAllDayCalendarNoteBlock(
    selectedBlock: TimeBlockUiModel?,
    sessionActive: Boolean
): TimeBlockUiModel? =
    selectedBlock?.takeIf { !sessionActive && it.isAllDayCalendarImport() }

internal fun focusTabNextFocusableBlock(nextBlock: TimeBlockUiModel?): TimeBlockUiModel? =
    nextBlock?.takeUnless { it.isAllDayCalendarImport() }

internal fun focusTabDraftUiState(
    capture: String?,
    selectedReadyBlock: TimeBlockUiModel?,
    sessionActive: Boolean
): FocusTabDraftUiState? {
    if (sessionActive || selectedReadyBlock != null) return null
    val normalized = capture
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf(String::isNotBlank)
        ?: return null
    return FocusTabDraftUiState(
        title = "Focus draft ready",
        message = focusCaptureDraftMessage(normalized)
    )
}

internal fun focusCaptureDraftMessage(capture: String?): String {
    val title = focusCaptureBlockTitle(capture)
    val start = focusCaptureBlockStartMinute(capture)
    val duration = focusCaptureBlockDurationMinutes(capture)
    return buildList {
        add(title)
        start?.let { add(formatFocusMinute(it)) }
        duration?.let { add(formatDurationLabel(it)) }
    }.joinToString(" · ")
}

internal fun focusCaptureBlockTitle(capture: String?): String {
    val normalized = capture
        ?.trim()
        ?.replace(FocusCaptureWhitespaceRegex, " ")
        .orEmpty()
    if (normalized.isBlank()) return "Focus Block"

    val cleaned = normalized
        .replace(FocusCaptureIntentPrefixRegex, "")
        .replace(FocusCaptureDurationRegex, " ")
        .replace(FocusCaptureCasualDurationRegex, " ")
        .replace(FocusCaptureClockTimeRegex, " ")
        .replace(FocusCaptureNamedTimeRegex, " ")
        .replace(FocusCaptureWhitespaceRegex, " ")
        .trim()
        .replace(FocusCaptureBoundaryPunctuationRegex, "")
        .replace(FocusCaptureLeadingConnectorRegex, "")
        .replace(FocusCaptureBoundaryPunctuationRegex, "")
        .trim()

    return cleaned.ifBlank { "Focus Block" }
}

internal fun focusCaptureBlockDurationMinutes(capture: String?): Int? {
    val normalized = capture
        ?.trim()
        ?.replace(FocusCaptureWhitespaceRegex, " ")
        .orEmpty()
    var minutes = 0.0
    var hasDuration = false
    FocusCaptureDurationRegex.findAll(normalized).forEach { match ->
        val amount = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return@forEach
        val unit = match.groupValues.getOrNull(2)?.lowercase() ?: return@forEach
        minutes += if (unit.startsWith("h")) amount * 60.0 else amount
        hasDuration = true
    }
    FocusCaptureCasualDurationRegex.findAll(normalized).forEach { match ->
        minutes += if (match.value.contains("half", ignoreCase = true)) 30.0 else 60.0
        hasDuration = true
    }
    if (!hasDuration) return null
    return minutes
        .roundToInt()
        .coerceIn(5, 240)
}

internal fun focusCaptureBlockStartMinute(capture: String?): Int? {
    val normalized = capture
        ?.trim()
        ?.replace(FocusCaptureWhitespaceRegex, " ")
        .orEmpty()
    FocusCaptureNamedTimeRegex.find(normalized)?.let { match ->
        return when (match.groupValues.getOrNull(1)?.lowercase()) {
            "noon" -> 12 * 60
            "midnight" -> 0
            else -> null
        }
    }
    val match = FocusCaptureClockTimeRegex.find(normalized) ?: return null
    val meridiem = match.groupValues.getOrNull(3)?.lowercase()?.takeIf(String::isNotBlank)
    val hour = when {
        meridiem != null -> match.groupValues.getOrNull(1)?.toIntOrNull()
        else -> match.groupValues.getOrNull(4)?.toIntOrNull()
    } ?: return null
    val minute = when {
        meridiem != null -> match.groupValues.getOrNull(2)?.takeIf(String::isNotBlank)?.toIntOrNull() ?: 0
        else -> match.groupValues.getOrNull(5)?.toIntOrNull()
    } ?: return null
    if (minute !in 0..59) return null
    return if (meridiem != null) {
        if (hour !in 1..12) return null
        val normalizedHour = when {
            meridiem == "am" && hour == 12 -> 0
            meridiem == "pm" && hour != 12 -> hour + 12
            else -> hour
        }
        normalizedHour * 60 + minute
    } else {
        if (hour !in 0..23) return null
        hour * 60 + minute
    }
}

internal fun focusTabBriefingUiState(
    currentBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    focusSession: FocusExecutionState? = null,
    sessionActive: Boolean,
    remainingSeconds: Long,
    elapsedSeconds: Long
): FocusTabBriefingUiState? {
    val block = focusTabBriefingBlock(
        currentBlock = currentBlock,
        focusSession = focusSession,
        sessionActive = sessionActive
    ) ?: return null
    return FocusTabBriefingUiState(
        window = focusTabWindowLabel(block),
        pace = focusTabPaceLabel(
            sessionActive = sessionActive,
            remainingSeconds = remainingSeconds,
            elapsedSeconds = elapsedSeconds
        ),
        nextStep = focusTabNextStepUiState(block, nextBlock)
    )
}

internal fun focusTabBriefingBlock(
    currentBlock: TimeBlockUiModel?,
    focusSession: FocusExecutionState?,
    sessionActive: Boolean
): FocusTabBriefingBlock? {
    currentBlock?.takeUnless { it.isAllDayCalendarImport() }?.let { block ->
        return FocusTabBriefingBlock(
            startMinuteOfDay = block.startMinuteOfDay,
            durationMinutes = block.durationMinutes
        )
    }
    val session = focusSession ?: return null
    if (!sessionActive || session.blockTitle.isBlank() || session.plannedDurationMinutes <= 0) {
        return null
    }
    return FocusTabBriefingBlock(
        startMinuteOfDay = session.blockStartMinute,
        durationMinutes = session.plannedDurationMinutes
    )
}

internal fun focusTabNextStepUiState(
    currentBlock: FocusTabBriefingBlock,
    nextBlock: TimeBlockUiModel?
): FocusTabNextStepUiState {
    val next = focusTabNextFocusableBlock(nextBlock)
        ?: return FocusTabNextStepUiState(
            label = "No queued block after this session",
            urgent = false
        )
    val minutesUntilNext = next.startMinuteOfDay - (currentBlock.startMinuteOfDay + currentBlock.durationMinutes)
    val nextTitle = focusTabBlockTitle(next)
    return when {
        minutesUntilNext > 10 -> FocusTabNextStepUiState(
            label = "${minutesUntilNext}m reset before $nextTitle",
            urgent = false
        )
        minutesUntilNext > 0 -> FocusTabNextStepUiState(
            label = "${formatDurationLabel(minutesUntilNext)} tight handoff to $nextTitle",
            urgent = true
        )
        minutesUntilNext == 0 -> FocusTabNextStepUiState(
            label = "Immediate handoff to $nextTitle",
            urgent = true
        )
        else -> FocusTabNextStepUiState(
            label = "Overlaps $nextTitle by ${formatDurationLabel(-minutesUntilNext)}",
            urgent = true
        )
    }
}

internal fun focusTabAllDayCalendarNoteMessage(block: TimeBlockUiModel): String =
    "${focusTabBlockTitle(block)} stays visible all day without blocking your schedule, reminders, or focus suggestions."

internal fun focusTabActiveTitle(
    selectedBlockTitle: String?,
    focusSession: FocusExecutionState
): String =
    selectedBlockTitle?.takeIf { it.isNotBlank() }
        ?: focusSession.blockTitle.takeIf { it.isNotBlank() }
        ?: "Focus session"

internal fun focusTabPrimaryControlContentDescription(status: FocusExecutionStatus): String =
    when (status) {
        FocusExecutionStatus.RUNNING -> "Pause session"
        FocusExecutionStatus.PAUSED -> "Resume session"
        else -> "Start session"
    }

internal fun focusSessionAdjustmentActionLabel(minutes: Int): String = when {
    minutes > 0 -> "Extend session by $minutes minutes"
    minutes < 0 -> "Shorten session by ${-minutes} minutes"
    else -> "Keep session duration unchanged"
}

internal fun focusSessionResumedDismissActionLabel(): String =
    "Dismiss session resumed notice"

private fun focusTabWindowLabel(block: TimeBlockUiModel): String =
    "${formatFocusMinute(block.startMinuteOfDay)} - ${formatFocusMinute(block.startMinuteOfDay + block.durationMinutes)} · ${formatDurationLabel(block.durationMinutes)}"

private fun focusTabWindowLabel(block: FocusTabBriefingBlock): String =
    "${formatFocusMinute(block.startMinuteOfDay)} - ${formatFocusMinute(block.startMinuteOfDay + block.durationMinutes)} · ${formatDurationLabel(block.durationMinutes)}"

private fun focusTabPaceLabel(
    sessionActive: Boolean,
    remainingSeconds: Long,
    elapsedSeconds: Long
): String =
    if (sessionActive) {
        "${focusTabDurationLabel(elapsedSeconds)} elapsed · ${focusTabDurationLabel(remainingSeconds)} left"
    } else {
        "Ready when you are"
    }

private fun focusTabDurationLabel(seconds: Long): String {
    val totalMinutes = ((seconds.coerceAtLeast(0L) + 59L) / 60L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${totalMinutes}m"
    }
}

private fun focusTabBlockTitle(block: TimeBlockUiModel): String =
    block.title.trim().ifBlank { "This calendar note" }

private fun focusPrivacyModeLabel(mode: PrivacyMode): String = when (mode) {
    PrivacyMode.ON_DEVICE_ONLY -> "Gemini Nano"
    PrivacyMode.CLOUD_ALLOWED -> "Cloud Gemini"
    PrivacyMode.DISABLED -> "Privacy off"
}

/** A work/break split preset offered before starting a focus session. */
internal data class FocusSplitOption(
    val label: String,
    val workMinutes: Int,
    val breakMinutes: Int
)

internal val FocusSplitOptions: List<FocusSplitOption> = listOf(
    FocusSplitOption("No breaks", 0, 0),
    FocusSplitOption("25 · 5", 25, 5),
    FocusSplitOption("50 · 10", 50, 10),
    FocusSplitOption("30 · 5", 30, 5)
)

/** Human-readable summary of what a split preset produces for a given block. */
internal fun focusSplitSummary(blockDurationMinutes: Int, option: FocusSplitOption): String {
    if (option.workMinutes <= 0 || option.breakMinutes <= 0) {
        return "One ${blockDurationMinutes}m block, no breaks"
    }
    val phases = FocusPhasePlanner.plan(blockDurationMinutes, option.workMinutes, option.breakMinutes)
    if (phases.size <= 1) {
        return "Too short to split — one ${blockDurationMinutes}m block"
    }
    val focusCount = phases.count { it.kind == FocusPhaseKind.FOCUS }
    val breakCount = phases.count { it.kind == FocusPhaseKind.BREAK }
    return "$focusCount×${option.workMinutes}m focus + $breakCount×${option.breakMinutes}m break"
}

/** Label for the current phase of an active split session (e.g. "Focus 2 of 3"). */
internal fun focusPhaseLabel(focusSession: FocusExecutionState): String {
    val phase = focusSession.currentPhase ?: return "Focus"
    return when (phase.kind) {
        FocusPhaseKind.BREAK -> "Break · ${phase.durationMinutes}m"
        FocusPhaseKind.FOCUS -> {
            val focusIndex = focusSession.phases
                .take(focusSession.currentPhaseIndex + 1)
                .count { it.kind == FocusPhaseKind.FOCUS }
            val focusTotal = focusSession.phases.count { it.kind == FocusPhaseKind.FOCUS }
            "Focus $focusIndex of $focusTotal"
        }
    }
}

/** Boundary prompt copy as (heading, advance-button label). */
internal fun focusBoundaryPrompt(focusSession: FocusExecutionState): Pair<String, String> {
    val finished = focusSession.currentPhase
    val next = focusSession.nextPhase ?: return "Session complete" to "Finish"
    return when (next.kind) {
        FocusPhaseKind.BREAK -> "Time for a break" to "Start ${next.durationMinutes}m break"
        FocusPhaseKind.FOCUS -> {
            val heading = if (finished?.kind == FocusPhaseKind.BREAK) "Break's over" else "Next focus interval"
            heading to "Back to focus (${next.durationMinutes}m)"
        }
    }
}

@Composable
private fun FocusSplitSelector(
    blockDurationMinutes: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
    ) {
        Text(
            text = "Breaks",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            FocusSplitOptions.forEachIndexed { index, option ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    label = { Text(option.label) }
                )
            }
        }
        Text(
            text = focusSplitSummary(blockDurationMinutes, FocusSplitOptions[selectedIndex]),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FocusPhaseIndicator(
    focusSession: FocusExecutionState,
    highContrastEnabled: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
    ) {
        Text(
            text = focusPhaseLabel(focusSession),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (focusSession.isOnBreak) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            focusSession.phases.forEachIndexed { index, phase ->
                val active = index == focusSession.currentPhaseIndex
                val done = index < focusSession.currentPhaseIndex
                val base = if (phase.kind == FocusPhaseKind.BREAK) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
                val dotColor = when {
                    active -> base
                    done -> base.copy(alpha = if (highContrastEnabled) 0.7f else 0.5f)
                    else -> base.copy(alpha = if (highContrastEnabled) 0.4f else 0.2f)
                }
                Box(
                    modifier = Modifier
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
private fun FocusPhaseBoundaryControls(
    focusSession: FocusExecutionState,
    onAdvancePhase: () -> Unit,
    onFinish: () -> Unit
) {
    val next = focusSession.nextPhase
    val (heading, advanceLabel) = focusBoundaryPrompt(focusSession)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onAdvancePhase,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (next?.kind == FocusPhaseKind.BREAK) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(advanceLabel, fontWeight = FontWeight.SemiBold)
        }
        TextButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("End session")
        }
    }
}
