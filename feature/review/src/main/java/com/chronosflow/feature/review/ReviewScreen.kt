package com.chronosflow.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronosflow.core.ai.AssistNarrative
import com.chronosflow.core.ai.genai.GenAiAssistCopy
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.ReviewInsight
import com.chronosflow.core.domain.model.ReviewInsightSeverity
import com.chronosflow.core.ui.components.ChronosEmptyState
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosMetricTile
import com.chronosflow.core.ui.components.ChronosScreenScaffold
import com.chronosflow.core.ui.components.ChronosSectionHeader
import com.chronosflow.core.ui.components.ChronosTooltipIconButton
import com.chronosflow.core.ui.shell.LocalChronosShellBottomInset
import com.chronosflow.core.ui.theme.ChronosSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reviewDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val weeklyRollup by viewModel.weeklyRollup.collectAsStateWithLifecycle()
    val coachNarrative by viewModel.coachNarrative.collectAsStateWithLifecycle()
    val bottomInset = LocalChronosShellBottomInset.current

    ChronosScreenScaffold(
        title = "Daily Review",
        onBack = onBack,
        actions = {
            ChronosTooltipIconButton(
                onClick = viewModel::jumpToToday,
                tooltip = "Jump to today",
                contentDescription = "Jump to today"
            ) {
                Icon(Icons.Filled.Today, contentDescription = null)
            }
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ChronosSpacing.Standard,
                end = ChronosSpacing.Standard,
                top = ChronosSpacing.Standard,
                bottom = ChronosSpacing.Standard + bottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
        ) {
            item {
                ReviewDateNav(
                    selectedDate = selectedDate,
                    onPrevious = viewModel::previousDay,
                    onNext = viewModel::nextDay
                )
            }

            val summary = review
            if (summary == null || summary.plannedMinutes <= 0) {
                item {
                    ChronosEmptyState(
                        title = "Nothing to review yet",
                        message = "Plan blocks for this day and run focus sessions — " +
                            "the review fills in as actual time is tracked."
                    )
                }
            } else {
                item { ReviewSummaryMetrics(summary) }
                item { ReviewExecutionRow(summary) }
                coachNarrative?.let { narrative ->
                    item { ReviewCoachCard(narrative) }
                }
                if (summary.insights.isNotEmpty()) {
                    item {
                        ChronosSectionHeader(
                            title = "Insights",
                            subtitle = "${summary.insights.size} for this day"
                        )
                    }
                    items(summary.insights, key = { it.id }) { insight ->
                        ReviewInsightCard(insight, modifier = Modifier.animateItem())
                    }
                }
            }

            weeklyRollup?.let { rollup ->
                item {
                    ChronosSectionHeader(
                        title = "This week",
                        subtitle = "${rollup.daysWithPlans} planned day${if (rollup.daysWithPlans == 1) "" else "s"}"
                    )
                }
                item { WeeklyRollupCard(rollup) }
            }
        }
    }
}

@Composable
private fun WeeklyRollupCard(rollup: WeeklyReviewRollup) {
    ChronosListCard {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${rollup.executionPercent}% executed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${formatMinutes(rollup.actualMinutes)} of ${formatMinutes(rollup.plannedMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${rollup.completedBlockCount} blocks done · " +
                    "${rollup.missedBlockCount} missed · " +
                    "${formatMinutes(rollup.missedMinutes)} unaccounted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReviewCoachCard(narrative: AssistNarrative) {
    ChronosListCard {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
            Text(
                text = "Coach summary",
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
                style = MaterialTheme.typography.bodyMedium,
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
private fun ReviewDateNav(
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ChronosTooltipIconButton(
            onClick = onPrevious,
            tooltip = "Previous day",
            contentDescription = "Previous day"
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null)
        }
        Text(
            text = selectedDate.format(reviewDateFormatter),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        ChronosTooltipIconButton(
            onClick = onNext,
            tooltip = "Next day",
            contentDescription = "Next day"
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun ReviewSummaryMetrics(summary: DailyReviewSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
            ChronosMetricTile(
                label = "Planned",
                value = formatMinutes(summary.plannedMinutes),
                modifier = Modifier.weight(1f)
            )
            ChronosMetricTile(
                label = "Actual",
                value = formatMinutes(summary.actualMinutes),
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
            ChronosMetricTile(
                label = "Missed",
                value = formatMinutes(summary.missedMinutes),
                modifier = Modifier.weight(1f),
                accent = if (summary.missedMinutes > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            ChronosMetricTile(
                label = "Drift",
                value = formatDrift(summary.driftMinutes),
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun ReviewExecutionRow(summary: DailyReviewSummary) {
    val executionPercent = if (summary.plannedMinutes > 0) {
        (summary.actualMinutes * 100 / summary.plannedMinutes).coerceIn(0, 100)
    } else {
        0
    }
    ChronosListCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Execution",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$executionPercent% of plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${summary.completedBlockCount} done · ${summary.missedBlockCount} missed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReviewInsightCard(insight: ReviewInsight, modifier: Modifier = Modifier) {
    val (icon, tint) = insightVisuals(insight.severity)
    ChronosListCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = insight.severity.name, tint = tint)
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = insight.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (insight.assistSource != null) {
                    Text(
                        text = "Model-assisted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun insightVisuals(
    severity: ReviewInsightSeverity
): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> = when (severity) {
    ReviewInsightSeverity.CRITICAL -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
    ReviewInsightSeverity.WARNING -> Icons.Outlined.WarningAmber to MaterialTheme.colorScheme.tertiary
    ReviewInsightSeverity.INFO -> Icons.Outlined.Info to MaterialTheme.colorScheme.primary
}

internal fun formatMinutes(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val rest = safe % 60
    return when {
        hours == 0 -> "${rest}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

internal fun formatDrift(driftMinutes: Int): String = when {
    driftMinutes > 0 -> "+${formatMinutes(driftMinutes)}"
    driftMinutes < 0 -> "-${formatMinutes(-driftMinutes)}"
    else -> "On plan"
}
