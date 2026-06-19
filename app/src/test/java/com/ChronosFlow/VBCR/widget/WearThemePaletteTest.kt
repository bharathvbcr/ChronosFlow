package com.ChronosFlow.VBCR.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.toArgb
import com.ChronosFlow.VBCR.core.domain.wear.WearThemeContract
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the phone→watch palette packing to the [WearThemeContract] index order; the watch's
 * theme mapper relies on these positions.
 */
class WearThemePaletteTest {

    @Test
    fun `palette packs accent roles in contract order`() {
        val scheme = darkColorScheme()
        val palette = scheme.toWearThemePalette()

        assertEquals(WearThemeContract.PALETTE_SIZE, palette.size)
        assertEquals(scheme.primary.toArgb().toLong(), palette[WearThemeContract.IDX_PRIMARY])
        assertEquals(scheme.onPrimary.toArgb().toLong(), palette[WearThemeContract.IDX_ON_PRIMARY])
        assertEquals(
            scheme.primaryContainer.toArgb().toLong(),
            palette[WearThemeContract.IDX_PRIMARY_CONTAINER]
        )
        assertEquals(
            scheme.onPrimaryContainer.toArgb().toLong(),
            palette[WearThemeContract.IDX_ON_PRIMARY_CONTAINER]
        )
        assertEquals(scheme.secondary.toArgb().toLong(), palette[WearThemeContract.IDX_SECONDARY])
        assertEquals(
            scheme.onSecondary.toArgb().toLong(),
            palette[WearThemeContract.IDX_ON_SECONDARY]
        )
        assertEquals(
            scheme.secondaryContainer.toArgb().toLong(),
            palette[WearThemeContract.IDX_SECONDARY_CONTAINER]
        )
        assertEquals(
            scheme.onSecondaryContainer.toArgb().toLong(),
            palette[WearThemeContract.IDX_ON_SECONDARY_CONTAINER]
        )
        assertEquals(scheme.tertiary.toArgb().toLong(), palette[WearThemeContract.IDX_TERTIARY])
        assertEquals(scheme.onTertiary.toArgb().toLong(), palette[WearThemeContract.IDX_ON_TERTIARY])
        assertEquals(
            scheme.tertiaryContainer.toArgb().toLong(),
            palette[WearThemeContract.IDX_TERTIARY_CONTAINER]
        )
        assertEquals(
            scheme.onTertiaryContainer.toArgb().toLong(),
            palette[WearThemeContract.IDX_ON_TERTIARY_CONTAINER]
        )
    }
}
