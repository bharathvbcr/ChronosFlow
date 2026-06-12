package com.chronosflow.feature.daydial.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ai.InsightRecommendation
import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.GenAiAssistCopy
import com.chronosflow.core.ai.genai.GenAiAssistUiSnapshot
import com.chronosflow.core.domain.model.ReviewInsight
import com.chronosflow.core.domain.model.ReviewInsightSeverity
import com.chronosflow.core.ui.components.ChronosEmptyState
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosMetricTile
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.components.GenAiAssistBanner
import com.chronosflow.core.ui.motion.ChronosValueAnimationFactory
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.feature.daydial.DailyReview
import com.chronosflow.feature.daydial.TimeBlockUiModel
import com.chronosflow.feature.daydial.model.DayDialTab
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val NO_PLAN_SUMMARY_ACTION_LABEL = "Create plan"
private const val NO_PLAN_SUMMARY_MESSAGE =
    "Create one now to unlock completion, drift, and missed-block analysis."
private const val NO_PLAN_SUMMARY_HEADER = "No plan set for today"
private const val NO_PLAN_CHART_MESSAGE =
    "Actual time from today is shown. Planned-vs-actual unlocks after a plan is created."
private const val NO_PLAN_ACTUAL_NONE_MESSAGE = "No logged time yet"
private const val EXECUTION_SCORE_NO_PLAN_MESSAGE = "No plan is active for today."
private const val EXECUTION_SCORE_AHEAD_MESSAGE = "You are ahead of plan."
private const val EXECUTION_SCORE_ON_TRACK_MESSAGE = "Execution is on track."
private const val EXECUTION_SCORE_DRIFT_MESSAGE = "Execution drifted from plan."
private const val EXECUTION_SCORE_BEHIND_MESSAGE = "Execution is behind."
private const val EXECUTION_SCORE_NO_PLAN_ACTION = "Create a plan to unlock completion and drift insights."
private const val EXECUTION_SCORE_AHEAD_ACTION = "Keep your current cadence and preserve block quality."
private const val EXECUTION_SCORE_ON_TRACK_ACTION =
    "Tighten 10-minute estimates on one block to improve forecasting."
private const val EXECUTION_SCORE_DRIFT_ACTION =
    "Cap your next work block to reduce variance and recover the plan."
private const val EXECUTION_SCORE_BEHIND_ACTION =
    "Prioritize your top two tasks and defer low-value blocks."

private const val METRIC_NOT_APPLICABLE = "N/A"

