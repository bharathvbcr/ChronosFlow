package com.ChronosFlow.VBCR.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ChronosFlow.VBCR.core.ui.components.ChronosBackground
import com.ChronosFlow.VBCR.core.ui.components.ChronosGlassPanel
import com.ChronosFlow.VBCR.core.ui.components.ChronosMetricTile
import com.ChronosFlow.VBCR.core.ui.components.ChronosSectionHeader
import com.ChronosFlow.VBCR.core.ui.components.ChronosWarningBanner
import com.ChronosFlow.VBCR.core.ui.components.LiquidBackdrop
import com.ChronosFlow.VBCR.core.ui.theme.ChronosTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LiquidMaterialScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun liquid_material_light_screenshot() {
        captureLiquidMaterialSurface(
            fileName = "liquid_material_light.png",
            darkTheme = false
        )
    }

    @Test
    fun liquid_material_dark_screenshot() {
        captureLiquidMaterialSurface(
            fileName = "liquid_material_dark.png",
            darkTheme = true
        )
    }

    @Test
    fun liquid_material_high_contrast_large_text_screenshot() {
        captureLiquidMaterialSurface(
            fileName = "liquid_material_high_contrast_large_text.png",
            darkTheme = true,
            highContrast = true,
            fontScale = 1.3f
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun liquid_material_expanded_layout_screenshot() {
        captureLiquidMaterialSurface(
            fileName = "liquid_material_expanded_layout.png",
            darkTheme = false,
            fontScale = 1.15f
        )
    }

    @Test
    fun liquid_backdrop_reduced_motion_screenshot() {
        composeTestRule.setContent {
            ScreenshotSurface(darkTheme = false) {
                LiquidBackdrop(reduceMotionEnabled = true)
            }
        }

        composeTestRule.onRoot().captureRoboImage(screenshotPath("liquid_backdrop_reduced_motion.png"))
    }

    private fun captureLiquidMaterialSurface(
        fileName: String,
        darkTheme: Boolean,
        highContrast: Boolean = false,
        fontScale: Float = 1f
    ) {
        composeTestRule.setContent {
            ScreenshotSurface(darkTheme = darkTheme, fontScale = fontScale) {
                LiquidMaterialFixture(
                    darkTheme = darkTheme,
                    highContrast = highContrast
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(screenshotPath(fileName))
    }
}

private fun screenshotPath(fileName: String): String = "src/test/screenshots/$fileName"

@Composable
private fun LiquidMaterialFixture(
    darkTheme: Boolean,
    highContrast: Boolean
) {
    ChronosBackground(
        modifier = Modifier.fillMaxSize(),
        darkTheme = darkTheme,
        highContrast = highContrast
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ChronosSectionHeader(
                title = "Daily command center",
                subtitle = "Liquid surfaces, readable metrics, and alert states"
            )
            ChronosGlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Focus session")
                    ChronosMetricTile(label = "Remaining", value = "42m")
                    ChronosMetricTile(label = "Protected blocks", value = "3")
                }
            }
            ChronosWarningBanner(
                title = "Schedule conflict",
                message = "Deep-work block overlaps with medication reminder."
            )
        }
    }
}

@Composable
private fun ScreenshotSurface(
    darkTheme: Boolean,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = fontScale)
    ) {
        ChronosTheme(darkTheme = darkTheme, dynamicColor = false) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    content()
                }
            }
        }
    }
}
