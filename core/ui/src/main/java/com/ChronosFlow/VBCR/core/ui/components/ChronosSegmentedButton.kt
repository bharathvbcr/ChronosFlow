package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Canonical single-choice segmented button. Drop-in for the Material 3 [SegmentedButton] that
 * fires the same `Confirm` haptic tick as the rest of the tappable family on selection (see
 * docs/UX_PRINCIPLES.md). Segments are selection buttons, so they tick too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleChoiceSegmentedButtonRowScope.ChronosSegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    SegmentedButton(
        selected = selected,
        onClick = haptics.confirmThen(onClick),
        shape = shape,
        modifier = modifier,
        enabled = enabled,
        label = label
    )
}
