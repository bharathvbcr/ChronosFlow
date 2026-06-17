package com.chronosflow.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.chronosflow.core.ui.motion.chronosPressScale
import com.chronosflow.core.ui.settings.rememberChronosUiSettings

/**
 * Canonical buttons for ChronosFlow. They are drop-in replacements for the Material 3
 * buttons (same parameters and defaults) that additionally carry the signature
 * iOS-style press-scale: the surface dips on press and springs back on release, in lockstep
 * with the Material ripple. Reduced motion collapses the spring to a snap.
 *
 * Every click also emits a `Confirm` haptic tick so taps feel tactile app-wide — the same
 * cue `chronosHapticClick` gives bespoke tappable surfaces. Because the haptic lives here,
 * call sites must NOT add their own `performHapticFeedback` (that would double-tick).
 *
 * Per the UX principles every standalone button must go through one of these — raw Material
 * `Button`/`TextButton`/… in feature/app UI is rejected by ChronosUxPrinciplesAuditTest.
 */
internal fun HapticFeedback.confirmThen(onClick: () -> Unit): () -> Unit = {
    performHapticFeedback(HapticFeedbackType.Confirm)
    onClick()
}

/**
 * Toggle counterpart of [confirmThen]: wraps an `onCheckedChange` so each flip fires the shared
 * `Confirm` tick. Null in → null out, so display-only (`onCheckedChange = null`) toggles stay
 * non-interactive and silent.
 */
internal fun HapticFeedback.confirmToggle(
    onCheckedChange: ((Boolean) -> Unit)?
): ((Boolean) -> Unit)? = onCheckedChange?.let { callback ->
    { checked: Boolean ->
        performHapticFeedback(HapticFeedbackType.Confirm)
        callback(checked)
    }
}

@Composable
fun ChronosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = haptics.confirmThen(onClick),
        modifier = modifier.chronosPressScale(source, reduceMotion),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun ChronosFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val haptics = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = haptics.confirmThen(onClick),
        modifier = modifier.chronosPressScale(source, reduceMotion),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun ChronosOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val haptics = LocalHapticFeedback.current
    OutlinedButton(
        onClick = haptics.confirmThen(onClick),
        modifier = modifier.chronosPressScale(source, reduceMotion),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun ChronosElevatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.elevatedShape,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.elevatedButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val haptics = LocalHapticFeedback.current
    ElevatedButton(
        onClick = haptics.confirmThen(onClick),
        modifier = modifier.chronosPressScale(source, reduceMotion),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun ChronosTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val haptics = LocalHapticFeedback.current
    TextButton(
        onClick = haptics.confirmThen(onClick),
        modifier = modifier.chronosPressScale(source, reduceMotion),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

/**
 * Icon button with the same press-scale spring. Use for standalone icon actions; for the
 * tooltip + content-description variant prefer [ChronosTooltipIconButton].
 */
@Composable
fun ChronosIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = haptics.confirmThen(onClick),
        modifier = modifier.chronosPressScale(source, reduceMotion),
        enabled = enabled,
        colors = colors,
        interactionSource = source,
        content = content
    )
}

/** Convenience overload: icon-only button that springs on press. */
@Composable
fun ChronosIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ChronosIconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/**
 * Tonal icon button with the same press-scale spring and `Confirm` haptic as the rest of the
 * family. Use for secondary icon actions that need a filled affordance (duplicate, delete, …).
 */
@Composable
fun ChronosFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val haptics = LocalHapticFeedback.current
    FilledTonalIconButton(
        onClick = haptics.confirmThen(onClick),
        modifier = modifier.chronosPressScale(source, reduceMotion),
        enabled = enabled,
        colors = colors,
        interactionSource = source,
        content = content
    )
}
