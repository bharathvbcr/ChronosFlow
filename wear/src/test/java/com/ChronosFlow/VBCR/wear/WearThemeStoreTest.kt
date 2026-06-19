package com.ChronosFlow.VBCR.wear

import com.ChronosFlow.VBCR.core.domain.wear.WearThemeContract
import com.ChronosFlow.VBCR.wear.presentation.phonePaletteToColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WearThemeStoreTest {

    private val context get() = RuntimeEnvironment.getApplication()

    /** A distinct ARGB value per slot so ordering mistakes can't cancel out. */
    private val palette = List(WearThemeContract.PALETTE_SIZE) { 0xFF000000.toInt() + it + 1 }

    @Before
    fun setUp() {
        WearThemeStore.clear(context)
    }

    @Test
    fun `write then read round-trips the palette`() {
        WearThemeStore.write(context, palette)
        assertEquals(palette, WearThemeStore.read(context))
    }

    @Test
    fun `clear empties the mirror`() {
        WearThemeStore.write(context, palette)
        WearThemeStore.clear(context)
        assertNull(WearThemeStore.read(context))
    }

    @Test
    fun `wrong-size palettes are rejected`() {
        WearThemeStore.write(context, palette)
        WearThemeStore.write(context, palette.dropLast(1))
        assertEquals(palette, WearThemeStore.read(context))
    }

    @Test
    fun `phone palette maps onto the wear scheme by contract index`() {
        val scheme = phonePaletteToColorScheme(palette)
        assertEquals(Color(palette[WearThemeContract.IDX_PRIMARY]), scheme.primary)
        assertEquals(Color(palette[WearThemeContract.IDX_ON_PRIMARY]), scheme.onPrimary)
        assertEquals(Color(palette[WearThemeContract.IDX_SECONDARY_CONTAINER]), scheme.secondaryContainer)
        assertEquals(Color(palette[WearThemeContract.IDX_ON_TERTIARY_CONTAINER]), scheme.onTertiaryContainer)
    }
}
