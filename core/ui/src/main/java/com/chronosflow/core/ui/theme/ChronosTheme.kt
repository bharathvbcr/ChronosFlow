package com.chronosflow.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val ChronosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalFocusAwareColorState = staticCompositionLocalOf { FocusAwareColorState() }

@Composable
fun ChronosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    highContrastEnabled: Boolean = false,
    reducedMotion: Boolean = false,
    focusAwareColorState: FocusAwareColorState = FocusAwareColorState(
        isHighContrast = highContrastEnabled,
        isReducedMotion = reducedMotion,
        dynamicColorEnabled = dynamicColor
    ),
    content: @Composable () -> Unit
) {
    val accentState = focusAwareColorState.copy(
        isHighContrast = highContrastEnabled,
        isReducedMotion = reducedMotion,
        dynamicColorEnabled = dynamicColor && !highContrastEnabled
    )

    val colorScheme = when {
        accentState.dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.withChronosReadableContentColors(
                darkTheme = darkTheme,
                highContrastEnabled = highContrastEnabled
            )
        }
        darkTheme -> darkColorScheme(
            primary = ChronosColors.BrightViolet,
            onPrimary = ChronosColors.DarkOnPrimary,
            primaryContainer = ChronosColors.DarkPrimaryContainer,
            onPrimaryContainer = ChronosColors.DarkOnPrimaryContainer,
            secondary = ChronosColors.BrightTeal,
            onSecondary = ChronosColors.DarkOnSecondary,
            tertiary = ChronosColors.BrightCoral,
            background = ChronosColors.Night,
            onBackground = ChronosColors.NightOnSurface,
            surface = ChronosColors.NightPanel,
            onSurface = ChronosColors.NightOnSurface,
            surfaceVariant = ChronosColors.NightSurfaceDim,
            onSurfaceVariant = if (highContrastEnabled) Color.White else ChronosColors.NightOnSurfaceVariant,
            outline = if (highContrastEnabled) Color.White else ChronosColors.NightOutline,
            surfaceContainer = if (highContrastEnabled) {
                ChronosColors.HighContrastDarkSurface
            } else {
                ChronosColors.NightSurfaceContainer
            },
            surfaceContainerHigh = if (highContrastEnabled) {
                ChronosColors.HighContrastDarkSurfaceHigh
            } else {
                ChronosColors.NightSurfaceDim
            },
            error = ChronosColors.DarkError,
            onError = ChronosColors.DarkOnError
        )
        else -> lightColorScheme(
            primary = ChronosColors.SoftViolet,
            onPrimary = Color.White,
            primaryContainer = ChronosColors.LightViolet,
            onPrimaryContainer = ChronosColors.LightOnPrimaryContainer,
            secondary = ChronosColors.Teal,
            onSecondary = Color.White,
            secondaryContainer = ChronosColors.LightTeal,
            onSecondaryContainer = ChronosColors.LightOnSecondaryContainer,
            tertiary = ChronosColors.Coral,
            background = ChronosColors.WarmOffWhite,
            onBackground = ChronosColors.Ink,
            surface = ChronosColors.LightSurface,
            onSurface = ChronosColors.Ink,
            surfaceVariant = ChronosColors.LightSurfaceVariant,
            onSurfaceVariant = if (highContrastEnabled) Color.Black else ChronosColors.LightOnSurfaceVariant,
            outline = if (highContrastEnabled) Color.Black else ChronosColors.LightOutline,
            surfaceContainer = if (highContrastEnabled) {
                ChronosColors.HighContrastLightSurface
            } else {
                ChronosColors.LightSurfaceVariant
            },
            surfaceContainerHigh = if (highContrastEnabled) {
                ChronosColors.HighContrastLightSurfaceHigh
            } else {
                ChronosColors.LightSurfaceVariant
            }
        )
    }

    val dialAccent = accentState.dialAccentColor(colorScheme.primary)

    CompositionLocalProvider(LocalFocusAwareColorState provides accentState.copy()) {
        MaterialTheme(
            colorScheme = colorScheme.copy(
                tertiary = dialAccent,
                primary = if (accentState.focusAwareAccentsEnabled && !highContrastEnabled) {
                    dialAccent
                } else {
                    colorScheme.primary
                }
            ),
            typography = ChronosTypography,
            shapes = ChronosShapes,
            content = content
        )
    }
}

fun ColorScheme.withChronosReadableContentColors(
    darkTheme: Boolean,
    highContrastEnabled: Boolean
): ColorScheme {
    if (!darkTheme && !highContrastEnabled) return this
    return if (darkTheme) {
        copy(
            onBackground = if (highContrastEnabled) Color.White else ChronosColors.NightOnSurface,
            onSurface = if (highContrastEnabled) Color.White else ChronosColors.NightOnSurface,
            onSurfaceVariant = if (highContrastEnabled) Color.White else ChronosColors.NightOnSurfaceVariant,
            outline = if (highContrastEnabled) Color.White else ChronosColors.NightOutline
        )
    } else {
        copy(
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = Color.Black,
            outline = Color.Black
        )
    }
}
