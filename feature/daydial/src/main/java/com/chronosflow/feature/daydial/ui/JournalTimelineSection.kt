package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.theme.ChronosSpacing
import java.time.format.DateTimeFormatter
import java.util.Locale

private val journalTimelineDateFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

private val journalPromptLabels: Map<String, String> =
    JournalPrompts.associate { it.key to it.label }

/**
 * A card list of recent journal entries, newest first. Shown on the evening
 * companion surfaces and reused by the Insights tab.
 */
@Composable
internal fun JournalTimelineSection(
    entries: List<JournalEntry>,
    modifier: Modifier = Modifier,
    title: String = "Recent reflections",
    maxEntries: Int = 7
) {
    val recent = entries
        .sortedByDescending { it.entryDate }
        .take(maxEntries.coerceAtLeast(1))

    ChronosSectionTitle(title = title)
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        if (recent.isEmpty()) {
            Text(
                text = "No journal entries yet. Capture how a day went from the evening companion.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                recent.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    JournalTimelineEntryRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun JournalTimelineEntryRow(entry: JournalEntry) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.entryDate.format(journalTimelineDateFormatter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            entry.promptType?.let { prompt ->
                journalPromptLabels[prompt]?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Text(
            text = entry.body.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4
        )
    }
}
