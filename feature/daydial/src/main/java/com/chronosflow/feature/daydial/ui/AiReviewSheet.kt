package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.chronosflow.feature.daydial.TimeBlockUiModel

@Composable
fun AiReviewSheet(
    suggestions: List<TimeBlockUiModel>,
    onApplyAll: () -> Unit,
    onDismissAll: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onModify: (String, String, Int, Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Review suggestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismissAll,
                    modifier = Modifier.semantics {
                        contentDescription = aiReviewDismissAllActionLabel(suggestions.size)
                    },
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Dismiss All") }
                Button(
                    onClick = onApplyAll,
                    modifier = Modifier.semantics {
                        contentDescription = aiReviewApplyAllActionLabel(suggestions.size)
                    },
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Apply All") }
            }
        }
        suggestions.forEach { suggestion ->
            var title by rememberSaveable(suggestion.id) { mutableStateOf(suggestion.title) }
            var start by rememberSaveable(suggestion.id) { mutableStateOf(formatReviewMinute(suggestion.startMinuteOfDay)) }
            var duration by rememberSaveable(suggestion.id) { mutableStateOf(suggestion.durationMinutes.toString()) }

            val diff = rememberAiSuggestionDiff(suggestion)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = diff.containerColor)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DiffPill(label = diff.label, color = diff.accentColor)
                        Text(diff.summary, style = MaterialTheme.typography.labelMedium, color = diff.accentColor)
                    }
                    Text(
                        "${formatReviewMinute(suggestion.startMinuteOfDay)} - ${formatReviewMinute(suggestion.startMinuteOfDay + suggestion.durationMinutes)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        diff.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = start,
                            onValueChange = { start = it },
                            label = { Text("Start") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            label = { Text("Minutes") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onReject(suggestion.id) },
                            modifier = Modifier.semantics {
                                contentDescription = aiReviewRejectActionLabel(suggestion)
                            }
                        ) { Text("Reject") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                onModify(
                                    suggestion.id,
                                    title,
                                    parseReviewMinute(start) ?: suggestion.startMinuteOfDay,
                                    duration.toIntOrNull() ?: suggestion.durationMinutes
                                )
                            },
                            modifier = Modifier.semantics {
                                contentDescription = aiReviewModifyActionLabel(suggestion)
                            },
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("Modify") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onAccept(suggestion.id) },
                            modifier = Modifier.semantics {
                                contentDescription = aiReviewAcceptActionLabel(suggestion)
                            },
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("Accept") }
                    }
                }
            }
        }
    }
}

internal fun aiReviewDismissAllActionLabel(suggestionCount: Int): String =
    "Dismiss $suggestionCount AI suggestion${if (suggestionCount == 1) "" else "s"}"

internal fun aiReviewApplyAllActionLabel(suggestionCount: Int): String =
    planSuggestionApplyAllActionLabel(suggestionCount)

internal fun aiReviewRejectActionLabel(suggestion: TimeBlockUiModel): String =
    planSuggestionRejectActionLabel(suggestion)

internal fun aiReviewModifyActionLabel(suggestion: TimeBlockUiModel): String =
    "Modify ${suggestion.title} suggestion ${aiReviewSuggestionTimeDetail(suggestion)}"

internal fun aiReviewAcceptActionLabel(suggestion: TimeBlockUiModel): String =
    planSuggestionAcceptActionLabel(suggestion)

private fun aiReviewSuggestionTimeDetail(suggestion: TimeBlockUiModel): String =
    "from ${formatReviewActionMinute(suggestion.startMinuteOfDay)} " +
        "for ${formatReviewActionDuration(suggestion.durationMinutes)}"

private fun formatReviewActionMinute(minute: Int): String {
    val normalized = ((minute % 1440) + 1440) % 1440
    val hour = normalized / 60
    val minutePart = normalized % 60
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return "%d:%02d %s".format(displayHour, minutePart, suffix)
}

private fun formatReviewActionDuration(durationMinutes: Int): String =
    "$durationMinutes minute${if (durationMinutes == 1) "" else "s"}"

private data class AiSuggestionDiff(
    val label: String,
    val summary: String,
    val explanation: String,
    val accentColor: Color,
    val containerColor: Color
)

@Composable
private fun rememberAiSuggestionDiff(suggestion: TimeBlockUiModel): AiSuggestionDiff {
    val isProtectedFocus = suggestion.isProtected || suggestion.title.contains("focus", ignoreCase = true)
    val isBreakOrRecovery = suggestion.title.contains("break", ignoreCase = true) ||
        suggestion.title.contains("recover", ignoreCase = true)
    val isShortBuffer = suggestion.durationMinutes <= 20
    val accent = when {
        isProtectedFocus -> Color(0xFF3F7E44)
        isBreakOrRecovery || isShortBuffer -> Color(0xFF8A5D00)
        suggestion.flexibility == "OPTIONAL" -> Color(0xFF3859B8)
        else -> MaterialTheme.colorScheme.primary
    }
    val label = when {
        isProtectedFocus -> "Protect"
        isBreakOrRecovery || isShortBuffer -> "Buffer"
        suggestion.flexibility == "OPTIONAL" -> "Add"
        else -> "Adjust"
    }
    val explanation = when (label) {
        "Protect" -> "Adds a protected work window that should resist automatic rebalance changes."
        "Buffer" -> "Adds recovery time to reduce overlap risk and make the surrounding plan easier to keep."
        "Add" -> "Adds an optional AI-suggested block without changing the existing timeline until accepted."
        else -> "Adjusts the proposed timing while preserving the suggestion for review before it is applied."
    }
    return AiSuggestionDiff(
        label = label,
        summary = "${suggestion.durationMinutes}m at ${formatReviewMinute(suggestion.startMinuteOfDay)}",
        explanation = explanation,
        accentColor = accent,
        containerColor = accent.copy(alpha = 0.08f)
    )
}

@Composable
private fun DiffPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatReviewMinute(minute: Int): String {
    val normalized = ((minute % 1440) + 1440) % 1440
    val hour = normalized / 60
    val minutePart = normalized % 60
    return "${hour.toString().padStart(2, '0')}:${minutePart.toString().padStart(2, '0')}"
}

private fun parseReviewMinute(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
}
