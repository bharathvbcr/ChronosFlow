package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosListCard
import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import com.ChronosFlow.VBCR.feature.daydial.model.DailyActionKind
import com.ChronosFlow.VBCR.feature.daydial.model.DailyActionUiModel

@Composable
internal fun DailyActionStrip(
    action: DailyActionUiModel,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // Use the shared card surface (same 16dp radius, surface tone, and glass/high-contrast
    // adaptation as every other Today card) instead of a bespoke Surface — the filled primary
    // button is what marks this as the CTA, so the container no longer needs to look different.
    ChronosListCard(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
            ) {
                ChronosButton(
                    onClick = onPrimary,
                    modifier = Modifier.weight(1.2f),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(action.primaryLabel, fontWeight = FontWeight.SemiBold)
                }
                action.secondaryLabel?.let { secondaryLabel ->
                    ChronosOutlinedButton(
                        onClick = { onSecondary?.invoke() },
                        enabled = onSecondary != null,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(secondaryLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

internal fun dailyActionPrimaryHandler(
    action: DailyActionUiModel,
    activeBlockId: String?,
    nextBlockId: String?,
    onPlanDay: () -> Unit,
    onAddBlock: (Int) -> Unit,
    currentMinute: Int,
    onStartFocus: (String) -> Unit,
    onCompleteBlock: (String) -> Unit,
    onStartNext: (String) -> Unit,
    onPrepareNext: (String) -> Unit,
    onReviewMissed: () -> Unit,
    onFillGaps: () -> Unit
): () -> Unit = when (action.kind) {
    DailyActionKind.EMPTY_DAY -> onPlanDay
    DailyActionKind.ACTIVE_BLOCK -> {
        { activeBlockId?.let(onStartFocus) }
    }
    DailyActionKind.UPCOMING_BLOCK -> {
        { nextBlockId?.let(onStartNext) }
    }
    DailyActionKind.MISSED_BLOCKS -> onReviewMissed
    DailyActionKind.OPEN_TIME -> { { onAddBlock(currentMinute) } }
}

internal fun dailyActionSecondaryHandler(
    action: DailyActionUiModel,
    activeBlockId: String?,
    nextBlockId: String?,
    onAddBlock: (Int) -> Unit,
    currentMinute: Int,
    onCompleteBlock: (String) -> Unit,
    onPrepareNext: (String) -> Unit,
    onFillGaps: () -> Unit,
    onReflowDay: () -> Unit
): (() -> Unit)? = when (action.kind) {
    DailyActionKind.EMPTY_DAY -> ({ onAddBlock(currentMinute) })
    DailyActionKind.ACTIVE_BLOCK -> activeBlockId?.let { id -> ({ onCompleteBlock(id) }) }
    DailyActionKind.UPCOMING_BLOCK -> nextBlockId?.let { id -> ({ onPrepareNext(id) }) }
    DailyActionKind.MISSED_BLOCKS -> onReflowDay
    DailyActionKind.OPEN_TIME -> ({ onFillGaps() })
}
