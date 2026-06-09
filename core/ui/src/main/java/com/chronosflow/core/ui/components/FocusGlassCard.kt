package com.chronosflow.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * List-style card for focus surfaces. [ChronosListCard] is glass-aware, so this is
 * now a plain alias kept for source compatibility with existing call sites.
 */
@Composable
fun FocusGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    ChronosListCard(modifier = modifier) {
        content()
    }
}

/** Shared label when a linked Day Dial block was marked missed. */
@Composable
fun FocusMissedBadge(modifier: Modifier = Modifier) {
    Text(
        text = "Marked missed",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}
