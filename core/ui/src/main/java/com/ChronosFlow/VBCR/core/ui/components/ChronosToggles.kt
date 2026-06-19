package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Canonical toggles for ChronosFlow. Drop-in replacements for Material 3 [Switch] and [Checkbox]
 * that fire the same `Confirm` haptic tick as the rest of the tappable family on each flip (see
 * docs/UX_PRINCIPLES.md). A null `onCheckedChange` stays display-only and silent — use it when an
 * enclosing row owns the click (e.g. [ChronosSettingsRow], a [ChronosDropdownMenuItem]).
 */
@Composable
fun ChronosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = haptics.confirmToggle(onCheckedChange),
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun ChronosCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    Checkbox(
        checked = checked,
        onCheckedChange = haptics.confirmToggle(onCheckedChange),
        modifier = modifier,
        enabled = enabled
    )
}
