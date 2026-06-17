package com.chronosflow.core.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the gradient stop builders shared by every ambient backdrop
 * (Liquid/Smoke/WaterDrop/Sunset/Nebula/Aurora). A regression here — a dropped
 * transparent endpoint, an inverted falloff, or a broken alpha multiplier — would
 * reintroduce the hard edges / banding these multi-stop ramps exist to prevent.
 */
class LiquidBackdropStopsTest {
    private val color = Color(red = 0.2f, green = 0.4f, blue = 0.6f, alpha = 0.5f)
    // The source reads color.alpha, which round-trips through Color's reduced-precision
    // packing, so derive the expected base from the same value rather than the raw literal.
    private val baseAlpha = color.alpha

    @Test
    fun `glow stops fade from a brighter core to a fully transparent edge`() {
        val stops = liquidBackdropGlowStops(color)

        assertEquals(
            "Glow ramp positions must be the fixed soft-falloff fractions.",
            listOf(0.0f, 0.28f, 0.52f, 0.74f, 1.0f),
            stops.map { it.first }
        )
        assertTrue(
            "Stop positions must be strictly increasing for a valid gradient.",
            stops.map { it.first }.zipWithNext().all { (a, b) -> a < b }
        )
        assertEquals(
            "Core stop should be slightly brighter than the base alpha (capped at 1).",
            (baseAlpha * 1.2f).coerceAtMost(1f),
            stops.first().second.alpha,
            // Tolerance absorbs Color's channel-packing quantization on the scaled value.
            0.01f
        )
        assertEquals(
            "The outer edge must be fully transparent to avoid a hard ring.",
            0f,
            stops.last().second.alpha,
            0.0001f
        )
        assertTrue(
            "Alpha must decrease monotonically from the core outward.",
            stops.map { it.second.alpha }.zipWithNext().all { (a, b) -> a >= b }
        )
        assertEquals(
            "Only alpha should change — the hue must be preserved across stops.",
            color.red,
            stops[1].second.red,
            0.0001f
        )
    }

    @Test
    fun `curtain stops are symmetric with a solid mid and transparent ends`() {
        val stops = liquidBackdropCurtainStops(color)

        assertEquals(
            "Curtain ramp positions must be the fixed symmetric fractions.",
            listOf(0.0f, 0.28f, 0.5f, 0.72f, 1.0f),
            stops.map { it.first }
        )
        assertEquals("Top edge must be transparent.", 0f, stops.first().second.alpha, 0.0001f)
        assertEquals("Bottom edge must be transparent.", 0f, stops.last().second.alpha, 0.0001f)
        assertEquals(
            "Curtain should peak at full base alpha in the middle.",
            baseAlpha,
            stops[2].second.alpha,
            0.0001f
        )
        assertEquals(
            "The two shoulder stops must be symmetric in alpha.",
            stops[1].second.alpha,
            stops[3].second.alpha,
            0.0001f
        )
    }

    @Test
    fun `layer translation fraction centers on the midpoint`() {
        assertEquals(0f, liquidBackdropLayerTranslationFraction(0.5f), 0.0001f)
        assertEquals(-0.5f, liquidBackdropLayerTranslationFraction(0.0f), 0.0001f)
        assertEquals(0.5f, liquidBackdropLayerTranslationFraction(1.0f), 0.0001f)
    }
}
