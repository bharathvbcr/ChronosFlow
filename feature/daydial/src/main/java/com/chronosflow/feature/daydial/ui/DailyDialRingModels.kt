package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosflow.core.domain.planner.DialRing
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.core.ui.theme.categoryColor
import com.chronosflow.feature.daydial.DailyReview
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import java.util.Locale

private const val DAY_IN_MINUTES = 1440
private const val RING_GUIDE_DESCRIPTION =
    "Outer is fixed time, middle is your plan, inner is action-oriented routines. Tap any lane to add in context."

internal data class DailyDialCenterState(
    val title: String,
    val timeWindow: String,
    val status: String,
    val supporting: String,
    val actionHint: String,
    val categoryLabel: String,
    val accentColor: Color
)

internal data class DailyDialLegendItem(
    val ring: DialRing,
    val title: String,
    val description: String,
    val quickCreateLabel: String,
    val accentColor: Color
)

internal data class DailyDialRingLegendState(
    val title: String,
    val description: String?,
    val actionLabel: String,
    val isExpanded: Boolean
)

internal fun buildDailyDialCenterState(
    currentMinute: Int,
    selectedBlock: TimeBlockUiModel?,
    activeBlock: TimeBlockUiModel?,
    nextBlock: TimeBlockUiModel?,
    isViewingToday: Boolean = true
): DailyDialCenterState {
    val focusedBlock = selectedBlock ?: activeBlock
    if (focusedBlock != null) {
        val startMinute = focusedBlock.startMinuteOfDay
        val endMinute = startMinute + focusedBlock.durationMinutes
        val isActive = isMinuteWithinBlock(currentMinute, focusedBlock)
        val minutesUntilStart = minutesUntil(currentMinute, startMinute)
        val categoryLabel = categoryDisplayLabel(focusedBlock.category)
        val status = when {
            selectedBlock != null && !isActive && isUpcomingBlock(focusedBlock, currentMinute) ->
                "Starts in $minutesUntilStart min"
            selectedBlock != null && !isActive ->
                "Ended ${minutesSince(endMinute, currentMinute)} min ago"
            isActive -> "Ends in ${minutesUntil(currentMinute, endMinute)} min"
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
            accentColor = categoryColor(focusedBlock.category)
        )
    }

    val nextStart = nextBlock?.startMinuteOfDay
    val freeMinutes = nextStart?.let { minutesUntil(currentMinute, it) } ?: 0
    return DailyDialCenterState(
        title = "Open time",
        timeWindow = formatDialClockMinute(currentMinute),
        status = if (nextStart != null) "Free for $freeMinutes min" else "Day is open",
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
        categoryLabel = "Open window",
        accentColor = Color(0xFF4DB6AC)
    )
}

internal fun dailyDialLegendItems(): List<DailyDialLegendItem> {
    return listOf(
        DailyDialLegendItem(
            ring = DialRing.OUTER,
            title = "Calendar",
            description = "Imported events and fixed holds",
            quickCreateLabel = "Calendar hold",
            accentColor = Color(0xFF8E99F3)
        ),
        DailyDialLegendItem(
            ring = DialRing.MIDDLE,
            title = "Plan",
            description = "Flexible blocks you can shape",
            quickCreateLabel = "Focus Block",
            accentColor = Color(0xFF4CAF50)
        ),
        DailyDialLegendItem(
            ring = DialRing.INNER,
            title = "Actions",
            description = "Tasks habits and medication",
            quickCreateLabel = "Routine checkpoint",
            accentColor = Color(0xFFFF9800)
        )
    )
}

internal fun buildDailyDialRingLegendState(isExpanded: Boolean): DailyDialRingLegendState {
    return DailyDialRingLegendState(
        title = "Ring guide",
        description = RING_GUIDE_DESCRIPTION.takeIf { isExpanded },
        actionLabel = if (isExpanded) "Minimize" else "Show",
        isExpanded = isExpanded
    )
}

@Composable
internal fun DailyDialCenterOverlay(
    state: DailyDialCenterState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 168.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = state.accentColor.copy(alpha = 0.16f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
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
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.timeWindow,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = state.status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.actionHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DailyDialRingLegend(
    review: DailyReview,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = dailyDialLegendItems()
    val state = buildDailyDialRingLegendState(isExpanded = isExpanded)
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = { onExpandedChange(!state.isExpanded) },
                    modifier = Modifier.semantics {
                        contentDescription = dailyDialRingLegendToggleActionLabel(state.isExpanded)
                    }
                ) {
                    Text(state.actionLabel)
                }
            }
            state.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.isExpanded) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                ) {
                    items.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(item.accentColor, CircleShape)
                                            .padding(5.dp)
                                    )
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Long-press: ${item.quickCreateLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = item.accentColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                ) {
                    DailyDialReviewBadge(
                        label = "Planned",
                        value = formatReviewMinutes(review.plannedMinutes),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    DailyDialReviewBadge(
                        label = "Actual",
                        value = formatReviewMinutes(review.actualMinutes),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    DailyDialReviewBadge(
                        label = "Missed",
                        value = formatReviewMinutes(review.missedMinutes),
                        color = if (review.missedMinutes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyDialReviewBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
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
    return ((endMinute - currentMinute + DAY_IN_MINUTES) % DAY_IN_MINUTES)
}

internal fun dailyDialRingLegendToggleActionLabel(isExpanded: Boolean): String =
    if (isExpanded) "Minimize ring guide details" else "Show ring guide details"

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

private fun formatReviewMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${remainingMinutes}m"
}
