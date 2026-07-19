package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.domain.planner.DialRing
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.formatDurationLabel
import com.ChronosFlow.VBCR.core.ui.theme.ChronosColors
import com.ChronosFlow.VBCR.core.ui.theme.ChronosGlassTokens
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import com.ChronosFlow.VBCR.core.ui.theme.LocalFocusAwareColorState
import com.ChronosFlow.VBCR.core.ui.theme.categoryColor
import com.ChronosFlow.VBCR.feature.daydial.DailyReview
import com.ChronosFlow.VBCR.feature.daydial.TimeRangeUi
import com.ChronosFlow.VBCR.feature.daydial.model.TimeBlockUiModel
import java.util.Locale

/** Cap hub width to the dial's inner clear zone (~55% of diameter). */
internal fun dailyDialHubMaxWidth(dialDiameter: Dp): Dp = dialDiameter * 0.55f

/**
 * When vertical space or font scale is tight, demote category pill and progress before
 * truncating essential status (which always keeps two lines).
 */
internal fun dailyDialHubShowsSecondaryLines(fontScale: Float, availableHeight: Dp): Boolean =
    fontScale < 1.3f && availableHeight >= 88.dp

internal fun dailyDialHubShowsCategoryPill(fontScale: Float, availableHeight: Dp): Boolean =
    fontScale < 1.3f && availableHeight >= 72.dp

private const val DAY_IN_MINUTES = 1440
private const val RING_GUIDE_DESCRIPTION =
    "Three lanes: Calendar holds fixed events, Plan is your flexible blocks, Actions are tasks, habits, and meds. Tap a lane to add there. The indigo band marks your sleep window."

internal data class DailyDialCenterState(
    val title: String,
    val timeWindow: String,
    val status: String,
    val supporting: String,
    val actionHint: String,
    val categoryLabel: String,
    val accentColor: Color,
    /** At-a-glance day progress, e.g. "3 of 7 done · 2h 10m free"; null when a block is selected. */
    val progressLine: String? = null
)

internal data class DailyDialLegendItem(
    val ring: DialRing,
    val title: String,
    val description: String,
    val quickCreateLabel: String,
    val accentColor: Color
)

internal fun buildDailyDialCenterState(
    currentMinute: Int,
    selectedBlock: TimeBlockUiModel?,
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    isViewingToday: Boolean = true,
    review: DailyReview? = null,
    totalBlocks: Int = 0,
    freeTime: List<TimeRangeUi> = emptyList()
): DailyDialCenterState {
    val progressLine = if (selectedBlock == null && isViewingToday && review != null && totalBlocks > 0) {
        val freeRemaining = remainingFreeMinutes(freeTime, currentMinute)
        buildString {
            append("${review.completedBlocks} of $totalBlocks done")
            if (freeRemaining > 0) {
                append(" · ${formatReviewMinutes(freeRemaining)} free")
            }
        }
    } else {
        null
    }
    val focusedBlock = selectedBlock ?: activeBlock
    if (focusedBlock != null) {
        val startMinute = focusedBlock.startMinuteOfDay
        val endMinute = startMinute + focusedBlock.durationMinutes
        val isActive = isMinuteWithinBlock(currentMinute, focusedBlock)
        val minutesUntilStart = minutesUntil(currentMinute, startMinute)
        val categoryLabel = categoryDisplayLabel(focusedBlock.category)
        val status = when {
            selectedBlock != null && !isActive && isUpcomingBlock(focusedBlock, currentMinute) ->
                "Starts in ${formatReviewMinutes(minutesUntilStart)}"
            selectedBlock != null && !isActive ->
                "Ended ${formatReviewMinutes(minutesSince(endMinute, currentMinute))} ago"
            isActive -> "Ends in ${formatReviewMinutes(minutesUntil(currentMinute, endMinute))}"
            else -> "Scheduled today"
        }
        val supporting = when {
            selectedBlock != null -> "Selected ${categoryLabel.lowercase(Locale.getDefault())} block"
            nextBlock != null && nextBlock.id != focusedBlock.id -> {
                "Next: ${nextBlock.title} at ${formatDialClockMinute(nextBlock.startMinuteOfDay)}"
            }
            else -> "Current focus lane"
        }
        return DailyDialCenterState(
            title = focusedBlock.title,
            timeWindow = "${formatDialClockMinute(startMinute)} - ${formatDialClockMinute(endMinute)}",
            status = status,
            supporting = supporting,
            actionHint = if (selectedBlock != null) {
                "Drag to move. Handles resize."
            } else {
                "Tap block for details"
            },
            categoryLabel = categoryLabel,
            accentColor = categoryColor(focusedBlock.category),
            progressLine = progressLine
        )
    }

    val nextStart = nextBlock?.startMinuteOfDay
    val freeMinutes = nextStart?.let { minutesUntil(currentMinute, it) } ?: 0
    // Open time leads with the clock, not the words "Open time" — the action strip and the
    // "Now & next" card below already carry that label, so repeating it in the hub three times
    // over read as clutter. The clock + "Free for …" status is the at-a-glance answer here.
    return DailyDialCenterState(
        title = formatDialClockMinute(currentMinute),
        timeWindow = "",
        status = if (nextStart != null) "Free for ${formatReviewMinutes(freeMinutes)}" else "Day is open",
        supporting = if (nextBlock != null) {
            "Next: ${nextBlock.title} at ${formatDialClockMinute(nextBlock.startMinuteOfDay)}"
        } else {
            "No more scheduled blocks"
        },
        actionHint = if (isViewingToday) {
            "Tap a ring to add a block"
        } else {
            "Tap a ring to schedule this day"
        },
        // No category pill for open time — the "Open time" title already says it, so a
        // duplicate "Open window" chip just adds noise. Blank → the hub skips the pill.
        categoryLabel = "",
        accentColor = ChronosColors.DialRingTeal,
        progressLine = progressLine
    )
}

