package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.ui.components.ChronosSpeechInputButton
import com.chronosflow.core.ui.theme.ChronosSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class JournalPrompt(val key: String, val label: String, val question: String)

internal val JournalPrompts = listOf(
    JournalPrompt("went_well", "Went well", "What went well today?"),
    JournalPrompt("drained", "Drained me", "What drained you today?"),
    JournalPrompt("tomorrow", "Tomorrow", "One thing to make tomorrow better?")
)

private val journalDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

@Composable
internal fun JournalEntrySheetContent(
    date: LocalDate,
    existing: JournalEntry?,
    moodSummary: String?,
    onSave: (body: String, promptType: String?) -> Unit
) {
    var body by rememberSaveable(existing?.id) { mutableStateOf(existing?.body.orEmpty()) }
    var promptType by rememberSaveable(existing?.id) { mutableStateOf(existing?.promptType) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        Text(
            text = "Evening journal",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = date.format(journalDateFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        moodSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
        ) {
            JournalPrompts.forEach { prompt ->
                FilterChip(
                    selected = promptType == prompt.key,
                    onClick = {
                        promptType = if (promptType == prompt.key) null else prompt.key
                        if (body.isBlank() && promptType == prompt.key) {
                            body = "${prompt.question}\n"
                        }
                    },
                    label = { Text(prompt.label) }
                )
            }
        }
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("How did today go?") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChronosSpeechInputButton(
                prompt = "Dictate your reflection",
                onTranscript = { transcript ->
                    body = listOf(body.trim(), transcript.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
            )
            Button(
                onClick = { onSave(body, promptType) },
                enabled = body.isNotBlank()
            ) {
                Text(if (existing == null) "Save entry" else "Update entry", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
