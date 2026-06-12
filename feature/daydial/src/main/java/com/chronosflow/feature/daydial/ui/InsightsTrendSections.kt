package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.domain.model.SleepTrack
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.components.ChronosTrendChart
import com.chronosflow.core.ui.components.ChronosTrendChartMode
import com.chronosflow.core.ui.components.ChronosTrendSeries
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.feature.daydial.delegate.CompanionTrendSections

private val TrendRangeOptions = listOf(14, 30)

@Composable
internal fun InsightsTrendSections(
    trendRangeDays: Int,
    trends: CompanionTrendSections,
    journalEntry: JournalEntry?,
    sleepTrack: SleepTrack?,
    onTrendRangeSelected: (Int) -> Unit,
    onOpenJournal: () -> Unit,
    onOpenSleepLog: () -> Unit,
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
                        FilterChip(
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

        if (journalEntry != null) {
            JournalTimelineSection(
                entries = listOf(journalEntry),
                title = "Evening reflection",
                maxEntries = 1
            )
            OutlinedButton(onClick = onOpenJournal) { Text("Open journal") }
        } else {
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                    Text(
                        text = "Evening reflection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "No entry for this day yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onOpenJournal) { Text("Write tonight's entry") }
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
                    } else {
                        Text(
                            text = "Last night not logged yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(onClick = onOpenSleepLog) {
                    Text(if (sleepTrack != null) "Update" else "Log sleep")
                }
            }
        }
    }
}

