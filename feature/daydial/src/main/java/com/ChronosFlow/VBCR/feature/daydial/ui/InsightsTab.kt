package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilledTonalButton

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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ai.AssistDigest
import com.ChronosFlow.VBCR.core.ai.InsightRecommendation
import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCopy
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightSeverity
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.ui.components.ChronosEmptyState
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilterChip
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosSectionTitle
import com.ChronosFlow.VBCR.feature.daydial.ScreenTimeCard
import com.ChronosFlow.VBCR.core.ui.components.GenAiAssistBanner
import com.ChronosFlow.VBCR.core.ui.components.formatDurationLabel
import com.ChronosFlow.VBCR.core.ui.motion.ChronosValueAnimationFactory
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import com.ChronosFlow.VBCR.feature.daydial.DailyReview
import com.ChronosFlow.VBCR.feature.daydial.TimeBlockUiModel
import com.ChronosFlow.VBCR.feature.daydial.model.DayDialTab
import com.ChronosFlow.VBCR.feature.daydial.model.InsightCategoryBreakdownRow
import com.ChronosFlow.VBCR.feature.daydial.model.InsightsPeriod
import com.ChronosFlow.VBCR.feature.daydial.model.InsightsPeriodSummary
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val NO_PLAN_SUMMARY_ACTION_LABEL = "Create plan"
private const val NO_PLAN_SUMMARY_MESSAGE =
    "Create one now to unlock completion, drift, and missed-block analysis."
