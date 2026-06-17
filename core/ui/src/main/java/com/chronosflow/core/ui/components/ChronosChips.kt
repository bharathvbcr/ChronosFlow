package com.chronosflow.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback
import com.chronosflow.core.ui.motion.chronosPressScale
import com.chronosflow.core.ui.settings.rememberChronosUiSettings

/**
 * Canonical chips for ChronosFlow. Drop-in replacements for the Material 3 [FilterChip] and
 * [AssistChip] that additionally carry the signature press-scale spring and a `Confirm` haptic
 * tick on tap — the same tactile contract the [ChronosButton] family provides (see
 * docs/UX_PRINCIPLES.md). Chips are tappable buttons, so they tick too.
 *
 * Only the parameters call sites actually use are exposed; the version-sensitive
 * `border`/`elevation` defaults stay inside Material. `shape` is forwarded (defaulting to
 * Material's [FilterChipDefaults.shape]) so callers can opt into a tighter chip silhouette.
 */
@Composable
fun ChronosFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val haptics = LocalHapticFeedback.current
    FilterChip(
        selected = selected,
        onClick = haptics.confirmThen(onClick),
        label = label,
        modifier = modifier.chronosPressScale(source, reduceMotion),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        interactionSource = source,
    )
}

@Composable
fun ChronosAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val haptics = LocalHapticFeedback.current
    AssistChip(
        onClick = haptics.confirmThen(onClick),
        label = label,
        modifier = modifier.chronosPressScale(source, reduceMotion),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = colors,
        interactionSource = source,
    )
}
