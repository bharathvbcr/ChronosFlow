package com.chronosflow.wear.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import com.chronosflow.core.domain.wear.WearThemeContract
import com.chronosflow.wear.ChronosWearPalette
import com.chronosflow.wear.WearThemeStore

/**
 * ChronosFlow's watch theme, resolved in precedence order:
 *  1. The phone's Material You palette mirrored over the Data Layer ([WearThemeStore]) — the
 *     watch app looks like the phone app the wearer just left.
 *  2. The watch's own dynamic scheme (watch-face derived, supported devices only).
 *  3. The static brand fallback below — teal primary ([ChronosWearPalette], also used by the
 *     tiles, = the phone's `ChronosColors.BrightTeal`), coral tertiary (= `BrightCoral`).
 * Only accent roles are overridden; neutrals keep the Material defaults, which are already
 * tuned for AMOLED watch displays (pure-black background).
 */
private val ChronosColorScheme = ColorScheme(
    primary = Color(ChronosWearPalette.PRIMARY),
    primaryDim = Color(0xFF4E9A9A),
    primaryContainer = Color(0xFF2C6B6B),
    onPrimary = Color(0xFF003735),
    onPrimaryContainer = Color(0xFFBFEDEA),
    secondary = Color(0xFFB0CCCA),
    secondaryDim = Color(0xFF7E9795),
    secondaryContainer = Color(0xFF334B4A),
    onSecondary = Color(0xFF1B3534),
    onSecondaryContainer = Color(0xFFCCE8E5),
    tertiary = Color(0xFFFFB4A8),
    tertiaryDim = Color(0xFFD86F5F),
    tertiaryContainer = Color(0xFF6E3B32),
    onTertiary = Color(0xFF4A221B),
    onTertiaryContainer = Color(0xFFFFDAD3)
)

/**
 * Builds a Wear color scheme from a phone palette ([WearThemeContract] `IDX_*` order). The
 * phone's Material scheme has no `*Dim` roles, so those are derived by darkening the base
 * accent toward black.
 */
internal fun phonePaletteToColorScheme(palette: List<Int>): ColorScheme {
    fun c(index: Int) = Color(palette[index])
    fun dim(index: Int) = lerp(c(index), Color.Black, 0.25f)
    return ColorScheme(
        primary = c(WearThemeContract.IDX_PRIMARY),
        primaryDim = dim(WearThemeContract.IDX_PRIMARY),
        primaryContainer = c(WearThemeContract.IDX_PRIMARY_CONTAINER),
        onPrimary = c(WearThemeContract.IDX_ON_PRIMARY),
        onPrimaryContainer = c(WearThemeContract.IDX_ON_PRIMARY_CONTAINER),
        secondary = c(WearThemeContract.IDX_SECONDARY),
        secondaryDim = dim(WearThemeContract.IDX_SECONDARY),
        secondaryContainer = c(WearThemeContract.IDX_SECONDARY_CONTAINER),
        onSecondary = c(WearThemeContract.IDX_ON_SECONDARY),
        onSecondaryContainer = c(WearThemeContract.IDX_ON_SECONDARY_CONTAINER),
        tertiary = c(WearThemeContract.IDX_TERTIARY),
        tertiaryDim = dim(WearThemeContract.IDX_TERTIARY),
        tertiaryContainer = c(WearThemeContract.IDX_TERTIARY_CONTAINER),
        onTertiary = c(WearThemeContract.IDX_ON_TERTIARY),
        onTertiaryContainer = c(WearThemeContract.IDX_ON_TERTIARY_CONTAINER)
    )
}

@Composable
internal fun ChronosWearTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val phonePalette by WearThemeStore.state(context).collectAsStateWithLifecycle()
    val colorScheme = phonePalette
        ?.takeIf { it.size == WearThemeContract.PALETTE_SIZE }
        ?.let(::phonePaletteToColorScheme)
        ?: dynamicColorScheme(context)
        ?: ChronosColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
