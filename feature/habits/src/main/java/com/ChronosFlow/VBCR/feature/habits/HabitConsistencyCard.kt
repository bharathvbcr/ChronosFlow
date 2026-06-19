package com.ChronosFlow.VBCR.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.domain.model.HabitDailyCompletion
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosTrendChart
import com.ChronosFlow.VBCR.core.ui.components.ChronosTrendChartMode
import com.ChronosFlow.VBCR.core.ui.components.ChronosTrendSeries

/**
 * Motivating 14-day completion bar chart for the Habits page. Reuses the shared [ChronosTrendChart]
 * so it matches the Insights tab styling, and adds an encouraging headline derived from the trend.
 */
@Composable
fun HabitConsistencyCard(
    trend: List<HabitDailyCompletion>,
    modifier: Modifier = Modifier
) {
    val totalCompleted = trend.sumOf { it.completedCount }
    if (trend.isEmpty() || totalCompleted == 0) return

    val totalMissed = trend.sumOf { it.missedCount }
    val activeDays = trend.count { it.completedCount > 0 }
    val windowDays = trend.size

    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Consistency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        habitConsistencyHeadline(activeDays, windowDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "$activeDays/$windowDays days",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            ChronosTrendChart(
                series = listOf(
                    ChronosTrendSeries(
                        label = "Completed",
                        color = MaterialTheme.colorScheme.secondary,
                        points = trend.map { it.completedCount.toFloat() }
                    )
                ),
                mode = ChronosTrendChartMode.BAR
            )
            Text(
                "$totalCompleted completed · $totalMissed missed in the last $windowDays days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun habitConsistencyHeadline(activeDays: Int, windowDays: Int): String {
    if (windowDays == 0) return "Complete a habit to start your trend."
    val ratio = activeDays.toFloat() / windowDays
    return when {
        ratio >= 0.85f -> "On fire — almost every day counts."
        ratio >= 0.5f -> "Strong rhythm. Keep it rolling."
        ratio >= 0.25f -> "Building momentum, one day at a time."
        else -> "Every completed day moves you forward."
    }
}
