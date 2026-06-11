package com.chronosflow.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.chronosflow.wear.model.WearBlock
import kotlin.math.cos
import kotlin.math.sin

/**
 * The watch take on the phone's DayDial: today mapped onto a 24-hour ring hugging the round
 * bezel (midnight at the top, clockwise). Each scheduled block is an arc — the one happening
 * now in full primary, upcoming ones dimmed — and a tertiary dot marks the current time, so
 * "what does my day look like" is answered by the screen's edge without any text.
 */
@Composable
internal fun DayDialRing(
    blocks: List<WearBlock>,
    nowMinute: Int,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val blockColor = MaterialTheme.colorScheme.primaryDim.copy(alpha = 0.65f)
    val currentColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier.fillMaxSize().padding(2.dp)) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val arcTopLeft = Offset(inset, inset)

        fun angleOf(minute: Int) = minute / 1440f * 360f - 90f

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(stroke)
        )

        blocks.forEach { block ->
            val sweep = (block.endMinute - block.startMinute).coerceAtLeast(0) / 1440f * 360f
            if (sweep <= 0f) return@forEach
            val isCurrent = nowMinute >= block.startMinute && nowMinute < block.endMinute
            drawArc(
                color = if (isCurrent) currentColor else blockColor,
                startAngle = angleOf(block.startMinute),
                sweepAngle = sweep.coerceAtMost(360f),
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }

        val markerAngle = Math.toRadians(angleOf(nowMinute % 1440).toDouble())
        val radius = (size.minDimension - stroke) / 2f
        drawCircle(
            color = markerColor,
            radius = stroke * 0.8f,
            center = center + Offset(
                (cos(markerAngle) * radius).toFloat(),
                (sin(markerAngle) * radius).toFloat()
            )
        )
    }
}
