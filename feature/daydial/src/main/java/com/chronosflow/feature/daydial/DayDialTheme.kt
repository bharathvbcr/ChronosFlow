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
            primary = ChronosColors.BrightViolet,
            onPrimary = ChronosColors.DarkOnPrimary,
            primaryContainer = ChronosColors.DarkPrimaryContainer,
            onPrimaryContainer = ChronosColors.DarkOnPrimaryContainer,
            secondary = ChronosColors.BrightTeal,
            onSecondary = ChronosColors.DarkOnSecondary,
            tertiary = ChronosColors.BrightCoral,
            background = if (highContrastEnabled) Color.Black else ChronosColors.Night,
            onBackground = ChronosColors.NightOnSurface,
            surface = if (highContrastEnabled) Color.Black else ChronosColors.NightPanel,
            onSurface = ChronosColors.NightOnSurface,
            surfaceVariant = if (highContrastEnabled) ChronosColors.HighContrastDarkSurface else ChronosColors.NightSurfaceDim,
            surfaceContainerLowest = if (highContrastEnabled) Color.Black else ChronosColors.NightSurfaceContainerLowest,
            surfaceContainerLow = if (highContrastEnabled) ChronosColors.HighContrastDarkSurface else ChronosColors.NightSurfaceContainerLow,
            surfaceContainer = if (highContrastEnabled) ChronosColors.HighContrastDarkSurface else ChronosColors.NightSurfaceContainer,
            surfaceContainerHigh = if (highContrastEnabled) ChronosColors.HighContrastDarkSurfaceHigh else ChronosColors.NightSurfaceDim,
            surfaceContainerHighest = if (highContrastEnabled) ChronosColors.HighContrastDarkSurfaceHighest else ChronosColors.NightSurfaceContainerHighest,
            onSurfaceVariant = if (highContrastEnabled) Color.White else ChronosColors.NightOnSurfaceVariantBright,
            outline = if (highContrastEnabled) Color.White else ChronosColors.NightOutline,
            error = ChronosColors.DarkError,
            onError = ChronosColors.DarkOnError
        )
    } else {
        lightColorScheme(
            primary = ChronosColors.SoftViolet,
            onPrimary = Color.White,
            primaryContainer = ChronosColors.LightViolet,
            onPrimaryContainer = ChronosColors.LightOnPrimaryContainer,
            secondary = ChronosColors.Teal,
            onSecondary = Color.White,
            secondaryContainer = ChronosColors.LightTeal,
            onSecondaryContainer = ChronosColors.LightOnSecondaryContainer,
            tertiary = ChronosColors.Coral,
            background = if (highContrastEnabled) Color.White else ChronosColors.WarmOffWhite,
            onBackground = if (highContrastEnabled) Color.Black else ChronosColors.Ink,
            surface = ChronosColors.LightSurface,
            onSurface = if (highContrastEnabled) Color.Black else ChronosColors.Ink,
            surfaceVariant = ChronosColors.LightSurfaceVariant,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = ChronosColors.LightSurfaceContainerLow,
            surfaceContainer = ChronosColors.LightSurfaceContainer,
            surfaceContainerHigh = ChronosColors.LightSurfaceVariant,
            surfaceContainerHighest = ChronosColors.LightSurfaceContainerHighest,
            onSurfaceVariant = if (highContrastEnabled) Color.Black else ChronosColors.LightOnSurfaceVariantStrong,
            outline = if (highContrastEnabled) Color.Black else ChronosColors.LightOutline,
            error = ChronosColors.LightError,
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
