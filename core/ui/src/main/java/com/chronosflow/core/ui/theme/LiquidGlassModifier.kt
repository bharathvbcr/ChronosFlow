package com.chronosflow.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassTone(val alpha: Float, val blurPx: Float) {
    QUIET(0.72f, 8f),
    STANDARD(0.84f, 16f),
    PROMINENT(0.92f, 24f)
}

enum class GlassElevation(val borderWidth: Dp) {
    LOW(0.7.dp),
    MEDIUM(1.0.dp),
    HIGH(1.5.dp)
}

@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp = 28.dp,
    blur: Dp = ChronosGlassTokens.StandardBlur
): Modifier = chronosGlass(
    shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
    tone = GlassTone.STANDARD.copyForBlur(blur),
    elevation = GlassElevation.HIGH
)

@Composable
fun Modifier.chronosGlass(
    shape: Shape,
    tone: GlassTone = GlassTone.STANDARD,
    elevation: GlassElevation = GlassElevation.MEDIUM
): Modifier {
    val colors = MaterialTheme.colorScheme
    val isHighContrast = LocalFocusAwareColorState.current.isHighContrast
    val container = if (isHighContrast) {
        colors.surfaceContainerHigh
    } else {
        colors.surfaceContainerHigh.copy(alpha = tone.alpha)
    }
    return this
        .clip(shape)
        .background(container)
        .border(
            width = if (isHighContrast) 2.dp else elevation.borderWidth,
            brush = if (isHighContrast) {
                androidx.compose.ui.graphics.SolidColor(colors.outline)
            } else {
                ChronosGlassTokens.borderBrush(colors.primary)
            },
            shape = shape
        )
}

@Composable
fun chronosGlassContentColor(container: Color): Color {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    return if (contrastRatio(onSurface, container) >= 4.5f) onSurface else onSurfaceVariant
}

private fun GlassTone.copyForBlur(blur: Dp): GlassTone {
    return when {
        blur <= 8.dp -> GlassTone.QUIET
        blur >= 24.dp -> GlassTone.PROMINENT
        else -> GlassTone.STANDARD
    }
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.relativeLuminance(), background.relativeLuminance())
    val darker = minOf(foreground.relativeLuminance(), background.relativeLuminance())
    return ((lighter + 0.05f) / (darker + 0.05f))
}

private fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float {
        return if (value <= 0.03928f) {
            value / 12.92f
        } else {
            Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
    }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
