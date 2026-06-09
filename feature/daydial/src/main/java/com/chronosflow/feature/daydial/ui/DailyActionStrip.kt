package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.feature.daydial.model.DailyActionKind
import com.chronosflow.feature.daydial.model.DailyActionUiModel

@Composable
internal fun DailyActionStrip(
    action: DailyActionUiModel,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(action.primaryLabel, fontWeight = FontWeight.SemiBold)
                }
                action.secondaryLabel?.let { secondaryLabel ->
                    OutlinedButton(
                        onClick = { onSecondary?.invoke() },
                        enabled = onSecondary != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
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
    onFillGaps: () -> Unit
): (() -> Unit)? = when (action.kind) {
    DailyActionKind.EMPTY_DAY -> ({ onAddBlock(currentMinute) })
    DailyActionKind.ACTIVE_BLOCK -> activeBlockId?.let { id -> ({ onCompleteBlock(id) }) }
    DailyActionKind.UPCOMING_BLOCK -> nextBlockId?.let { id -> ({ onPrepareNext(id) }) }
    DailyActionKind.MISSED_BLOCKS -> null
    DailyActionKind.OPEN_TIME -> ({ onFillGaps() })
}
