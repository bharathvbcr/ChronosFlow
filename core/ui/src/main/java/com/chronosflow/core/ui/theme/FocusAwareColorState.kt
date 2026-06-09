package com.chronosflow.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Accent inputs for dial and focus surfaces. Mood/energy only tint tertiary accents;
 * dynamic color remains the base palette from [ChronosTheme].
 */
data class FocusAwareColorState(
    val activeBlockId: String? = null,
    val activeBlockCategory: String? = null,
    val energyScore: Int? = null,
    val moodScore: Int? = null,
    val focusScore: Int? = null,
    val isHighContrast: Boolean = false,
    val isReducedMotion: Boolean = false,
    val dynamicColorEnabled: Boolean = true,
    val focusAwareAccentsEnabled: Boolean = true
) {
    fun dialAccentColor(basePrimary: Color): Color {
        if (!focusAwareAccentsEnabled || isHighContrast) return basePrimary
        val energy = energyScore ?: return basePrimary
        val mood = moodScore ?: 3
        val blend = ((energy.coerceIn(1, 5) + mood.coerceIn(1, 5)) / 10f).coerceIn(0.08f, 0.22f)
        return Color(
            red = basePrimary.red * (1f - blend) + 0.45f * blend,
            green = basePrimary.green * (1f - blend) + 0.72f * blend,
            blue = basePrimary.blue * (1f - blend) + 0.88f * blend,
            alpha = basePrimary.alpha
        )
    }

    fun selectedBlockOutlineAlpha(): Float = when {
        isHighContrast -> 1f
        activeBlockId != null -> 0.95f
        else -> 0.72f
    }
}
