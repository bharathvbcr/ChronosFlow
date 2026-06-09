package com.chronosflow.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChronosThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun darkThemeWithoutDynamicColorUsesStaticBrightPalette() {
        var scheme: ColorScheme? = null

        composeTestRule.setContent {
            ChronosTheme(darkTheme = true, dynamicColor = false) {
                scheme = MaterialTheme.colorScheme
            }
        }

        composeTestRule.runOnIdle {
            val colors = requireNotNull(scheme)

            assertEquals(ChronosColors.BrightViolet, colors.primary)
            assertEquals(ChronosColors.DarkError, colors.error)
        }
    }

    @Test
    fun lightThemeWithoutDynamicColorUsesChronosNeutrals() {
        var scheme: ColorScheme? = null

        composeTestRule.setContent {
            ChronosTheme(darkTheme = false, dynamicColor = false) {
                scheme = MaterialTheme.colorScheme
            }
        }

        composeTestRule.runOnIdle {
            val colors = requireNotNull(scheme)

            assertEquals(ChronosColors.WarmOffWhite, colors.background)
            assertEquals(ChronosColors.Ink, colors.onBackground)
        }
    }

    @Test
    fun highContrastLightForcesReadableContentTokens() {
        var scheme: ColorScheme? = null

        composeTestRule.setContent {
            ChronosTheme(darkTheme = false, dynamicColor = false, highContrastEnabled = true) {
                scheme = MaterialTheme.colorScheme
            }
        }

        composeTestRule.runOnIdle {
            val colors = requireNotNull(scheme)

            assertEquals(Color.Black, colors.onSurfaceVariant)
            assertEquals(ChronosColors.HighContrastLightSurface, colors.surfaceContainer)
            assertEquals(ChronosColors.HighContrastLightSurfaceHigh, colors.surfaceContainerHigh)
        }
    }

    @Test
    fun focusAwareSettingsPropagateIntoLocalFocusAwareColorState() {
        var reducedMotion = false
        var highContrast = false
        var dynamicEnabled = false

        composeTestRule.setContent {
            ChronosTheme(
                darkTheme = false,
                dynamicColor = true,
                highContrastEnabled = true,
                reducedMotion = true
            ) {
                val localState = LocalFocusAwareColorState.current
                reducedMotion = localState.isReducedMotion
                highContrast = localState.isHighContrast
                dynamicEnabled = localState.dynamicColorEnabled
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(true, highContrast)
            assertEquals(true, reducedMotion)
            assertFalse("High contrast disables dynamic color", dynamicEnabled)
        }
    }

}