internal fun remainingFreeMinutes(freeTime: List<TimeRangeUi>, currentMinute: Int): Int =
    freeTime.sumOf { segment ->
        (segment.endMinute - maxOf(segment.startMinute, currentMinute)).coerceAtLeast(0)
    }

internal fun dailyDialLegendItems(): List<DailyDialLegendItem> {
    return listOf(
        DailyDialLegendItem(
            ring = DialRing.OUTER,
            title = "Calendar",
            description = "Imported events and fixed holds",
            quickCreateLabel = "Calendar hold",
            accentColor = ChronosColors.DialRingIndigo
        ),
        DailyDialLegendItem(
            ring = DialRing.MIDDLE,
            title = "Plan",
            description = "Flexible blocks you can shape",
            quickCreateLabel = "Focus Block",
            accentColor = ChronosColors.DialRingGreen
        ),
        DailyDialLegendItem(
            ring = DialRing.INNER,
            title = "Actions",
            description = "Tasks habits and medication",
            quickCreateLabel = "Routine checkpoint",
            accentColor = ChronosColors.DialRingAmber
        )
    )
}

@Composable
internal fun DailyDialCenterOverlay(
    state: DailyDialCenterState,
    modifier: Modifier = Modifier,
    dialDiameter: Dp = 300.dp
) {
    // Kept deliberately compact: width tracks the dial's inner clear zone so large fonts and
    // small heroes stay readable. Status is essential (2 lines, no ellipsis); category pill and
    // progress demote first when vertical space is tight.
    val isHighContrast = LocalFocusAwareColorState.current.isHighContrast
    val fontScale = LocalDensity.current.fontScale
    val maxHubWidth = dailyDialHubMaxWidth(dialDiameter)
    val hubSurface = if (isHighContrast) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = ChronosGlassTokens.BaseOpacity)
    }
    val liveAnnouncement = buildString {
        append(state.title)
        if (state.status.isNotBlank()) {
            append(". ")
            append(state.status)
        }
    }
    Surface(
        modifier = modifier
            .widthIn(max = maxHubWidth)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = liveAnnouncement
            },
        shape = MaterialTheme.shapes.large,
        color = hubSurface,
        tonalElevation = ChronosSpacing.Micro,
        shadowElevation = ChronosSpacing.Small
    ) {
        BoxWithConstraints {
            val showCategory = state.categoryLabel.isNotBlank() &&
                dailyDialHubShowsCategoryPill(fontScale, maxHeight)
            val showProgress = state.progressLine != null &&
                dailyDialHubShowsSecondaryLines(fontScale, maxHeight)
            Column(
                modifier = Modifier
                    .heightIn(max = dialDiameter * 0.5f)
                    .padding(
                        horizontal = ChronosSpacing.Compact,
                        vertical = ChronosSpacing.Small
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
            ) {
                // The category pill names a block's lane (Work, Meeting, …). Open time has no
                // category, so it leaves this blank and the pill is skipped — otherwise it just
                // echoed the "Open time" title below it.
                if (showCategory) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = state.accentColor.copy(alpha = 0.16f),
                                shape = CircleShape
                            )
                            .padding(
                                horizontal = ChronosSpacing.Small,
                                vertical = ChronosSpacing.Micro / 2
                            )
                    ) {
                        Text(
                            text = state.categoryLabel,
                            color = state.accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.timeWindow.isNotBlank()) {
                    Text(
                        text = state.timeWindow,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    lineHeight = MaterialTheme.typography.labelLarge.lineHeight
                )
                // Idle day-progress is the one extra line worth keeping — it isn't shown anywhere
                // else on the hub and reads at a glance. Demoted under large font / short hub.
                if (showProgress) {
                    Text(
                        text = state.progressLine.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * One-line legend naming the three dial rings. Replaces the expandable
 * "Ring guide" card; ring meaning is also described for accessibility.
 */
@Composable
internal fun DailyDialInlineLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = RING_GUIDE_DESCRIPTION },
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Standard, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dailyDialLegendItems().forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(item.accentColor, CircleShape)
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** One-time dismissible explanation of the rings, shown until acknowledged. */
@Composable
internal fun DailyDialLegendTip(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            Text(
                text = RING_GUIDE_DESCRIPTION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChronosTextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = "Dismiss ring guide tip" }
            ) {
                Text("Got it")
            }
        }
    }
}

private fun isMinuteWithinBlock(currentMinute: Int, block: TimeBlockUiModel): Boolean {
    val endMinute = block.startMinuteOfDay + block.durationMinutes
    return if (endMinute <= DAY_IN_MINUTES) {
        currentMinute in block.startMinuteOfDay until endMinute
    } else {
        currentMinute >= block.startMinuteOfDay || currentMinute < endMinute % DAY_IN_MINUTES
    }
}

private fun minutesUntil(currentMinute: Int, targetMinute: Int): Int {
    return ((targetMinute - currentMinute + DAY_IN_MINUTES) % DAY_IN_MINUTES)
}

internal fun remainingMinutesInBlock(block: TimeBlockUiModel, currentMinute: Int): Int {
    val endMinute = block.startMinuteOfDay + block.durationMinutes
    return if (endMinute <= DAY_IN_MINUTES) {
        (endMinute - currentMinute).coerceAtLeast(0)
    } else {
        ((endMinute - currentMinute + DAY_IN_MINUTES) % DAY_IN_MINUTES)
    }
}

private fun minutesSince(targetMinute: Int, currentMinute: Int): Int {
    return ((currentMinute - targetMinute + DAY_IN_MINUTES) % DAY_IN_MINUTES)
}

private fun isUpcomingBlock(block: TimeBlockUiModel, currentMinute: Int): Boolean {
    val endMinute = (block.startMinuteOfDay + block.durationMinutes) % DAY_IN_MINUTES
    return if (block.startMinuteOfDay + block.durationMinutes <= DAY_IN_MINUTES) {
        currentMinute < block.startMinuteOfDay
    } else {
        currentMinute < block.startMinuteOfDay && currentMinute >= endMinute
    }
}

private fun categoryDisplayLabel(category: String): String {
    return category.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun formatDialClockMinute(minute: Int): String {
    val normalized = ((minute % DAY_IN_MINUTES) + DAY_IN_MINUTES) % DAY_IN_MINUTES
    val hour = normalized / 60
    val minutes = normalized % 60
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val value = hour % 12) {
        0 -> 12
        else -> value
    }
    return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minutes, suffix)
}

private fun formatReviewMinutes(minutes: Int): String =
    formatDurationLabel(minutes.coerceAtLeast(0))
