package com.chronosflow.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ai.genai.RewriteAssistUiState
import com.chronosflow.core.ai.genai.RewriteStyle

/** One rewrite action offered on a free-text field. */
data class ChronosRewriteOption(
    val label: String,
    val style: RewriteStyle,
    val hint: String
)

val ChronosDefaultRewriteOptions: List<ChronosRewriteOption> = listOf(
    ChronosRewriteOption("Shorten", RewriteStyle.SHORTEN, "Tighten the wording"),
    ChronosRewriteOption("Polish", RewriteStyle.PROFESSIONAL, "Clean, neutral tone"),
    ChronosRewriteOption("Expand", RewriteStyle.ELABORATE, "Add helpful detail")
)

const val CHRONOS_MIN_REWRITE_LENGTH = 12

/**
 * Shared on-device rewrite actions for a free-text form field (Task description, Medication
 * notes). Style chips request an ML Kit GenAI rewrite; the result is shown as a preview the
 * user explicitly applies — typed text is never replaced automatically. The preview hides
 * itself if the field was edited after the request so a stale rewrite can't overwrite newer
 * wording.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChronosTextRewriteRow(
    text: String,
    rewriteState: RewriteAssistUiState,
    onRequestRewrite: (text: String, style: RewriteStyle, styleLabel: String) -> Unit,
    onApplyRewrite: (String) -> Unit,
    onDismissRewrite: () -> Unit,
    modifier: Modifier = Modifier,
    fieldName: String = "description",
    options: List<ChronosRewriteOption> = ChronosDefaultRewriteOptions,
    minLength: Int = CHRONOS_MIN_REWRITE_LENGTH
) {
    val canRewrite = text.trim().length >= minLength
    val hasRewriteActivity = rewriteState.isLoading ||
        rewriteState.rewritten != null ||
        rewriteState.message != null
    if (!canRewrite && !hasRewriteActivity) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (canRewrite) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    AssistChip(
                        onClick = { onRequestRewrite(text, option.style, option.label) },
                        enabled = !rewriteState.isLoading,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        label = { Text(option.label) },
                        modifier = Modifier.semantics {
                            contentDescription =
                                "Rewrite $fieldName on-device: ${option.label}. ${option.hint}."
                        }
                    )
                }
            }
        }
        if (rewriteState.isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Rewriting on-device${rewriteState.styleLabel?.let { " ($it)" }.orEmpty()}…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        rewriteState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val rewritten = rewriteState.rewritten
        if (rewritten != null && rewriteState.original == text) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${rewriteState.styleLabel ?: "Rewritten"} draft — your text stays until you use it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = rewritten,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onApplyRewrite(rewritten) }) {
                            Text("Use this wording")
                        }
                        TextButton(onClick = onDismissRewrite) {
                            Text("Keep mine")
                        }
                    }
                }
            }
        }
    }
}
