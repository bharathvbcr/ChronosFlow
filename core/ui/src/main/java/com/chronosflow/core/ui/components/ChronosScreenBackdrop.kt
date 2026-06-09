package com.chronosflow.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.settings.resolveChronosDarkTheme

/**
 * Themed screen background for routes outside DayDial chrome.
 * Reads appearance prefs (glass, reduce motion, high contrast, light/dark).
 */
@Composable
fun ChronosScreenBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val uiSettings = rememberChronosUiSettings()
    val darkTheme = resolveChronosDarkTheme(uiSettings.appearanceMode)
    val showAmbientBackdrop = uiSettings.glassSurfacesEnabled &&
        !uiSettings.highContrastEnabled

    ChronosBackground(
        modifier = modifier,
        darkTheme = darkTheme,
        highContrast = uiSettings.highContrastEnabled
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showAmbientBackdrop) {
                ChronosBackdrop(
                    theme = uiSettings.backdropTheme,
                    darkTheme = darkTheme,
                    reduceMotionEnabled = uiSettings.reduceMotionEnabled
                )
            }
            content()
        }
    }
}
