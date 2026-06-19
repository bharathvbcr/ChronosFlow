package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.motion.chronosPressScale
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import com.ChronosFlow.VBCR.core.ui.theme.GlassElevation
import com.ChronosFlow.VBCR.core.ui.theme.GlassTone
import com.ChronosFlow.VBCR.core.ui.theme.chronosGlass

/**
 * Single card surface behind ChronosListCard, ChronosMetricTile, ChronosActionTile and
 * ChronosEmptyState. Renders liquid glass when the user has glass surfaces enabled
 * (and high contrast off); otherwise falls back to the opaque Material container.
 *
 * Clickable cards get the full tactile treatment: ripple, press-scale and a haptic tick.
 */
@Composable
fun ChronosCardSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val settings = rememberChronosUiSettings()
    val useGlass = settings.glassSurfacesEnabled && !settings.highContrastEnabled
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    var surfaceModifier = modifier
    if (onClick != null) {
        surfaceModifier = surfaceModifier.chronosPressScale(interactionSource, settings.reduceMotionEnabled)
    }
    surfaceModifier = if (useGlass) {
        surfaceModifier.chronosGlass(
            shape = shape,
            tone = GlassTone.STANDARD,
            elevation = GlassElevation.LOW
        )
    } else {
        surfaceModifier
            .clip(shape)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = if (enabled) 1f else 0.6f
                )
            )
    }
    if (onClick != null) {
        surfaceModifier = surfaceModifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        }
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier = surfaceModifier) {
            content()
        }
    }
}
