package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun ChronosTimeWindowControls(
    startText: String,
    endText: String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    parsedStart: Int?,
    parsedEnd: Int?,
    showFineTuneFields: Boolean,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    modifier: Modifier = Modifier,
    durationMinutes: Int? = null,
    onDurationChange: ((Int) -> Unit)? = null,
    durationRangeMinutes: IntRange = 15..240
) {
    val duration = durationMinutes ?: run {
        if (parsedStart != null && parsedEnd != null && parsedEnd > parsedStart) {
            parsedEnd - parsedStart
        } else {
            null
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (parsedStart != null && parsedEnd != null) {
            ChronosTimeWindowSummary(
                startLabel = formatDisplayMinute(parsedStart),
                endLabel = formatDisplayMinute(parsedEnd),
                durationLabel = duration?.let { formatDurationLabel(it) }
            )
        }
        if (duration != null && onDurationChange != null) {
            ChronosDurationSlider(
                durationMinutes = duration,
                onDurationChange = onDurationChange,
                range = durationRangeMinutes,
                label = "Window length"
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChronosFilledTonalButton(
                onClick = { onNudgeStart(-15) },
                modifier = Modifier.weight(1f)
            ) { Text("Start −15m") }
            ChronosFilledTonalButton(
                onClick = { onNudgeStart(15) },
                modifier = Modifier.weight(1f)
            ) { Text("Start +15m") }
            ChronosFilledTonalButton(
                onClick = { onNudgeEnd(15) },
                modifier = Modifier.weight(1f)
            ) { Text("End +15m") }
        }
        if (showFineTuneFields) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChronosTimePickerField(
                    label = "Start",
                    value = if (parsedStart != null) formatDisplayMinute(parsedStart) else startText,
                    selectedMinute = parsedStart,
                    onTimeSelected = { onStartChange(formatDisplayMinute(it)) },
                    modifier = Modifier.weight(1f),
                )
                ChronosTimePickerField(
                    label = "End",
                    value = if (parsedEnd != null) formatDisplayMinute(parsedEnd) else endText,
                    selectedMinute = parsedEnd,
                    onTimeSelected = { onEndChange(formatDisplayMinute(it)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun ChronosTimeWindowSummary(
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    durationLabel: String? = null
) {
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Time window",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$startLabel – $endLabel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            durationLabel?.let { label ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

fun formatDurationLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun nudgeMinuteText(currentText: String, deltaMinutes: Int, fallbackMinute: Int = 8 * 60): String {
    val base = parseFlexibleMinute(currentText) ?: fallbackMinute
    val nudged = (base + deltaMinutes).coerceIn(0, 23 * 60 + 59)
    return formatDisplayMinute(nudged)
}

fun applyDurationToWindow(startMinute: Int, durationMinutes: Int): Int =
    (startMinute + max(durationMinutes, 15)).coerceAtMost(24 * 60)
