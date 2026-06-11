package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosSettingsRow
import com.chronosflow.core.ui.components.formatDurationLabel
import com.chronosflow.feature.daydial.DailyReview

@Composable
internal fun ActionGrid(actions: List<Pair<String, () -> Unit>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, action) ->
                    FilledTonalButton(onClick = action, modifier = Modifier.weight(1f)) {
                        Text(label, textAlign = TextAlign.Center)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun PrivacyModeSelector(
    privacyMode: PrivacyMode,
    onPrivacyModeSelected: (PrivacyMode) -> Unit
) {
    Text("Privacy Mode", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = privacyMode == PrivacyMode.ON_DEVICE_ONLY,
            onClick = { onPrivacyModeSelected(PrivacyMode.ON_DEVICE_ONLY) },
            label = { Text("Gemini Nano") }
        )
        FilterChip(
            selected = privacyMode == PrivacyMode.CLOUD_ALLOWED,
            onClick = { onPrivacyModeSelected(PrivacyMode.CLOUD_ALLOWED) },
            label = { Text("Cloud Gemini") }
        )
        FilterChip(
            selected = privacyMode == PrivacyMode.DISABLED,
            onClick = { onPrivacyModeSelected(PrivacyMode.DISABLED) },
            label = { Text("Privacy Off") }
        )
    }
}

@Composable
internal fun CheckboxSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ChronosSettingsRow(
        title = label,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
internal fun DailyReviewHeader(
    review: DailyReview,
    onPlannedClick: () -> Unit = {},
    onActualClick: () -> Unit = {},
    onMissedClick: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    showReviewAction: Boolean = true
) {
    val completionPercent = if (review.plannedMinutes > 0) {
        ((review.actualMinutes.toFloat() / review.plannedMinutes) * 100).toInt().coerceIn(0, 100)
    } else {
        null
    }
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            completionPercent?.let { percent ->
                Text(
                    text = "$percent% of planned time completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReviewItem(
                    label = "Planned",
                    value = formatReviewMinutes(review.plannedMinutes),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onPlannedClick
                )
                VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outline)
                ReviewItem(
                    label = "Actual",
                    value = formatReviewMinutes(review.actualMinutes),
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onActualClick
                )
                VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outline)
                ReviewItem(
                    label = "Missed",
                    value = formatReviewMinutes(review.missedMinutes),
                    color = if (review.missedMinutes > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = onMissedClick
                )
            }
            if (showReviewAction) {
                FilledTonalButton(
                    onClick = onOpenReview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Review")
                }
            }
        }
    }
}

@Composable
private fun ReviewItem(
    label: String, 
    value: String, 
    color: androidx.compose.ui.graphics.Color, 
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                onClickLabel = reviewMetricActionLabel(label),
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

internal fun reviewMetricActionLabel(label: String): String = when (label) {
    "Planned" -> "Open Planned breakdown"
    "Actual" -> "Open Actual log"
    "Missed" -> "Open Missed recovery"
    else -> "Open $label details"
}

private fun formatReviewMinutes(minutes: Int): String =
    formatDurationLabel(minutes.coerceAtLeast(0))
