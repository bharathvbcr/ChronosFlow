package com.ChronosFlow.VBCR.core.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Haze state for the shell content layer. The navigation shell provides this after
 * marking its content with `hazeSource`; chrome surfaces (bars, pills) consume it to
 * render real backdrop blur. Null when no blur source is available (e.g. dialogs,
 * separate windows, tests) — consumers fall back to the faux [chronosGlass] look.
 */
val LocalChronosHazeState = compositionLocalOf<HazeState?> { null }

/**
 * iOS-style frosted glass: real backdrop blur (Android 12+; scrim fallback below) tinted
 * with the Material You surface color, plus the Chronos vibrancy border. Falls back to
 * the translucency-only [chronosGlass] when no [LocalChronosHazeState] source is present.
 *
 * Must only be applied to nodes that are NOT children of the `hazeSource` layer.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.chronosFrostedGlass(
    shape: Shape,
    tone: GlassTone = GlassTone.STANDARD,
    elevation: GlassElevation = GlassElevation.MEDIUM
): Modifier {
    val hazeState = LocalChronosHazeState.current
    val isHighContrast = LocalFocusAwareColorState.current.isHighContrast
    if (hazeState == null || isHighContrast) {
        return chronosGlass(shape = shape, tone = tone, elevation = elevation)
    }
    val colors = MaterialTheme.colorScheme
    val style = when (tone) {
        GlassTone.QUIET -> HazeMaterials.ultraThin(colors.surfaceContainerHigh)
        GlassTone.STANDARD -> HazeMaterials.thin(colors.surfaceContainerHigh)
        GlassTone.PROMINENT -> HazeMaterials.regular(colors.surfaceContainerHigh)
    }
    return this
        .clip(shape)
        .hazeEffect(state = hazeState, style = style)
        .border(
            width = elevation.borderWidth,
            brush = ChronosGlassTokens.borderBrush(colors.primary),
            shape = shape
        )
}
