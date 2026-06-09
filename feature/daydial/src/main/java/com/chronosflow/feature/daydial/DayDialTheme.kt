package com.chronosflow.feature.daydial

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.chronosflow.core.ui.settings.rememberPersistentUiBooleanSetting
import com.chronosflow.core.ui.settings.rememberPersistentUiIntSetting
import com.chronosflow.core.ui.settings.rememberPersistentUiStringSetting
import com.chronosflow.core.ui.theme.ChronosColors
import com.chronosflow.core.ui.theme.withChronosReadableContentColors
import com.chronosflow.feature.daydial.model.AppearanceMode

@Composable
fun rememberPersistentBoolean(key: String, defaultValue: Boolean): MutableState<Boolean> {
    return rememberPersistentUiBooleanSetting(key, defaultValue)
}

@Composable
fun rememberPersistentString(key: String, defaultValue: String): MutableState<String> {
    return rememberPersistentUiStringSetting(key, defaultValue)
}

@Composable
fun rememberPersistentInt(key: String, defaultValue: Int): MutableState<Int> {
    return rememberPersistentUiIntSetting(key, defaultValue)
}

@Composable
internal fun resolveDayDialDarkTheme(appearanceMode: AppearanceMode): Boolean = when (appearanceMode) {
    AppearanceMode.SYSTEM -> isSystemInDarkTheme()
    AppearanceMode.LIGHT -> false
    AppearanceMode.DARK -> true
}

@Composable
fun chronosColorScheme(
    context: Context,
    dynamicColorEnabled: Boolean,
    darkTheme: Boolean,
    highContrastEnabled: Boolean
): ColorScheme {
    if (dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return dynamic.withChronosReadableContentColors(
            darkTheme = darkTheme,
            highContrastEnabled = highContrastEnabled
        )
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFB9A5FF),
            onPrimary = Color(0xFF28105E),
            primaryContainer = Color(0xFF554294),
            onPrimaryContainer = Color(0xFFF0E9FF),
            secondary = Color(0xFF75D6D2),
            onSecondary = Color(0xFF003735),
            tertiary = Color(0xFFFFB4A8),
            background = if (highContrastEnabled) Color.Black else ChronosColors.Night,
            onBackground = Color(0xFFF6F2FF),
            surface = if (highContrastEnabled) Color.Black else ChronosColors.NightPanel,
            onSurface = Color(0xFFF6F2FF),
            surfaceVariant = if (highContrastEnabled) Color(0xFF111111) else Color(0xFF2A2E3D),
            surfaceContainerLowest = if (highContrastEnabled) Color.Black else Color(0xFF0F1118),
            surfaceContainerLow = if (highContrastEnabled) Color(0xFF111111) else Color(0xFF1A1D28),
            surfaceContainer = if (highContrastEnabled) Color(0xFF111111) else Color(0xFF222633),
            surfaceContainerHigh = if (highContrastEnabled) Color(0xFF1A1A1A) else Color(0xFF2A2E3D),
            surfaceContainerHighest = if (highContrastEnabled) Color(0xFF222222) else Color(0xFF35394A),
            onSurfaceVariant = if (highContrastEnabled) Color.White else Color(0xFFE4DDF0),
            outline = if (highContrastEnabled) Color.White else Color(0xFF9F98AC),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005)
        )
    } else {
        lightColorScheme(
            primary = ChronosColors.SoftViolet,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE9DDFF),
            onPrimaryContainer = Color(0xFF25134D),
            secondary = ChronosColors.Teal,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFBFEDEA),
            onSecondaryContainer = Color(0xFF00201F),
            tertiary = ChronosColors.Coral,
            background = if (highContrastEnabled) Color.White else ChronosColors.WarmOffWhite,
            onBackground = if (highContrastEnabled) Color.Black else ChronosColors.Ink,
            surface = Color(0xFFFFFBFE),
            onSurface = if (highContrastEnabled) Color.Black else ChronosColors.Ink,
            surfaceVariant = Color(0xFFEDE7F3),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFAF7FD),
            surfaceContainer = Color(0xFFF3EEF9),
            surfaceContainerHigh = Color(0xFFEDE7F3),
            surfaceContainerHighest = Color(0xFFE7E0F0),
            onSurfaceVariant = if (highContrastEnabled) Color.Black else Color(0xFF373142),
            outline = if (highContrastEnabled) Color.Black else Color(0xFF797187),
            error = Color(0xFFB3261E),
            onError = Color.White
        )
    }
}

fun chronosBackgroundBrush(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    highContrastEnabled: Boolean
): Brush {
    if (highContrastEnabled) {
        return Brush.verticalGradient(
            colors = listOf(colorScheme.background, colorScheme.surface)
        )
    }
    return if (darkTheme) {
        Brush.radialGradient(
            colors = listOf(
                colorScheme.primaryContainer.copy(alpha = 0.55f),
                colorScheme.background,
                colorScheme.background
            ),
            center = androidx.compose.ui.geometry.Offset(0f, 0f),
            radius = 1200f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                colorScheme.primaryContainer.copy(alpha = 0.35f),
                colorScheme.surfaceVariant.copy(alpha = 0.5f),
                colorScheme.background
            ),
            center = androidx.compose.ui.geometry.Offset(0f, 0f),
            radius = 1200f
        )
    }
}
