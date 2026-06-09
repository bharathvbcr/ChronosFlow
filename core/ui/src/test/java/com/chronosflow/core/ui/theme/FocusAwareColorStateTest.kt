package com.chronosflow.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusAwareColorStateTest {

    @Test
    fun `dial accent color is base when accents are disabled`() {
        val base = Color(0.20f, 0.40f, 0.60f, 1f)
        val state = FocusAwareColorState(
            focusAwareAccentsEnabled = false,
            energyScore = 5,
            moodScore = 1
        )
        val output = state.dialAccentColor(base)
        assertEquals(base, output)
    }

    @Test
    fun `dial accent color is unchanged for high contrast mode`() {
        val base = Color(0.20f, 0.40f, 0.60f, 1f)
        val state = FocusAwareColorState(
            isHighContrast = true,
            energyScore = 5,
            moodScore = 1
        )
        val output = state.dialAccentColor(base)
        assertEquals(base, output)
    }

    @Test
    fun `dial accent color blends with mood and energy`() {
        val base = Color(0.20f, 0.40f, 0.60f, 1f)
        val state = FocusAwareColorState(energyScore = 2, moodScore = 4)

        val output = state.dialAccentColor(base)
        val blend = ((2 + 4) / 10f).coerceIn(0.08f, 0.22f)
        val expected = Color(
            red = base.red * (1f - blend) + 0.45f * blend,
            green = base.green * (1f - blend) + 0.72f * blend,
            blue = base.blue * (1f - blend) + 0.88f * blend,
            alpha = base.alpha
        )
        assertEquals(expected.toArgb(), output.toArgb())
    }

    @Test
    fun `selected block outline alpha tracks focus state`() {
        assertEquals(1f, FocusAwareColorState(isHighContrast = true).selectedBlockOutlineAlpha(), 0.0f)
        assertEquals(0.95f, FocusAwareColorState(activeBlockId = "abc").selectedBlockOutlineAlpha(), 0.0f)
        assertEquals(0.72f, FocusAwareColorState(activeBlockId = null).selectedBlockOutlineAlpha(), 0.0f)
    }
}
