package com.chronosflow.feature.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosTrendChart
import com.chronosflow.core.ui.components.ChronosTrendChartMode
import com.chronosflow.core.ui.components.ChronosTrendSeries
import com.chronosflow.core.ui.motion.ChronosValueAnimationFactory
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Engagement snapshot derived from the in-memory task list — no extra persistence needed. */
internal data class TaskStatsSnapshot(
    val total: Int,
    val done: Int,
    val completedThisWeek: Int,
    val dueToday: Int,
    val overdue: Int,
    val openLow: Int,
    val openMedium: Int,
    val openHigh: Int
) {
    val completionRate: Float get() = if (total == 0) 0f else done.toFloat() / total
    val openCount: Int get() = openLow + openMedium + openHigh
}

internal fun buildTaskStatsSnapshot(
    tasks: List<Task>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): TaskStatsSnapshot {
    val today = now.atZone(zoneId).toLocalDate()
    val weekAgo = now.minusSeconds(7L * 24 * 60 * 60)
    val open = tasks.filterNot { it.isCompleted }
    return TaskStatsSnapshot(
        total = tasks.size,
        done = tasks.count { it.isCompleted },
        completedThisWeek = tasks.count { it.isCompleted && !it.updatedAt.isBefore(weekAgo) },
        dueToday = open.count { task ->
            task.dueDate?.atZone(zoneId)?.toLocalDate() == today
        },
        overdue = open.count { task -> task.dueDate?.isBefore(now) == true },
        openLow = open.count { it.priority <= 0 },
        openMedium = open.count { it.priority == 1 },
        openHigh = open.count { it.priority >= 2 }
    )
}

/**
 * Motivating overview card for the Tasks page: a completion ring plus this-week progress,
 * due-today / overdue counts, and an open-work priority breakdown bar. All values are derived
 * from the already-loaded task list so the card stays in sync without extra queries.
 */
@Composable
fun TaskStatsCard(
    tasks: List<Task>,
    modifier: Modifier = Modifier
) {
    val stats = remember(tasks) { buildTaskStatsSnapshot(tasks) }
    if (stats.total == 0) return

    val ringColor = MaterialTheme.colorScheme.primary
    val ringTrack = MaterialTheme.colorScheme.surfaceVariant
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled

    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompletionRing(
                    progress = stats.completionRate,
                    trackColor = ringTrack,
                    progressColor = ringColor,
                    reduceMotion = reduceMotion
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        taskStatsHeadline(stats),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${stats.completedThisWeek} completed this week",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (stats.dueToday > 0 || stats.overdue > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stats.dueToday > 0) {
                        TaskStatPill(
                            label = "${stats.dueToday} due today",
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (stats.overdue > 0) {
                        TaskStatPill(
                            label = "${stats.overdue} overdue",
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (stats.openCount > 0) {
                PriorityBreakdownBar(stats = stats)
            }

            val weekTrend = remember(tasks) { taskCompletionByDay(tasks) }
            if (weekTrend.sum() > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Completed · last 7 days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChronosTrendChart(
                        series = listOf(
                            ChronosTrendSeries(
                                label = "",
                                color = MaterialTheme.colorScheme.secondary,
                                points = weekTrend.map { it.toFloat() }
                            )
                        ),
                        mode = ChronosTrendChartMode.BAR,
                        height = 56.dp
                    )
                }
            }
        }
    }
}

/**
 * Tasks completed on each of the trailing [days] days (oldest first), bucketed by the completion's
 * `updatedAt`. An approximation — a done task edited later moves buckets — but a useful recent-activity
 * view, and the only completion signal a task carries without a dedicated event log. Pure for testing.
 */
internal fun taskCompletionByDay(
    tasks: List<Task>,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    days: Int = 7
): List<Int> {
    if (days <= 0) return emptyList()
    val start = today.minusDays((days - 1).toLong())
    val completedDates = tasks
        .filter { it.isCompleted }
        .map { it.updatedAt.atZone(zoneId).toLocalDate() }
    return (0 until days).map { offset ->
        val date = start.plusDays(offset.toLong())
        completedDates.count { it == date }
    }
}

@Composable
private fun CompletionRing(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    reduceMotion: Boolean
) {
    val clamped = progress.coerceIn(0f, 1f)
    // Sweep up from zero on first appearance so the ring fills with a satisfying motion.
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(clamped) { target = clamped }
    val sweep by animateFloatAsState(
        targetValue = target,
        animationSpec = ChronosValueAnimationFactory.stateChange(reduceMotion),
        label = "taskCompletionRingSweep"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
        Canvas(modifier = Modifier.size(84.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            "${(sweep * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PriorityBreakdownBar(stats: TaskStatsSnapshot) {
    val high = MaterialTheme.colorScheme.error
    val medium = MaterialTheme.colorScheme.tertiary
    val low = MaterialTheme.colorScheme.secondary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${stats.openCount} open by priority",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val total = stats.openCount.toFloat()
            if (stats.openHigh > 0) {
                Box(modifier = Modifier.weight(stats.openHigh / total).fillMaxWidth().background(high))
            }
            if (stats.openMedium > 0) {
                Box(modifier = Modifier.weight(stats.openMedium / total).fillMaxWidth().background(medium))
            }
            if (stats.openLow > 0) {
                Box(modifier = Modifier.weight(stats.openLow / total).fillMaxWidth().background(low))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stats.openHigh > 0) PriorityLegend("Urgent ${stats.openHigh}", high)
            if (stats.openMedium > 0) PriorityLegend("Medium ${stats.openMedium}", medium)
            if (stats.openLow > 0) PriorityLegend("Normal ${stats.openLow}", low)
        }
    }
}

@Composable
private fun PriorityLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TaskStatPill(label: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
    ) {
        Text(
            label,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content
        )
    }
}

internal fun taskStatsHeadline(stats: TaskStatsSnapshot): String {
    return when {
        stats.total == 0 -> "Add a task to get started."
        stats.openCount == 0 -> "All clear — every task is done!"
        stats.completionRate >= 0.75f -> "Almost there. Finish strong."
        stats.completedThisWeek > 0 -> "Nice momentum this week."
        else -> "Knock out your first task today."
    }
}
