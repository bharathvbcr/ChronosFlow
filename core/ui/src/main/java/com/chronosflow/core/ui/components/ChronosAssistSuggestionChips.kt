package com.chronosflow.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Shared pill row for AI assist suggestions across the Task, Medication, and Habit forms.
 * Renders a progress row while generation is in flight so the sheet never looks idle,
 * and offers a one-tap "Apply all" shortcut once multiple suggestions are available.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChronosAssistSuggestionChips(
    suggestions: List<T>,
    isLoading: Boolean,
    onApply: (T) -> Unit,
    label: (T) -> String,
    reason: (T) -> String,
    sourceLabel: (T) -> String,
    modifier: Modifier = Modifier,
    loadingLabel: String = "Drafting suggestions…",
    onApplyAll: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = loadingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (suggestions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    val suggestionLabel = label(suggestion)
                    val suggestionReason = reason(suggestion)
                    val suggestionSource = sourceLabel(suggestion)
                    FilterChip(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription =
                                "Apply suggestion: $suggestionLabel. $suggestionSource. $suggestionReason"
                        },
                        selected = false,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onApply(suggestion)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        label = {
                            Column {
                                Text(suggestionLabel)
                                Text(
                                    text = "$suggestionSource • $suggestionReason",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
                if (onApplyAll != null && suggestions.size >= 2) {
                    AssistChip(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onApplyAll()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Apply all ${suggestions.size}") }
                    )
                }
            }
        }
    }
}