@Composable
internal fun InsightsTab(
    review: DailyReview,
    timeBlocks: List<TimeBlockUiModel>,
    missedCount: Int,
    reviewInsights: List<ReviewInsight>,
    recommendations: List<InsightRecommendation>,
    assistSnapshot: GenAiAssistUiSnapshot?,
    isRefreshing: Boolean,
    onRefreshRecommendations: () -> Unit,
    onApplyRecommendation: (InsightRecommendation) -> Unit,
    onOpenFullReview: () -> Unit = {},
    contentBottomPadding: Dp = 0.dp,
    onCreatePlan: () -> Unit = {},
    trendRangeDays: Int = 14,
    trends: com.chronosflow.feature.daydial.delegate.CompanionTrendSections =
        com.chronosflow.feature.daydial.delegate.CompanionTrendSections(),
    journalEntry: com.chronosflow.core.domain.model.JournalEntry? = null,
    sleepTrack: com.chronosflow.core.domain.model.SleepTrack? = null,
    onTrendRangeSelected: (Int) -> Unit = {},
    onOpenJournal: () -> Unit = {},
    onOpenSleepLog: () -> Unit = {}
) {
    val periodPlanned = review.plannedMinutes
    val periodActual = review.actualMinutes.coerceAtLeast(0)
    val periodMissed = review.missedMinutes.coerceAtLeast(0)
    val periodDrift = periodActual - periodPlanned
    val shouldShowNoPlan = hasNoPlannedDayMinutes(periodPlanned)
    val isPlanSet = !shouldShowNoPlan
    val completion = plannedCompletionPercent(periodPlanned, periodActual)
    val plannedMinutesLabel = planMetricLabel(isPlanSet, formatInsightMinutes(periodPlanned))
    val missedMinutesLabel = planMetricLabel(isPlanSet, formatInsightMinutes(periodMissed))
    val driftLabel = planMetricLabel(isPlanSet, formatDriftMinutes(periodDrift))
    val missedAccent = if (isPlanSet) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val driftAccentColor = if (isPlanSet) driftAccent(periodDrift) else MaterialTheme.colorScheme.onSurfaceVariant
    val actualMinutesLabel = noPlanAwareActualMetricLabel(periodActual, isPlanSet)
    val actualAccent = noPlanAwareActualMetricColor(periodActual, isPlanSet)
    val executionNarrative = executionScoreNarrative(completion, isPlanSet)
    val executionAction = executionScoreAction(completion, isPlanSet)
    val missedSummary = missedBlocksSummaryText(isPlanSet, missedCount)
    val categoryRows = insightCategoryBreakdownRows(timeBlocks)
    val orderedInsights = reviewInsights.sortedWith(
        compareBy({ insightSeveritySort(it) }, { it.type.name })
    )
    val pagePadding = ChronosSpacing.Standard
    val bottomContentPadding = dayDialScrollableBottomPadding(
        contentBottomPadding = contentBottomPadding,
        pageBottomPadding = pagePadding
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = pagePadding),
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Standard)
    ) {
        Spacer(Modifier.height(pagePadding))
        DayDialPageHeader(
            title = DayDialTab.INSIGHTS.label,
            subtitle = dayDialPrimaryPageSubtitle(DayDialTab.INSIGHTS),
            icon = DayDialTab.INSIGHTS.icon
        )

        ChronosEmptyState(
            title = "Scope",
            message = "This tab shows selected-day execution data only. Weekly and monthly rollups are coming in a future update.",
            modifier = Modifier.fillMaxWidth()
        )

        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                Text(
                    text = "Execution score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChronosMetricTile(
                        "Completion",
                        formatCompletionText(completion, isPlanSet),
                        modifier = Modifier.weight(1f),
                        accent = scoreColor(completion, isPlanSet)
                    )
                    ChronosMetricTile(
                        "Status",
                        completionStatusText(completion, isPlanSet),
                        Modifier.weight(1f),
                        accent = scoreColor(completion, isPlanSet)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                ) {
                    ChronosMetricTile("Planned", plannedMinutesLabel, Modifier.weight(1f))
                    ChronosMetricTile(
                        "Actual",
                        actualMinutesLabel,
                        Modifier.weight(1f),
                        accent = actualAccent
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                ) {
                    ChronosMetricTile(
                        "Missed",
                        missedMinutesLabel,
                        Modifier.weight(1f),
                        accent = missedAccent
                    )
                    ChronosMetricTile(
                        "Drift",
                        driftLabel,
                        Modifier.weight(1f),
                        accent = driftAccentColor
                    )
                }
                ExecutionSummaryRow(
                    isPlanSet = isPlanSet,
                    missedSummary = missedSummary,
                    onCreatePlan = onCreatePlan
                )
                val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
                AnimatedVisibility(
                    visible = isPlanSet,
                    enter = if (reduceMotion) fadeIn() else expandVertically() + fadeIn(),
                    exit = if (reduceMotion) fadeOut() else shrinkVertically() + fadeOut()
                ) {
                    val animatedCompletion by animateFloatAsState(
                        targetValue = completion.toFloat().coerceIn(0f, 100f) / 100f,
                        animationSpec = ChronosValueAnimationFactory.stateChange(reduceMotion),
                        label = "executionCompletion"
                    )
                    LinearProgressIndicator(
                        progress = { animatedCompletion },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = scoreColor(completion, isPlanSet),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                ) {
                    FilledTonalButton(
                        onClick = onOpenFullReview,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open daily review", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onRefreshRecommendations,
                        enabled = !isRefreshing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        val refreshFade = ChronosValueAnimationFactory.stateChange<Float>(reduceMotion)
                        AnimatedContent(
                            targetState = isRefreshing,
                            transitionSpec = { fadeIn(refreshFade) togetherWith fadeOut(refreshFade) },
                            label = "refreshLeadingIndicator"
                        ) { refreshing ->
                            if (refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isRefreshing) "Refreshing…" else "Refresh recommendations",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = executionNarrative,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPlanSet) MaterialTheme.colorScheme.onSurfaceVariant else
                        MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Suggestion: $executionAction",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                Text("Planned vs actual", style = MaterialTheme.typography.labelLarge)
                    PlannedVsActualChart(
                        planned = periodPlanned,
                        actual = periodActual,
                        noPlan = shouldShowNoPlan,
                        onCreatePlan = onCreatePlan
                    )
                Text(
                    text = "Category breakdown",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = ChronosSpacing.Small)
                )
                if (categoryRows.isNotEmpty()) {
                    Text(
                        text = "Legend: ★ is your top focus category",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FocusConcentrationLegend()
                }
                if (categoryRows.isEmpty()) {
                    Text(
                        "No blocks tracked yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val topRow = categoryRows.first()
                    val concentrationProfile = focusConcentrationProfile(topRow.share, topRow.category, categoryRows.size)
                    val topCategoryMix = focusTopCategoryMix(categoryRows)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
                    ) {
                        ChronosMetricTile(
                            "Top focus",
                            topRow.category,
                            modifier = Modifier.weight(1f),
                            accent = MaterialTheme.colorScheme.secondary
                        )
                        ChronosMetricTile(
                            "Share",
                            percentText(topRow.share),
                            modifier = Modifier.weight(1f),
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Concentration: ${focusConcentrationLabel(topRow.share)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = concentrationProfile.color
                    )
                    Text(
                        text = "Dominant share: ${percentText(topRow.share)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (concentrationProfile.tip != null) {
                        Text(
                            text = concentrationProfile.tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (concentrationProfile.suggestion != null) {
                        Text(
                            text = "Suggested next action: ${concentrationProfile.suggestion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (categoryRows.size > 1) {
                        Text(
                            text = "Top mix: $topCategoryMix",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    categoryRows.forEachIndexed { index, row ->
                        val rowColor = if (index == 0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    if (index == 0) "★ ${row.category}" else row.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (index == 0) rowColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${formatInsightMinutes(row.minutes)} • ${percentText(row.share)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (index == 0) rowColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { row.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = rowColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (orderedInsights.isNotEmpty()) {
            ChronosSectionTitle(
                title = "Review insights",
                subtitle = "Priority: critical -> warning -> info"
            )
            orderedInsights.forEach { insight ->
                ReviewInsightRow(insight = insight)
            }
        } else {
            ChronosSectionTitle(title = "Review insights")
            ChronosEmptyState(
                title = "No review insights yet",
                message = "Complete your day and run the end-of-day review to get automatically generated findings.",
                modifier = Modifier.fillMaxWidth()
            )
        }

        assistSnapshot?.let { snapshot ->
            GenAiAssistBanner(
                title = snapshot.bannerTitle,
                message = snapshot.bannerMessage,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ChronosSectionTitle(title = "AI recommendations")
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                if (recommendations.isEmpty()) {
                    Text(
                        text = "Refresh to generate schedule recommendations from your review data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    recommendations.forEach { recommendation ->
                        RecommendationRow(
                            text = recommendation.text,
                            sourceLabel = GenAiAssistCopy.assistSourceLabel(recommendation.source),
                            onApply = { onApplyRecommendation(recommendation) }
                        )
                    }
                }
            }
        }

        InsightsTrendSections(
            trendRangeDays = trendRangeDays,
            trends = trends,
            journalEntry = journalEntry,
            sleepTrack = sleepTrack,
            onTrendRangeSelected = onTrendRangeSelected,
            onOpenJournal = onOpenJournal,
            onOpenSleepLog = onOpenSleepLog
        )
        Spacer(Modifier.height(bottomContentPadding))
    }
}

@Composable
private fun ReviewInsightRow(insight: ReviewInsight) {
    val severityColor = when (insight.severity) {
        ReviewInsightSeverity.INFO -> MaterialTheme.colorScheme.primary
        ReviewInsightSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        ReviewInsightSeverity.CRITICAL -> MaterialTheme.colorScheme.error
    }
    val source = insight.assistSource?.let { name ->
        runCatching { AssistGenAiSource.valueOf(name) }.getOrNull()
    }
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(insight.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                insight.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = insight.type.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor
                )
                source?.takeIf { it != AssistGenAiSource.LOCAL }?.let { assistSource ->
                    Text(
                        text = GenAiAssistCopy.assistSourceLabel(assistSource),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = when (insight.severity) {
                        ReviewInsightSeverity.INFO -> Icons.Default.Visibility
                        ReviewInsightSeverity.WARNING -> Icons.Default.Analytics
                        ReviewInsightSeverity.CRITICAL -> Icons.Default.Schedule
                    },
                    contentDescription = null,
                    tint = severityColor
                )
                Text(
                    text = insight.severity.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor
                )
            }
        }
    }
}

@Composable
private fun NoPlanCreateAction(
    onCreatePlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onCreatePlan,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp)
    ) {
        Icon(Icons.Default.Schedule, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(NO_PLAN_SUMMARY_ACTION_LABEL)
    }
}

@Composable
private fun ExecutionSummaryRow(
    isPlanSet: Boolean,
    missedSummary: String,
    onCreatePlan: () -> Unit
) {
    if (isPlanSet) {
        PlanSetSummaryRow(missedSummary = missedSummary)
    } else {
        NoPlanSummaryRow(onCreatePlan = onCreatePlan)
    }
}

@Composable
private fun NoPlanSummaryRow(
    onCreatePlan: () -> Unit,
    message: String = NO_PLAN_SUMMARY_MESSAGE,
    showHeader: Boolean = true,
    showAction: Boolean = true,
    messageTextStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
        if (showHeader) {
            Text(
                text = NO_PLAN_SUMMARY_HEADER,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showAction) {
            NoPlanCreateAction(onCreatePlan = onCreatePlan)
        }
        Text(
            text = message,
            style = messageTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoPlanChartSummaryRow(
    onCreatePlan: () -> Unit
) {
    NoPlanSummaryRow(
        onCreatePlan = onCreatePlan,
        message = NO_PLAN_CHART_MESSAGE,
        showHeader = false,
        showAction = true,
        messageTextStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun NoPlanMetricPlaceholderRow(
    label: String,
    value: String = METRIC_NOT_APPLICABLE,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall)
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = valueColor
        )
    }
}

@Composable
private fun PlanSetSummaryRow(missedSummary: String) {
    Text(
        text = missedSummary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PlannedVsActualChart(
    planned: Int,
    actual: Int,
    noPlan: Boolean,
    onCreatePlan: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (noPlan) {
            NoPlanChartSummaryRow(onCreatePlan = onCreatePlan)
            NoPlanMetricPlaceholderRow(label = "Planned")
            NoPlanMetricPlaceholderRow(
                label = "Actual logged",
                value = noPlanAwareActualMetricLabel(actual, false),
                valueColor = noPlanAwareActualMetricColor(actual, false)
            )
        } else {
            val safePlanned = planned.coerceAtLeast(0)
            val safeActual = actual.coerceAtLeast(0)
            val maxValue = max(safePlanned, safeActual).coerceAtLeast(1).toFloat()
            val plannedWidth = safePlanned.toFloat() / maxValue
            val actualWidth = safeActual.toFloat() / maxValue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Planned", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(
                    progress = { plannedWidth },
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Actual", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(
                    progress = { actualWidth },
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        }
    }
}

internal fun hasPlannedDayMinutes(plannedMinutes: Int): Boolean {
    return plannedMinutes > 0
}

internal fun hasNoPlannedDayMinutes(plannedMinutes: Int): Boolean {
    return plannedMinutes <= 0
}

internal fun plannedCompletionPercent(
    plannedMinutes: Int,
    actualMinutes: Int
): Int {
    val safeActualMinutes = actualMinutes.coerceAtLeast(0)
    return if (hasNoPlannedDayMinutes(plannedMinutes)) {
        0
    } else {
        ((safeActualMinutes.toFloat() / plannedMinutes.toFloat()) * 100).roundToInt().coerceIn(0, 100)
    }
}

internal fun planMetricLabel(
    isPlanSet: Boolean,
    actual: String
): String = if (isPlanSet) actual else METRIC_NOT_APPLICABLE

internal fun noPlanAwareActualMetricLabel(
    actualMinutes: Int,
    isPlanSet: Boolean
): String {
    val safeActualMinutes = actualMinutes.coerceAtLeast(0)
    if (isPlanSet) {
        return formatInsightMinutes(safeActualMinutes)
    }
    return if (safeActualMinutes > 0) {
        formatInsightMinutes(safeActualMinutes)
    } else {
        NO_PLAN_ACTUAL_NONE_MESSAGE
    }
}

@Composable
private fun noPlanAwareActualMetricColor(
    actualMinutes: Int,
    isPlanSet: Boolean
): androidx.compose.ui.graphics.Color {
    val safeActualMinutes = actualMinutes.coerceAtLeast(0)
    return if (isPlanSet) {
        MaterialTheme.colorScheme.secondary
    } else if (safeActualMinutes > 0) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
}

internal fun missedBlocksSummaryText(
    isPlanSet: Boolean,
    missedCount: Int
) : String {
    if (!isPlanSet) {
        return NO_PLAN_SUMMARY_MESSAGE
    }

    val safeMissedCount = max(missedCount, 0)
    return when (safeMissedCount) {
        0 -> "Missed blocks: none"
        1 -> "Missed blocks: 1"
        else -> "Missed blocks: $safeMissedCount"
    }
}

@Composable
private fun RecommendationRow(
    text: String,
    sourceLabel: String,
    onApply: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        OutlinedButton(onClick = onApply, shape = RoundedCornerShape(20.dp)) {
            Text("Apply", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatInsightMinutes(minutes: Int): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    val hours = safeMinutes / 60
    val remainingMinutes = safeMinutes % 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${remainingMinutes}m"
}

private fun formatDriftMinutes(driftMinutes: Int): String {
    val sign = if (driftMinutes > 0) "+" else if (driftMinutes < 0) "−" else ""
    return "$sign${formatInsightMinutes(abs(driftMinutes))}"
}

private fun completionStatusText(
    completion: Int,
    isPlanSet: Boolean
): String = when {
    !isPlanSet -> "No plan"
    completion >= 95 -> "Ahead"
    completion >= 80 -> "On track"
    completion >= 60 -> "Some drift"
    else -> "Behind"
}

internal fun executionScoreNarrative(
    completion: Int,
    isPlanSet: Boolean
): String = when {
    !isPlanSet -> EXECUTION_SCORE_NO_PLAN_MESSAGE
    completion >= 95 -> EXECUTION_SCORE_AHEAD_MESSAGE
    completion >= 80 -> EXECUTION_SCORE_ON_TRACK_MESSAGE
    completion >= 60 -> EXECUTION_SCORE_DRIFT_MESSAGE
    else -> EXECUTION_SCORE_BEHIND_MESSAGE
}

internal fun executionScoreAction(
    completion: Int,
    isPlanSet: Boolean
): String = when {
    !isPlanSet -> EXECUTION_SCORE_NO_PLAN_ACTION
    completion >= 95 -> EXECUTION_SCORE_AHEAD_ACTION
    completion >= 80 -> EXECUTION_SCORE_ON_TRACK_ACTION
    completion >= 60 -> EXECUTION_SCORE_DRIFT_ACTION
    else -> EXECUTION_SCORE_BEHIND_ACTION
}

private fun formatCompletionText(completion: Int, isPlanSet: Boolean): String = when {
    !isPlanSet -> "N/A"
    else -> "$completion%"
}

@Composable
private fun scoreColor(
    completion: Int,
    isPlanSet: Boolean
) = when {
    !isPlanSet -> MaterialTheme.colorScheme.onSurfaceVariant
    completion >= 95 -> MaterialTheme.colorScheme.primary
    completion >= 80 -> MaterialTheme.colorScheme.secondary
    completion >= 60 -> MaterialTheme.colorScheme.tertiary
    completion > 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun insightSeveritySort(insight: ReviewInsight): Int = when (insight.severity) {
    ReviewInsightSeverity.CRITICAL -> 0
    ReviewInsightSeverity.WARNING -> 1
    ReviewInsightSeverity.INFO -> 2
}

internal data class InsightCategoryBreakdownRow(
    val category: String,
    val minutes: Int,
    val share: Float,
    val progress: Float
)

internal fun insightCategoryBreakdownRows(
    timeBlocks: List<TimeBlockUiModel>
): List<InsightCategoryBreakdownRow> {
    val categoryMinutes = timeBlocks
        .groupingBy { inferInsightCategory(it) }
        .fold(0) { acc, block -> acc + block.durationMinutes }
    val maxCategoryMinutes = categoryMinutes.values.maxOrNull()?.coerceAtLeast(1) ?: return emptyList()
    val totalMinutes = categoryMinutes.values.sum().coerceAtLeast(1)

    return categoryMinutes.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { (category, categoryMinutes) ->
            InsightCategoryBreakdownRow(
                category = category,
                minutes = categoryMinutes,
                share = categoryMinutes.toFloat() / totalMinutes.toFloat(),
                progress = categoryMinutes.toFloat() / maxCategoryMinutes.toFloat()
            )
        }
}

@Composable
private fun driftAccent(driftMinutes: Int): androidx.compose.ui.graphics.Color = when {
    driftMinutes > 0 -> MaterialTheme.colorScheme.tertiary
    driftMinutes < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun inferInsightCategory(block: TimeBlockUiModel): String = when {
    block.taskId != null -> "Task"
    block.habitId != null -> "Habit"
    block.medicationPlanId != null -> "Medication"
    block.calendarEventId != null -> "Calendar"
    else -> "Block"
}

internal fun focusConcentrationLabel(share: Float): String = when {
    share >= 0.7f -> "High"
    share >= 0.5f -> "Moderate"
    share >= 0.3f -> "Even"
    else -> "Balanced"
}

private data class FocusConcentrationProfile(
    val color: androidx.compose.ui.graphics.Color,
    val tip: String?,
    val suggestion: String?
)

@Composable
private fun focusConcentrationProfile(
    share: Float,
    topCategory: String,
    totalCategories: Int
): FocusConcentrationProfile = when {
    totalCategories == 1 -> FocusConcentrationProfile(
        color = focusConcentrationColor(share),
        tip = "Only one category appears today. Add a short block in another category tomorrow to reduce concentration risk.",
        suggestion = null
    )
    share >= 0.85f -> FocusConcentrationProfile(
        color = focusConcentrationColor(share),
        tip = "Very high focus on $topCategory. Try splitting this category into two shorter focus blocks with a reset task in between.",
        suggestion = "Split this category into smaller sessions and alternate with a 5-10 minute reset block."
    )
    share >= 0.7f -> FocusConcentrationProfile(
        color = focusConcentrationColor(share),
        tip = "You spent most of your day on $topCategory. A short complementary block could improve context recovery.",
        suggestion = "Insert one short task or habit block before your next top-focus segment."
    )
    else -> FocusConcentrationProfile(
        color = focusConcentrationColor(share),
        tip = "Balanced distribution. You are spreading attention across multiple categories.",
        suggestion = null
    )
}

@Composable
private fun focusConcentrationColor(share: Float) = when {
    share >= 0.7f -> MaterialTheme.colorScheme.error
    share >= 0.5f -> MaterialTheme.colorScheme.tertiary
    share >= 0.3f -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun FocusConcentrationLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LegendLine("High (>= 70%)", focusConcentrationColor(0.7f))
        LegendLine("Moderate (>= 50%)", focusConcentrationColor(0.5f))
        LegendLine("Even (>= 30%)", focusConcentrationColor(0.3f))
        LegendLine("Balanced (< 30%)", focusConcentrationColor(0f))
    }
}

@Composable
private fun LegendLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = "● $text",
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

internal fun focusTopCategoryMix(rows: List<InsightCategoryBreakdownRow>): String = rows
    .take(2)
    .joinToString(" • ") { "${it.category} ${percentText(it.share)}" }

private fun percentText(value: Float): String = "${(value * 100).roundToInt()}%"


