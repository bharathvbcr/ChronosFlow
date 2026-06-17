package com.chronosflow.feature.daydial.ui

import com.chronosflow.core.ui.motion.chronosHapticClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.theme.ChronosSpacing
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CollapsedBodyLines = 4

private val journalTimelineDateFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

private val journalPromptLabels: Map<String, String> =
    JournalPrompts.associate { it.key to it.label } + (GuidedPromptType to "Guided")

/**
 * A card list of recent journal entries, newest first. Each row expands in place to reveal the full
 * reflection (read-only) so browsing history never disturbs the selected day. Shown on the Insights tab.
 */
@Composable
internal fun JournalTimelineSection(
    entries: List<JournalEntry>,
    modifier: Modifier = Modifier,
    title: String = "Recent reflections",
    maxEntries: Int = 7,
    summary: String? = null
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
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)) {
                Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
                    recent.forEachIndexed { index, entry ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        JournalTimelineEntryRow(entry = entry)
                    }
                }
                summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalTimelineEntryRow(entry: JournalEntry) {
    // Keyed by entry id so each row keeps its own expand state across recompositions/reorders.
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    // Driven by the actual text layout below: true once the collapsed body genuinely overflows, so the
    // toggle only appears when there is more to reveal — no guessing from character/line counts.
    var bodyOverflows by remember(entry.id) { mutableStateOf(false) }
    val body = entry.body.trim()
    val expandable = expanded || bodyOverflows

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (expandable) {
                    // Merge the row into one node so a screen reader reads the date + reflection together,
                    // announces collapsed/expanded state, and exposes a labelled toggle action.
                    it
                        .semantics(mergeDescendants = true) {
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        }
                        .chronosHapticClick(
                            onClick = { expanded = !expanded },
                            onClickLabel = if (expanded) "Show less" else "Show more",
                            role = Role.Button
                        )
                } else {
                    it
                }
            },
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
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else CollapsedBodyLines,
            overflow = TextOverflow.Ellipsis,
            // Capture overflow only while collapsed; expanded layout never overflows, so guarding here
            // preserves the "Show less" affordance once a row has been opened.
            onTextLayout = { result -> if (!expanded) bodyOverflows = result.hasVisualOverflow }
        )
        if (expandable) {
            Text(
                text = if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
