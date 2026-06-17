package com.chronosflow.feature.daydial.ui

import com.chronosflow.core.ui.components.ChronosOutlinedButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.domain.model.SleepSource
import com.chronosflow.core.domain.model.bestFocusDay
import com.chronosflow.core.domain.model.distractionNudge
import com.chronosflow.core.domain.model.focusRatioTrend
import com.chronosflow.core.domain.model.SleepTrack
import com.chronosflow.core.domain.model.SleepTrendNight
import com.chronosflow.core.domain.model.SleepTrends
import com.chronosflow.core.ui.components.ChronosFilterChip
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.components.ChronosTrendChart
import com.chronosflow.core.ui.components.ChronosTrendChartMode
import com.chronosflow.core.ui.components.ChronosTrendSeries
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.core.ui.components.formatDurationLabel
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.feature.daydial.delegate.CompanionTrendSections
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TrendRangeOptions = listOf(7, 14, 30)

/**
 * Caps for the eagerly-composed history lists on the Insights tab (it is a verticalScroll, not a
 * lazy list). A long window can produce many rows, so only the most recent are rendered inline with
 * a "showing N of M" note; the rest stay available on the journal/sleep surfaces.
 */
private const val InlineHistoryCap = 10

private val sleepNightDateFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

@Composable
internal fun InsightsTrendSections(
    trendRangeDays: Int,
    trends: CompanionTrendSections,
    journalEntry: JournalEntry?,
    sleepTrack: SleepTrack?,
    onTrendRangeSelected: (Int) -> Unit,
    onOpenJournal: () -> Unit,
    onOpenSleepLog: () -> Unit,
    journalEnabled: Boolean = true,
    sleepEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
    ) {
        ChronosSectionTitle(title = "Trends")
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                    TrendRangeOptions.forEach { days ->
                        ChronosFilterChip(
                            selected = trendRangeDays == days,
                            onClick = { onTrendRangeSelected(days) },
                            label = { Text("${days}d") }
                        )
                    }
                }
                val dailyAverages = trends.moodTrends.dailyAverages
                if (dailyAverages.isEmpty()) {
                    Text(
                        text = "Check in with mood and energy to build this trend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ChronosTrendChart(
                        series = listOf(
                            ChronosTrendSeries(
                                label = "Mood",
                                color = MaterialTheme.colorScheme.primary,
                                points = dailyAverages.map { it.avgMood }
                            ),
                            ChronosTrendSeries(
                                label = "Energy",
                                color = MaterialTheme.colorScheme.tertiary,
                                points = dailyAverages.map { it.avgEnergy }
                            )
                        ),
                        valueRange = 1f..5f
                    )
                    trends.moodTrends.peakEnergyHour?.let { hour ->
                        Text(
                            text = "Energy usually peaks around %02d:00.".format(hour),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (trends.habitTrend.isNotEmpty()) {
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                    Text(
                        text = "Habit consistency",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    ChronosTrendChart(
                        series = listOf(
                            ChronosTrendSeries(
                                label = "Completed",
                                color = MaterialTheme.colorScheme.secondary,
                                points = trends.habitTrend.map { it.completedCount.toFloat() }
                            )
                        ),
                        mode = ChronosTrendChartMode.BAR
                    )
                    Text(
                        text = "${trends.habitTrend.sumOf { it.completedCount }} completed · " +
                            "${trends.habitTrend.sumOf { it.missedCount }} missed in the last $trendRangeDays days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (trends.medicationTrend.isNotEmpty()) {
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                    Text(
                        text = "Medication pattern",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    ChronosTrendChart(
                        series = listOf(
                            ChronosTrendSeries(
                                label = "Taken",
                                color = MaterialTheme.colorScheme.primary,
                                points = trends.medicationTrend.map { it.takenCount.toFloat() }
                            )
                        ),
                        mode = ChronosTrendChartMode.BAR
                    )
                    Text(
                        text = "${trends.medicationTrend.sumOf { it.takenCount }} taken · " +
                            "${trends.medicationTrend.sumOf { it.missedCount }} missed in the last $trendRangeDays days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (trends.screenTimeTrend.any { it.totalMinutes > 0 }) {
            val screenTime = trends.screenTimeTrend
            val totalProductive = screenTime.sumOf { it.productiveMinutes }
            val totalDistracting = screenTime.sumOf { it.distractingMinutes }
            val totalTracked = screenTime.sumOf { it.totalMinutes }
            val focusPercent = if (totalTracked == 0) 0 else totalProductive * 100 / totalTracked
            val activeDays = screenTime.filter { it.totalMinutes > 0 }
            val goalMinutes = trends.screenTimeFocusGoalMinutes
            val daysMetGoal = if (goalMinutes > 0) activeDays.count { it.productiveMinutes >= goalMinutes } else 0
            // Subtle nudge: today's distraction is notably above the window's typical level.
            val distractionNudge = distractionNudge(screenTime, screenTime.last().date)
            val bestDay = bestFocusDay(screenTime)
            // Direction of focus across the window (recent half vs earlier half), if meaningful.
            val focusTrend = focusRatioTrend(screenTime)?.takeIf { kotlin.math.abs(it.deltaPercent) >= 3 }
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                    Text(
                        text = "Screen time focus",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$focusPercent% of screen time focused",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ChronosTrendChart(
                        series = listOf(
                            ChronosTrendSeries(
                                label = "Focused",
                                color = MaterialTheme.colorScheme.primary,
                                points = screenTime.map { it.productiveMinutes / 60f }
                            ),
                            ChronosTrendSeries(
                                label = "Distracting",
                                color = MaterialTheme.colorScheme.error,
                                points = screenTime.map { it.distractingMinutes / 60f }
                            )
                        )
                    )
                    Text(
                        text = "${formatDurationLabel(totalProductive)} focused · " +
                            "${formatDurationLabel(totalDistracting)} distracting in the last $trendRangeDays days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    focusTrend?.let { trend ->
                        Text(
                            text = if (trend.isImproving) {
                                "Focus trending up — ${trend.recentPercent}% recently vs ${trend.earlierPercent}% earlier"
                            } else {
                                "Focus trending down — ${trend.recentPercent}% recently vs ${trend.earlierPercent}% earlier"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (trend.isImproving) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                    if (goalMinutes > 0) {
                        Text(
                            text = "Met your ${formatDurationLabel(goalMinutes)} focus goal on " +
                                "$daysMetGoal of ${activeDays.size} days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    bestDay?.let { day ->
                        Text(
                            text = "Best focus day: ${day.date.format(sleepNightDateFormatter)} · " +
                                formatDurationLabel(day.productiveMinutes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    distractionNudge?.let { nudge ->
                        Text(
                            text = "Heads up — more distracted than your usual today " +
                                "(${formatDurationLabel(nudge.todayDistractingMinutes)} vs " +
                                "${formatDurationLabel(nudge.averageDistractingMinutes)} avg).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (trends.topDistractingApps.isNotEmpty()) {
                        Text(
                            text = "Top distractions",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        trends.topDistractingApps.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatDurationLabel(app.minutes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (journalEnabled) {
            if (trends.journalHistory.isNotEmpty()) {
                JournalTimelineSection(
                    entries = trends.journalHistory,
                    title = "Journal history",
                    maxEntries = InlineHistoryCap,
                    summary = journalHistorySummary(
                        total = trends.journalHistory.size,
                        shown = minOf(trends.journalHistory.size, InlineHistoryCap),
                        windowDays = trendRangeDays
                    )
                )
            }
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
                    ) {
                        Text(
                            text = "Evening reflection",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = journalEntry?.body?.takeIf { it.isNotBlank() }
                                ?: "No entry for this day yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ChronosOutlinedButton(onClick = onOpenJournal) {
                        Text(if (journalEntry != null) "Open" else "Write")
                    }
                }
            }
        }

        if (sleepEnabled) {
            val sleepTrend = trends.sleepTrend
            if (!sleepTrend.isEmpty) {
                val qualityPoints = sleepTrend.loggedNights.mapNotNull { it.quality?.toFloat() }
                val durationHourPoints =
                    sleepTrend.loggedNights.mapNotNull { it.durationMinutes?.let { minutes -> minutes / 60f } }
                ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                        Text(
                            text = "Sleep trends",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (qualityPoints.isNotEmpty()) {
                            Text(
                                text = "Quality (1–5)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ChronosTrendChart(
                                series = listOf(
                                    ChronosTrendSeries(
                                        label = "Quality",
                                        color = MaterialTheme.colorScheme.primary,
                                        points = qualityPoints
                                    )
                                ),
                                valueRange = 1f..5f
                            )
                        }
                        if (durationHourPoints.isNotEmpty()) {
                            Text(
                                text = "Hours slept",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ChronosTrendChart(
                                series = listOf(
                                    ChronosTrendSeries(
                                        label = "Hours",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        points = durationHourPoints
                                    )
                                )
                            )
                        }
                        Text(
                            text = sleepTrendSummary(sleepTrend, trendRangeDays),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val recentNights = sleepTrend.loggedNights.sortedByDescending { it.date }
                        if (recentNights.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
                                recentNights.take(InlineHistoryCap).forEach { night ->
                                    SleepNightRow(night = night)
                                }
                                if (recentNights.size > InlineHistoryCap) {
                                    Text(
                                        text = "Showing $InlineHistoryCap of ${recentNights.size} logged nights",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
                ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)
                ) {
                    Text(
                        text = "Sleep",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (sleepTrack != null) {
                        val window = listOfNotNull(
                            sleepTrack.actualStartMinute?.let(::formatDisplayMinute),
                            sleepTrack.actualEndMinute?.let(::formatDisplayMinute)
                        ).joinToString(" – ").ifBlank { "times not set" }
                        Text(
                            text = "Quality ${sleepTrack.sleepQuality}/5 · $window" +
                                if (sleepTrack.interruptedCount > 0) " · ${sleepTrack.interruptedCount} interruptions" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (sleepTrack.source == SleepSource.HEALTH_CONNECT) {
                            Text(
                                text = "From Health Connect",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = "Last night not logged yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ChronosOutlinedButton(onClick = onOpenSleepLog) {
                    Text(if (sleepTrack != null) "Update" else "Log sleep")
                }
                }
            }
        }
    }
}

@Composable
private fun SleepNightRow(night: SleepTrendNight) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = night.date.format(sleepNightDateFormatter),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        val detail = listOfNotNull(
            night.quality?.let { "$it/5" },
            night.durationMinutes?.let(::formatDurationLabel)
        ).joinToString(" · ").ifBlank { "Logged" }
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun journalHistorySummary(total: Int, shown: Int, windowDays: Int): String {
    val label = if (total == 1) "reflection" else "reflections"
    return if (shown < total) {
        "Showing $shown of $total $label from the last $windowDays days"
    } else {
        "$total $label in the last $windowDays days"
    }
}

private fun sleepTrendSummary(trends: SleepTrends, windowDays: Int): String {
    val parts = mutableListOf<String>()
    trends.averageQuality?.let { parts += "Avg quality %.1f/5".format(it) }
    trends.averageDurationMinutes?.let { parts += "avg ${formatDurationLabel(it)}" }
    val nights = trends.loggedNights.size
    parts += "$nights ${if (nights == 1) "night" else "nights"} logged in $windowDays days"
    return parts.joinToString(" · ")
}

