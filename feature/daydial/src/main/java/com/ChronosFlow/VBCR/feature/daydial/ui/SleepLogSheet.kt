package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.domain.model.SleepTrack
import com.ChronosFlow.VBCR.feature.daydial.HealthConnectSleepSyncButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosTimePickerField
import com.ChronosFlow.VBCR.core.ui.components.formatDisplayMinute
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val sleepDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private const val UNSET_MINUTE = -1
private const val MAX_INTERRUPTIONS = 10

@Composable
internal fun SleepLogSheetContent(
    date: LocalDate,
    existing: SleepTrack?,
    onSave: (
        quality: Int,
        actualStartMinute: Int?,
        actualEndMinute: Int?,
        interruptions: Int,
        notes: String?,
        refreshed: Int?
    ) -> Unit
) {
    var quality by rememberSaveable(existing?.id) {
        mutableIntStateOf(existing?.sleepQuality ?: 3)
    }
    var bedMinute by rememberSaveable(existing?.id) {
        mutableIntStateOf(existing?.actualStartMinute ?: UNSET_MINUTE)
    }
    var wakeMinute by rememberSaveable(existing?.id) {
        mutableIntStateOf(existing?.actualEndMinute ?: UNSET_MINUTE)
    }
    // Interruptions always start at zero on the add/update card so the count reflects a fresh
    // tally for the night rather than carrying over a previous entry.
    var interruptions by rememberSaveable(existing?.id) {
        mutableIntStateOf(0)
    }
    var refreshed by rememberSaveable(existing?.id) {
        mutableIntStateOf(existing?.refreshedRating ?: 3)
    }
    var notes by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.windDownNotes.orEmpty())
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        Text(
            text = "Sleep log",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Night ending ${date.format(sleepDateFormatter)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Pull last night's sleep straight from Health Connect instead of filling it in by hand;
        // the imported values flow back into the fields below.
        HealthConnectSleepSyncButton()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Quality",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${sleepQualityEmoji(quality)} $quality of 5 · ${sleepQualityLabel(quality)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = quality.toFloat(),
            onValueChange = { quality = it.roundToInt() },
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = sleepQualityContentDescription(quality) }
        )
        ChronosTimePickerField(
            label = "Bed time",
            value = bedMinute.takeIf { it != UNSET_MINUTE }?.let { formatDisplayMinute(it) } ?: "Not set",
            selectedMinute = bedMinute.takeIf { it != UNSET_MINUTE },
            onTimeSelected = { bedMinute = it }
        )
        ChronosTimePickerField(
            label = "Wake time",
            value = wakeMinute.takeIf { it != UNSET_MINUTE }?.let { formatDisplayMinute(it) } ?: "Not set",
            selectedMinute = wakeMinute.takeIf { it != UNSET_MINUTE },
            onTimeSelected = { wakeMinute = it }
        )
        val bedArg = bedMinute.takeIf { it != UNSET_MINUTE }
        val wakeArg = wakeMinute.takeIf { it != UNSET_MINUTE }
        sleepWindowLabel(bedArg, wakeArg)?.let { window ->
            Text(
                text = window,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val detail = listOfNotNull(
                sleepDurationSummary(bedArg, wakeArg),
                sleepVsRecommendedLabel(bedArg, wakeArg)
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        sleepDurationHint(bedArg, wakeArg)?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Interruptions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (interruptions >= MAX_INTERRUPTIONS) "$MAX_INTERRUPTIONS+" else interruptions.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = interruptions.toFloat().coerceIn(0f, MAX_INTERRUPTIONS.toFloat()),
            onValueChange = { interruptions = it.roundToInt() },
            valueRange = 0f..MAX_INTERRUPTIONS.toFloat(),
            steps = MAX_INTERRUPTIONS - 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = sleepInterruptionsContentDescription(interruptions) }
        )
        Text(
            text = sleepRestfulnessLabel(quality, interruptions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "How refreshed do you feel?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${sleepRefreshedEmoji(refreshed)} ${sleepRefreshedLabel(refreshed)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        RefreshedEmojiSelector(
            selected = refreshed,
            onSelect = { refreshed = it }
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Wind-down notes (optional)") }
        )
        ChronosButton(
            onClick = {
                onSave(
                    quality,
                    bedMinute.takeIf { it != UNSET_MINUTE },
                    wakeMinute.takeIf { it != UNSET_MINUTE },
                    interruptions,
                    notes.takeIf { it.isNotBlank() },
                    refreshed
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (existing == null) "Save sleep" else "Update sleep", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Row of five tappable emoji faces for the 1–5 morning-refreshment rating. */
@Composable
private fun RefreshedEmojiSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        (1..5).forEach { rating ->
            val isSelected = rating == selected
            Surface(
                onClick = { onSelect(rating) },
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = sleepRefreshedContentDescription(rating) }
            ) {
                Text(
                    text = sleepRefreshedEmoji(rating),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ChronosSpacing.Small),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
