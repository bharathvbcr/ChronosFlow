package com.chronosflow.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.motion.ChronosValueAnimationFactory
import com.chronosflow.core.ui.settings.rememberChronosUiSettings

/** A selectable item for [ChronosLinkPickerField], e.g. a goal to link a task or habit to. */
data class ChronosLinkOption(
    val id: String,
    val label: String
)

/**
 * Single-select chip field for linking a record to another entity, with a leading "None" choice.
 * Lives in core/ui so feature modules (tasks, habits) can share it without depending on each other.
 */
@Composable
fun ChronosLinkPickerField(
    label: String,
    options: List<ChronosLinkOption>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String = "None"
) {
    val haptics = LocalHapticFeedback.current
    val reduceMotionEnabled = rememberChronosUiSettings().reduceMotionEnabled
    val selectionSpec = ChronosValueAnimationFactory.selection<Color>(reduceMotionEnabled)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinkChip(
                text = noneLabel,
                isSelected = selectedId == null,
                selectionSpec = selectionSpec
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSelected(null)
            }
            options.forEach { option ->
                LinkChip(
                    text = option.label,
                    isSelected = selectedId == option.id,
                    selectionSpec = selectionSpec
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSelected(option.id)
                }
            }
        }
    }
}

@Composable
private fun LinkChip(
    text: String,
    isSelected: Boolean,
    selectionSpec: androidx.compose.animation.core.AnimationSpec<Color>,
    onClick: () -> Unit
) {
    val baseColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        animationSpec = selectionSpec,
        label = "linkChipBackground"
    )
    val borderCol by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        },
        animationSpec = selectionSpec,
        label = "linkChipBorder"
    )
    val textCol by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = selectionSpec,
        label = "linkChipText"
    )
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(baseColor)
            .border(1.dp, borderCol, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textCol
        )
    }
}
