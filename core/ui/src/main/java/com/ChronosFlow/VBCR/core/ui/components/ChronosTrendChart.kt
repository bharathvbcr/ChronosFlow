package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** One labelled series of values to plot. */
data class ChronosTrendSeries(
    val label: String,
    val color: Color,
    val points: List<Float>
)

enum class ChronosTrendChartMode { LINE, BAR }

/**
 * A compact Canvas line/bar chart for companion analytics (mood/energy/stress/focus over time,
 * or a single adherence series). Values are normalized across all series so they share one axis.
 */
@Composable
fun ChronosTrendChart(
    series: List<ChronosTrendSeries>,
    modifier: Modifier = Modifier,
    mode: ChronosTrendChartMode = ChronosTrendChartMode.LINE,
    height: androidx.compose.ui.unit.Dp = 120.dp,
    valueRange: ClosedFloatingPointRange<Float>? = null
) {
    val nonEmpty = series.filter { it.points.isNotEmpty() }
    if (nonEmpty.isEmpty()) return

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val allValues = nonEmpty.flatMap { it.points }
    val minValue = valueRange?.start ?: allValues.min()
    val maxValue = valueRange?.endInclusive ?: allValues.max()
    val span = (maxValue - minValue).takeIf { it > 0f } ?: 1f

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val w = size.width
            val h = size.height
            // Baseline + midline.
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = h * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            fun xFor(index: Int, count: Int): Float =
                if (count <= 1) w / 2f else w * index / (count - 1).toFloat()

            fun yFor(value: Float): Float = h - ((value - minValue) / span) * h

            when (mode) {
                ChronosTrendChartMode.LINE -> nonEmpty.forEach { s ->
                    val count = s.points.size
                    val path = Path()
                    s.points.forEachIndexed { index, value ->
                        val x = xFor(index, count)
                        val y = yFor(value)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = s.color,
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                    s.points.forEachIndexed { index, value ->
                        drawCircle(
                            color = s.color,
                            radius = 3.5f,
                            center = Offset(xFor(index, count), yFor(value))
                        )
                    }
                }

                ChronosTrendChartMode.BAR -> {
                    val s = nonEmpty.first()
                    val count = s.points.size
                    val slot = if (count == 0) w else w / count
                    val barWidth = slot * 0.55f
                    s.points.forEachIndexed { index, value ->
                        val centerX = slot * index + slot / 2f
                        val top = yFor(value)
                        drawLine(
                            color = s.color,
                            start = Offset(centerX, h),
                            end = Offset(centerX, top),
                            strokeWidth = barWidth,
                            cap = StrokeCap.Butt
                        )
                    }
                }
            }
        }

        if (nonEmpty.size > 1 || nonEmpty.first().label.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                nonEmpty.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(s.color)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = s.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
