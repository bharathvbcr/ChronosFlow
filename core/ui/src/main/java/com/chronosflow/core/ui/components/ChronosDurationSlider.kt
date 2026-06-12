package com.chronosflow.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shared duration control for form sheets: label and live value badge over a
 * continuous slider with minus/plus steppers for precision. Values snap to
 * 5-minute steps up to two hours, then 15-minute steps, which keeps long
 * blocks (up to 8h) adjustable without dozens of slider detents.
 */
@Composable
fun ChronosDurationSlider(
    durationMinutes: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 5..480,
    label: String = "Duration"
) {
    val haptics = LocalHapticFeedback.current
    val clamped = durationMinutes.coerceIn(range.first, range.last)

    fun update(newValue: Int) {
        if (newValue != clamped) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onDurationChange(newValue)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = formatDurationLabel(clamped),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { update(stepDurationMinutes(clamped, -1, range)) },
                modifier = Modifier.semantics { contentDescription = "Decrease $label" }
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null)
            }
            Slider(
                value = clamped.toFloat(),
                onValueChange = { update(snapDurationMinutes(it, range)) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = { update(stepDurationMinutes(clamped, 1, range)) },
                modifier = Modifier.semantics { contentDescription = "Increase $label" }
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    }
}

private const val FineStepMinutes = 5
private const val CoarseStepMinutes = 15
private const val CoarseStepThresholdMinutes = 120

fun durationStepMinutes(durationMinutes: Int): Int =
    if (durationMinutes < CoarseStepThresholdMinutes) FineStepMinutes else CoarseStepMinutes

fun snapDurationMinutes(value: Float, range: IntRange): Int {
    val raw = value.coerceIn(range.first.toFloat(), range.last.toFloat())
    val step = durationStepMinutes(raw.roundToInt())
    return ((raw / step).roundToInt() * step).coerceIn(range.first, range.last)
}

fun stepDurationMinutes(durationMinutes: Int, direction: Int, range: IntRange): Int {
    // Step relative to the value we are leaving so 120 -> 115 going down, 120 -> 135 going up.
    val step = if (direction < 0) {
        durationStepMinutes(durationMinutes - 1)
    } else {
        durationStepMinutes(durationMinutes)
    }
    return (durationMinutes + direction * step).coerceIn(range.first, range.last)
}
