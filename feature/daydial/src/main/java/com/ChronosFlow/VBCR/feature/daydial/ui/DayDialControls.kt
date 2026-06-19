package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.core.ui.components.ChronosFilledTonalButton

import androidx.compose.foundation.clickable
import com.ChronosFlow.VBCR.core.ui.motion.chronosHapticClick
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.domain.wear.WearLinkStatus
import com.ChronosFlow.VBCR.core.ui.components.ChronosFilterChip
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosSectionTitle
import com.ChronosFlow.VBCR.core.ui.components.ChronosSettingsRow
import com.ChronosFlow.VBCR.core.ui.components.formatDurationLabel
import com.ChronosFlow.VBCR.core.ui.components.formatLastSyncedLabel
import com.ChronosFlow.VBCR.feature.daydial.DailyReview
import kotlinx.coroutines.launch

@Composable
internal fun ActionGrid(actions: List<Pair<String, () -> Unit>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, action) ->
                    ChronosFilledTonalButton(onClick = action, modifier = Modifier.weight(1f)) {
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
        ChronosFilterChip(
            selected = privacyMode == PrivacyMode.ON_DEVICE_ONLY,
            onClick = { onPrivacyModeSelected(PrivacyMode.ON_DEVICE_ONLY) },
            label = { Text("Gemini Nano") }
        )
        ChronosFilterChip(
            selected = privacyMode == PrivacyMode.CLOUD_ALLOWED,
            onClick = { onPrivacyModeSelected(PrivacyMode.CLOUD_ALLOWED) },
            label = { Text("Cloud Gemini") }
        )
        ChronosFilterChip(
            selected = privacyMode == PrivacyMode.DISABLED,
            onClick = { onPrivacyModeSelected(PrivacyMode.DISABLED) },
            label = { Text("Privacy Off") }
        )
    }
}

/**
 * Wear OS link status + on-demand sync, for the Privacy & Sync settings. Shows whether a watch is
 * reachable and when the phone last pushed the day summary, and lets the user force a push — the
 * direct fix for a watch stuck on "Not synced yet" while the phone app is open. Status loads when
 * the card appears and refreshes after a manual sync.
 */
@Composable
internal fun WearLinkStatusCard(
    backdropMutedText: Color,
    showMessage: (String) -> Unit
) {
    val provider = rememberWearLinkStatusProvider()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<WearLinkStatus?>(null) }
    var syncing by remember { mutableStateOf(false) }
    LaunchedEffect(provider) { status = runCatching { provider.currentStatus() }.getOrNull() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChronosSectionTitle(title = "Watch", subtitle = "Wear OS sync")
        val current = status
        val connectionLine = when {
            current == null -> "Checking watch…"
            current.watchAppInstalled ->
                "Connected" + (current.connectedNodeName?.let { ": $it" }.orEmpty())
            current.watchConnected -> "Watch connected — ChronosFlow app not installed on it"
            current.watchPaired -> "Watch paired but not reachable"
            else -> "No watch connected"
        }
        Text(connectionLine, style = MaterialTheme.typography.bodyMedium)
        if (current?.watchConnected == true && !current.watchAppInstalled) {
            Text(
                "Install ChronosFlow on your watch from the Play Store on the watch to sync.",
                style = MaterialTheme.typography.bodySmall,
                color = backdropMutedText
            )
        }
        Text(
            formatLastSyncedLabel(
                current?.lastPublishedAtMillis?.takeIf { it > 0L },
                System.currentTimeMillis()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = backdropMutedText
        )
        Text(
            "Opening the watch app pulls a fresh copy automatically. Sync now if the watch still " +
                "looks out of date.",
            style = MaterialTheme.typography.bodySmall,
            color = backdropMutedText
        )
        ChronosFilledTonalButton(
            onClick = {
                if (syncing) return@ChronosFilledTonalButton
                syncing = true
                scope.launch {
                    status = runCatching { provider.syncNow() }.getOrNull() ?: status
                    syncing = false
                    showMessage(
                        when {
                            status?.watchAppInstalled == true -> "Synced to watch"
                            status?.watchConnected == true -> "Watch app not installed"
                            else -> "No watch connected"
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (syncing) "Syncing…" else "Sync watch now")
        }
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
                ChronosFilledTonalButton(
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
            .chronosHapticClick(
                onClick = onClick,
                onClickLabel = reviewMetricActionLabel(label),
                role = Role.Button
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
