package com.ChronosFlow.VBCR.core.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings

/**
 * iOS-style press feedback: scales the surface down while pressed and springs
 * back on release. Interruptible, so quick taps and cancelled presses stay fluid.
 *
 * Pair with the same [InteractionSource] passed to `clickable`/`Button`.
 */
@Composable
fun Modifier.chronosPressScale(
    interactionSource: InteractionSource,
    reduceMotionEnabled: Boolean = false,
    pressedScale: Float = ChronosMotionDefaults.PressedScale
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = ChronosValueAnimationFactory.pressScale(reduceMotionEnabled),
        label = "chronosPressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Tactile click: ripple + press-scale + a Confirm haptic tick on release.
 * Apply after a `clip()` so the ripple stays inside the surface shape.
 */
@Composable
fun Modifier.chronosHapticClick(
    onClick: () -> Unit,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val reduceMotionEnabled = rememberChronosUiSettings().reduceMotionEnabled
    return this
        .chronosPressScale(source, reduceMotionEnabled)
        .clickable(
            interactionSource = source,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        }
}
