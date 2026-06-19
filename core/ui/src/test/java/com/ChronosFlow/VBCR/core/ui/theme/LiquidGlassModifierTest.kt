package com.ChronosFlow.VBCR.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LiquidGlassModifierTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chronosGlassContentColorUsesSurfaceTextForHighContrastContainerMatch() {
        var onSurface = Color.Unspecified
        var onSurfaceVariant = Color.Unspecified
        var computed = Color.Unspecified

        composeTestRule.setContent {
            ChronosTheme(darkTheme = false, dynamicColor = false) {
                onSurface = MaterialTheme.colorScheme.onSurface
                onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                computed = chronosGlassContentColor(onSurface)
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(onSurfaceVariant.toArgb(), computed.toArgb())
        }
    }

    @Test
    fun chronosGlassContentColorUsesOnSurfaceForHighLuminanceContainer() {
        var onSurface = Color.Unspecified
        var computed = Color.Unspecified

        composeTestRule.setContent {
            ChronosTheme(darkTheme = false, dynamicColor = false) {
                onSurface = MaterialTheme.colorScheme.onSurface
                computed = chronosGlassContentColor(Color.White)
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(onSurface.toArgb(), computed.toArgb())
        }
    }
}
