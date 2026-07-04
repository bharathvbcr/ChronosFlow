package com.ChronosFlow.VBCR.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.ChronosFlow.VBCR.wear.model.WearBlock
import com.ChronosFlow.VBCR.wear.model.currentBlock
import com.ChronosFlow.VBCR.wear.model.upcomingBlockCount
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
    // Past blocks fade to a neutral grey (done/elapsed), upcoming ones stay primary-tinted, and the
    // one happening now is full primary — so the ring reads "how much of the day is behind me".
    val pastColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val upcomingColor = MaterialTheme.colorScheme.primaryDim.copy(alpha = 0.65f)
    val currentColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.tertiary

    // The ring answers "what does my day look like" without any text, so give TalkBack a spoken
    // equivalent of that glance — otherwise the primary screen's edge context is silent to it.
    val ringDescription = remember(blocks, nowMinute) {
        val remaining = upcomingBlockCount(blocks, nowMinute)
        val remainder = when (remaining) {
            0 -> "no more blocks today"
            1 -> "1 block remaining today"
            else -> "$remaining blocks remaining today"
        }
        if (currentBlock(blocks, nowMinute) != null) {
            "Day schedule, in a scheduled block now, $remainder"
        } else {
            "Day schedule, $remainder"
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .semantics { contentDescription = ringDescription }
    ) {
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
            // Wrap-aware span so a block packed across midnight (endMinute < startMinute) still
            // draws its real duration instead of coercing to zero and silently vanishing.
            val start = ((block.startMinute % 1440) + 1440) % 1440
            val span = (((block.endMinute - block.startMinute) % 1440) + 1440) % 1440
            val sweep = span / 1440f * 360f
            if (sweep <= 0f) return@forEach
            val isCurrent = nowMinute >= block.startMinute && nowMinute < block.endMinute
            val color = when {
                isCurrent -> currentColor
                block.endMinute <= nowMinute -> pastColor
                else -> upcomingColor
            }
            // Butt caps (matching the track) so each arc's angular extent equals its true duration
            // and adjacent blocks show a crisp boundary — round caps overstate short blocks and
            // fuse back-to-back ones into a single arc.
            drawArc(
                color = color,
                startAngle = angleOf(start),
                sweepAngle = sweep.coerceAtMost(360f),
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(stroke)
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
