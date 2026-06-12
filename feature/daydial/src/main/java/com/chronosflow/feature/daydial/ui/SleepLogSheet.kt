package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.chronosflow.core.domain.model.SleepTrack
import com.chronosflow.core.ui.components.ChronosTimePickerField
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.core.ui.theme.ChronosSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sleepDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private const val UNSET_MINUTE = -1

@Composable
internal fun SleepLogSheetContent(
    date: LocalDate,
    existing: SleepTrack?,
    onSave: (quality: Int, actualStartMinute: Int?, actualEndMinute: Int?, interruptions: Int, notes: String?) -> Unit
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
    var interruptions by rememberSaveable(existing?.id) {
        mutableIntStateOf(existing?.interruptedCount ?: 0)
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
        Text(
            text = "Quality",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
            (1..5).forEach { level ->
                FilterChip(
                    selected = quality == level,
                    onClick = { quality = level },
                    label = { Text(level.toString()) }
                )
            }
        }
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            Text(
                text = "Interruptions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { interruptions = (interruptions - 1).coerceAtLeast(0) },
                enabled = interruptions > 0
            ) { Text("−") }
            Text(text = interruptions.toString(), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { interruptions += 1 }) { Text("+") }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Wind-down notes (optional)") }
        )
        Button(
            onClick = {
                onSave(
                    quality,
                    bedMinute.takeIf { it != UNSET_MINUTE },
                    wakeMinute.takeIf { it != UNSET_MINUTE },
                    interruptions,
                    notes.takeIf { it.isNotBlank() }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (existing == null) "Save sleep" else "Update sleep", fontWeight = FontWeight.SemiBold)
        }
    }
}