private const val NO_PLAN_SUMMARY_HEADER = "No plan set for today"
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
    digest: AssistDigest? = null,
    isRefreshing: Boolean,
    onRefreshRecommendations: () -> Unit,
    onApplyRecommendation: (InsightRecommendation) -> Unit,
    onOpenFullReview: () -> Unit = {},
    contentTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    onCreatePlan: () -> Unit = {},
    trendRangeDays: Int = 14,
    trends: com.ChronosFlow.VBCR.feature.daydial.delegate.CompanionTrendSections =
        com.ChronosFlow.VBCR.feature.daydial.delegate.CompanionTrendSections(),
    journalEntry: com.ChronosFlow.VBCR.core.domain.model.JournalEntry? = null,
    sleepTrack: com.ChronosFlow.VBCR.core.domain.model.SleepTrack? = null,
    onTrendRangeSelected: (Int) -> Unit = {},
    onOpenJournal: () -> Unit = {},
    onOpenSleepLog: () -> Unit = {},
    journalEnabled: Boolean = true,
    sleepEnabled: Boolean = true,
    period: InsightsPeriod = InsightsPeriod.DAY,
    periodSummary: InsightsPeriodSummary? = null,
    onPeriodSelected: (InsightsPeriod) -> Unit = {},
    onStartFocus: () -> Unit = {}
) {
    // For DAY the live selected-day inputs are used; week/month swap in the aggregated rollup.
    val effectiveReview = periodSummary?.review ?: review
    val effectiveMissedCount = periodSummary?.missedCount ?: missedCount
    val effectiveCategoryRows = periodSummary?.categoryRows ?: insightCategoryBreakdownRows(timeBlocks)
    val periodPlanned = effectiveReview.plannedMinutes
    val periodActual = effectiveReview.actualMinutes.coerceAtLeast(0)
    val periodMissed = effectiveReview.missedMinutes.coerceAtLeast(0)
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
    val missedSummary = missedBlocksSummaryText(isPlanSet, effectiveMissedCount)
    val categoryRows = effectiveCategoryRows
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
        Spacer(Modifier.height(contentTopPadding + pagePadding))
        DayDialPageHeader(
            title = DayDialTab.INSIGHTS.label,
            subtitle = dayDialPrimaryPageSubtitle(DayDialTab.INSIGHTS),
            icon = DayDialTab.INSIGHTS.icon
        )

        InsightsPeriodSelector(
            period = period,
            isLoading = periodSummary?.isLoading == true,
            onPeriodSelected = onPeriodSelected,
            modifier = Modifier.fillMaxWidth()
        )

        // Quick-filter pills: no selection shows the whole page; tapping pills narrows it to exactly
        // the chosen sections. Selection survives config changes but resets on a fresh page.
        var selectedSections by rememberSaveable { mutableStateOf(emptySet<String>()) }
        InsightsSectionFilterPills(
            selected = selectedSections,
            onToggle = { section ->
                selectedSections = if (section.name in selectedSections) {
                    selectedSections - section.name
                } else {
                    selectedSections + section.name
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (insightsSectionVisible(InsightsSection.EXECUTION, selectedSections)) {
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Execution score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatCompletionText(completion, isPlanSet),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scoreColor(completion, isPlanSet)
                    )
                }
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
                    ExecutionStat("Planned", plannedMinutesLabel, Modifier.weight(1f))
                    ExecutionStat("Actual", actualMinutesLabel, Modifier.weight(1f), accent = actualAccent)
                    ExecutionStat("Missed", missedMinutesLabel, Modifier.weight(1f), accent = missedAccent)
                    ExecutionStat("Drift", driftLabel, Modifier.weight(1f), accent = driftAccentColor)
                }
                ExecutionSummaryRow(
                    isPlanSet = isPlanSet,
                    missedSummary = missedSummary,
                    onCreatePlan = onCreatePlan
                )
                if (isPlanSet) {
                    Text(
                        text = "$executionNarrative $executionAction",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ChronosFilledTonalButton(
                    onClick = onOpenFullReview,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open daily review", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        } // end EXECUTION section

        if (insightsSectionVisible(InsightsSection.CATEGORIES, selectedSections)) {
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                Text(
                    text = "Category breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (categoryRows.isEmpty()) {
                    Text(
                        "No blocks tracked yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val topRow = categoryRows.first()
                    Text(
                        text = "★ ${topRow.category} leads at ${percentText(topRow.share)} · " +
                            "${focusConcentrationLabel(topRow.share)} concentration",
                        style = MaterialTheme.typography.bodySmall,
                        color = focusConcentrationColor(topRow.share)
                    )
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
                    focusConcentrationTip(topRow.share, topRow.category, categoryRows.size)?.let { tip ->
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        } // end CATEGORIES section

        if (insightsSectionVisible(InsightsSection.INSIGHTS, selectedSections)) {
        if (orderedInsights.isNotEmpty()) {
            ChronosSectionTitle(title = "Review insights")
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                    orderedInsights.forEach { insight ->
                        ReviewInsightRow(insight = insight)
                    }
                }
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
                modifier = Modifier.fillMaxWidth(),
                ready = snapshot.isReady
            )
        }

        digest?.takeIf { it.text.isNotBlank() }?.let { dayDigest ->
            ChronosSectionTitle(title = "Day digest")
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                    Text(
                        text = dayDigest.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = GenAiAssistCopy.assistSourceLabel(dayDigest.source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ChronosSectionTitle(title = "AI recommendations")
            ChronosOutlinedButton(
                onClick = onRefreshRecommendations,
                enabled = !isRefreshing,
                shape = RoundedCornerShape(20.dp)
            ) {
                val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
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
                    if (isRefreshing) "Refreshing…" else "Refresh",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
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
        } // end INSIGHTS section

        if (insightsSectionVisible(InsightsSection.SCREEN_TIME, selectedSections)) {
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            ScreenTimeCard(onStartFocus = onStartFocus)
        }
        } // end SCREEN_TIME section

        if (insightsSectionVisible(InsightsSection.TRENDS, selectedSections)) {
        InsightsTrendSections(
            trendRangeDays = trendRangeDays,
            trends = trends,
            journalEntry = journalEntry,
            sleepTrack = sleepTrack,
            onTrendRangeSelected = onTrendRangeSelected,
            onOpenJournal = onOpenJournal,
            onOpenSleepLog = onOpenSleepLog,
            journalEnabled = journalEnabled,
            sleepEnabled = sleepEnabled
        )
        } // end TRENDS section
        Spacer(Modifier.height(bottomContentPadding))
    }
}

@Composable
private fun InsightsPeriodSelector(
    period: InsightsPeriod,
    isLoading: Boolean,
    onPeriodSelected: (InsightsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            InsightsPeriod.values().forEach { option ->
                ChronosFilterChip(
                    selected = option == period,
                    onClick = { onPeriodSelected(option) },
                    label = { Text(option.label) }
                )
            }
            if (isLoading) {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(16.dp)
                        .width(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        if (period != InsightsPeriod.DAY) {
            Text(
                text = insightsPeriodScopeNote(period),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Horizontally scrollable row of section-filter pills. A pill is selected when its [InsightsSection]
 * name is in [selected]; tapping toggles it via [onToggle]. With nothing selected the page shows
 * every section (see [insightsSectionVisible]).
 */
@Composable
private fun InsightsSectionFilterPills(
    selected: Set<String>,
    onToggle: (InsightsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        InsightsSection.values().forEach { section ->
            ChronosFilterChip(
                selected = section.name in selected,
                onClick = { onToggle(section) },
                label = { Text(section.label) }
            )
        }
    }
}

internal fun insightsPeriodScopeNote(period: InsightsPeriod): String = when (period) {
    InsightsPeriod.DAY ->
        "Showing the selected day. Weekly and monthly rollups are one tap away."
    InsightsPeriod.WEEK ->
        "Execution metrics cover the last 7 days. Review insights and recommendations below stay scoped to the selected day."
    InsightsPeriod.MONTH ->
        "Execution metrics cover the last 30 days. Review insights and recommendations below stay scoped to the selected day."
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        Icon(
            imageVector = when (insight.severity) {
                ReviewInsightSeverity.INFO -> Icons.Default.Visibility
                ReviewInsightSeverity.WARNING -> Icons.Default.Analytics
                ReviewInsightSeverity.CRITICAL -> Icons.Default.Schedule
            },
            contentDescription = insight.severity.name.lowercase().replaceFirstChar { it.uppercase() },
            tint = severityColor
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        }
    }
}

@Composable
private fun NoPlanCreateAction(
    onCreatePlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    ChronosFilledTonalButton(
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
private fun NoPlanSummaryRow(onCreatePlan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
        Text(
            text = NO_PLAN_SUMMARY_HEADER,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        NoPlanCreateAction(onCreatePlan = onCreatePlan)
        Text(
            text = NO_PLAN_SUMMARY_MESSAGE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        ChronosOutlinedButton(onClick = onApply, shape = RoundedCornerShape(20.dp)) {
            Text("Apply", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatInsightMinutes(minutes: Int): String =
    formatDurationLabel(minutes.coerceAtLeast(0))

private fun formatDriftMinutes(driftMinutes: Int): String {
    val sign = if (driftMinutes > 0) "+" else if (driftMinutes < 0) "−" else ""
    return "$sign${formatInsightMinutes(abs(driftMinutes))}"
}

@Composable
private fun ExecutionStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }
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

internal fun insightCategoryBreakdownRows(
    timeBlocks: List<TimeBlockUiModel>
): List<InsightCategoryBreakdownRow> =
    categoryBreakdownRows(timeBlocks.map { inferInsightCategory(it) to it.durationMinutes })

internal fun insightCategoryBreakdownRowsFromBlocks(
    blocks: List<TimeBlock>
): List<InsightCategoryBreakdownRow> =
    categoryBreakdownRows(blocks.map { inferInsightCategory(it) to it.durationMinutes })

private fun categoryBreakdownRows(
    categoryMinutePairs: List<Pair<String, Int>>
): List<InsightCategoryBreakdownRow> {
    val categoryMinutes = categoryMinutePairs
        .groupingBy { it.first }
        .fold(0) { acc, pair -> acc + pair.second }
    val maxCategoryMinutes = categoryMinutes.values.maxOrNull()?.coerceAtLeast(1) ?: return emptyList()
    val totalMinutes = categoryMinutes.values.sum().coerceAtLeast(1)

    return categoryMinutes.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { (category, minutes) ->
            InsightCategoryBreakdownRow(
                category = category,
                minutes = minutes,
                share = minutes.toFloat() / totalMinutes.toFloat(),
                progress = minutes.toFloat() / maxCategoryMinutes.toFloat()
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

private fun inferInsightCategory(block: TimeBlock): String = when {
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

internal fun focusConcentrationTip(
    share: Float,
    topCategory: String,
    totalCategories: Int
): String? = when {
    totalCategories == 1 ->
        "Only one category appears today. Add a short block in another category tomorrow to reduce concentration risk."
    share >= 0.85f ->
        "Very high focus on $topCategory. Try splitting this category into two shorter focus blocks with a reset task in between."
    share >= 0.7f ->
        "You spent most of your day on $topCategory. A short complementary block could improve context recovery."
    else -> null
}

@Composable
private fun focusConcentrationColor(share: Float) = when {
    share >= 0.7f -> MaterialTheme.colorScheme.error
    share >= 0.5f -> MaterialTheme.colorScheme.tertiary
    share >= 0.3f -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun percentText(value: Float): String = "${(value * 100).roundToInt()}%"


