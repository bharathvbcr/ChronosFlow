package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.motion.ChronosValueAnimationFactory

/**
 * Shared circular focus timer used on DayDial Focus tab and the standalone Focus screen.
 *
 * @param remainingFraction Fraction of session time still remaining (0..1).
 */
@Composable
fun FocusTimerRing(
    timeLabel: String,
    modifier: Modifier = Modifier,
    sublabel: String = "remaining",
    remainingFraction: Float,
    containerSize: Dp = 220.dp,
    ringSize: Dp = 200.dp,
    reduceMotionEnabled: Boolean = false,
    highContrastEnabled: Boolean = false,
    accentColor: Color? = null
) {
    val animatedSweepAngle by animateFloatAsState(
        targetValue = remainingFraction.coerceIn(0f, 1f) * 360f,
        animationSpec = ChronosValueAnimationFactory.focusTimerProgress(reduceMotionEnabled),
        label = "focusTimerSweep"
    )

    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center
    ) {
        val trackColor = MaterialTheme.colorScheme.outline
        val primaryColor = accentColor ?: MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.tertiary
        // Hoisted so the sweep shader is built once and reused across every animated
        // frame of the progress sweep instead of reallocating a gradient each draw.
        val progressBrush = remember(primaryColor, secondaryColor) {
            Brush.sweepGradient(colors = listOf(primaryColor, secondaryColor, primaryColor))
        }
        // Ambient glow depends only on the accent color, not the per-second time label, so
        // remember it — the ring recomposes every second and would otherwise rebuild it each tick.
        val ambientGlowBrush = remember(primaryColor) {
            Brush.radialGradient(colors = listOf(primaryColor.copy(alpha = 0.06f), Color.Transparent))
        }

        if (!reduceMotionEnabled && !highContrastEnabled) {
            Box(
                modifier = Modifier
                    .size(ringSize - 20.dp)
                    .clip(CircleShape)
                    .background(ambientGlowBrush)
            )
        }

        Canvas(modifier = Modifier.size(ringSize).clearAndSetSemantics { }) {
            drawArc(
                color = trackColor.copy(alpha = if (highContrastEnabled) 0.35f else 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
            if (highContrastEnabled) {
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = animatedSweepAngle,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    brush = progressBrush,
                    startAngle = -90f,
                    sweepAngle = animatedSweepAngle,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatFocusCountdown(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

fun formatFocusCountdown(seconds: Long): String = formatFocusCountdown(seconds.toInt())
