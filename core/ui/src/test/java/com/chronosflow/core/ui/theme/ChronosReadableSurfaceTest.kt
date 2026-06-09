package com.chronosflow.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosReadableSurfaceTest {

    @Test
    fun darkDynamicSchemePreservesGlassSurfacesAndFixesTextTokens() {
        val source = lightColorScheme(
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            surfaceContainerHigh = Color.Green,
            onSurface = Color.Black,
            onSurfaceVariant = Color.Black
        )

        val readable = source.withChronosReadableContentColors(
            darkTheme = true,
            highContrastEnabled = false
        )

        assertEquals(source.background, readable.background)
        assertEquals(source.surface, readable.surface)
        assertEquals(source.surfaceContainerHigh, readable.surfaceContainerHigh)
        assertEquals(ChronosColors.NightOnSurface, readable.onBackground)
        assertEquals(ChronosColors.NightOnSurface, readable.onSurface)
        assertEquals(ChronosColors.NightOnSurfaceVariant, readable.onSurfaceVariant)
    }

    @Test
    fun lightDynamicSchemeKeepsItsMaterialYouNeutrals() {
        val source = lightColorScheme(
            background = Color.Yellow,
            onBackground = Color.Blue,
            surface = Color.Green,
            onSurface = Color.Red
        )

        val readable = source.withChronosReadableContentColors(
            darkTheme = false,
            highContrastEnabled = false
        )

        assertEquals(source.background, readable.background)
        assertEquals(source.onBackground, readable.onBackground)
        assertEquals(source.surface, readable.surface)
        assertEquals(source.onSurface, readable.onSurface)
    }

    @Test
    fun highContrastLightSchemeFixesTextWithoutChangingSurfaces() {
        val source = lightColorScheme(
            background = Color.Yellow,
            onBackground = Color.Blue,
            surface = Color.Green,
            surfaceContainerHigh = Color.Cyan,
            onSurface = Color.Red
        )

        val readable = source.withChronosReadableContentColors(
            darkTheme = false,
            highContrastEnabled = true
        )

        assertEquals(source.background, readable.background)
        assertEquals(Color.Black, readable.onBackground)
        assertEquals(source.surface, readable.surface)
        assertEquals(source.surfaceContainerHigh, readable.surfaceContainerHigh)
        assertEquals(Color.Black, readable.onSurface)
    }

    @Test
    fun highContrastDarkSchemeForcesReadableContent() {
        val source = darkColorScheme(
            background = Color.Black,
            onBackground = Color.Cyan,
            surface = Color.DarkGray,
            onSurface = Color.Red,
            onSurfaceVariant = Color.Yellow,
            outline = Color.Magenta
        )

        val readable = source.withChronosReadableContentColors(
            darkTheme = true,
            highContrastEnabled = true
        )

        assertEquals(source.background, readable.background)
        assertEquals(source.surface, readable.surface)
        assertEquals(Color.White, readable.onBackground)
        assertEquals(Color.White, readable.onSurface)
        assertEquals(Color.White, readable.onSurfaceVariant)
        assertEquals(Color.White, readable.outline)
    }
}
