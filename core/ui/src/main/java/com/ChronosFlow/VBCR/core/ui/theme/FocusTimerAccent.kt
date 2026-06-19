package com.ChronosFlow.VBCR.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings

/**
 * Blends category color with mood/energy-aware dial accent for focus timer rings.
 */
@Composable
fun rememberFocusTimerAccent(
    blockCategory: String?,
    blockId: String? = null,
    moodScore: Int? = null,
    energyScore: Int? = null
): Color {
    val settings = rememberChronosUiSettings()
    val scheme = MaterialTheme.colorScheme
    val categoryTint = blockCategory?.let { categoryColor(it) }
    val focusAware = FocusAwareColorState(
        activeBlockId = blockId,
        activeBlockCategory = blockCategory,
        moodScore = moodScore,
        energyScore = energyScore,
        isHighContrast = settings.highContrastEnabled,
        isReducedMotion = settings.reduceMotionEnabled,
        dynamicColorEnabled = !settings.highContrastEnabled,
        focusAwareAccentsEnabled = true
    )
    val moodTint = focusAware.dialAccentColor(scheme.primary)
    if (settings.highContrastEnabled) {
        return categoryTint ?: scheme.primary
    }
    if (categoryTint == null) return moodTint
    val blend = 0.28f
    return Color(
        red = categoryTint.red * (1f - blend) + moodTint.red * blend,
        green = categoryTint.green * (1f - blend) + moodTint.green * blend,
        blue = categoryTint.blue * (1f - blend) + moodTint.blue * blend,
        alpha = categoryTint.alpha
    )
}
