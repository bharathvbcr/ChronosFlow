package com.ChronosFlow.VBCR.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ChronosFlow.VBCR.core.ui.components.ChronosBackground
import com.ChronosFlow.VBCR.core.ui.components.ChronosGlassPanel
import com.ChronosFlow.VBCR.core.ui.components.ChronosMetricTile
import com.ChronosFlow.VBCR.core.ui.components.ChronosSectionHeader
import com.ChronosFlow.VBCR.core.ui.components.ChronosShimmerPlaceholder
import com.ChronosFlow.VBCR.core.ui.components.ChronosWarningBanner
import com.ChronosFlow.VBCR.core.ui.components.LiquidBackdrop
import com.ChronosFlow.VBCR.core.ui.theme.ChronosGlassTokens
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
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

    @Test
    fun chronos_skeleton_light_screenshot() {
        captureSkeletonSurface(
            fileName = "chronos_skeleton_light.png",
            darkTheme = false,
            highContrast = false,
            reduceMotion = false
        )
    }

    @Test
    fun chronos_skeleton_dark_screenshot() {
        captureSkeletonSurface(
            fileName = "chronos_skeleton_dark.png",
            darkTheme = true,
            highContrast = false,
            reduceMotion = false
        )
    }

    @Test
    fun chronos_skeleton_light_high_contrast_screenshot() {
        captureSkeletonSurface(
            fileName = "chronos_skeleton_light_high_contrast.png",
            darkTheme = false,
            highContrast = true,
            reduceMotion = false
        )
    }

    @Test
    fun chronos_skeleton_dark_high_contrast_screenshot() {
        captureSkeletonSurface(
            fileName = "chronos_skeleton_dark_high_contrast.png",
            darkTheme = true,
            highContrast = true,
            reduceMotion = false
        )
    }

    @Test
    fun chronos_skeleton_reduced_motion_screenshot() {
        captureSkeletonSurface(
            fileName = "chronos_skeleton_reduced_motion.png",
            darkTheme = false,
            highContrast = false,
            reduceMotion = true
        )
    }

    @Test
    fun chronos_skeleton_dark_reduced_motion_screenshot() {
        captureSkeletonSurface(
            fileName = "chronos_skeleton_dark_reduced_motion.png",
            darkTheme = true,
            highContrast = false,
            reduceMotion = true
        )
    }

    @Test
    fun chronos_skeleton_large_font_screenshot() {
        captureSkeletonSurface(
            fileName = "chronos_skeleton_large_font.png",
            darkTheme = false,
            highContrast = false,
            reduceMotion = true,
            fontScale = 1.3f
        )
    }

    @Test
    fun chronos_skeleton_dark_high_contrast_large_font_screenshot() {
        captureSkeletonSurface(
            fileName = "chronos_skeleton_dark_high_contrast_large_font.png",
            darkTheme = true,
            highContrast = true,
            reduceMotion = true,
            fontScale = 1.3f
        )
    }

    @Test
    fun chronos_nav_pill_light_screenshot() {
        captureNavPillSurface(
            fileName = "chronos_nav_pill_light.png",
            darkTheme = false,
            highContrast = false,
            reduceMotion = false
        )
    }

    @Test
    fun chronos_nav_pill_dark_screenshot() {
        captureNavPillSurface(
            fileName = "chronos_nav_pill_dark.png",
            darkTheme = true,
            highContrast = false,
            reduceMotion = false
        )
    }

    @Test
    fun chronos_nav_pill_high_contrast_screenshot() {
        captureNavPillSurface(
            fileName = "chronos_nav_pill_high_contrast.png",
            darkTheme = true,
            highContrast = true,
            reduceMotion = false
        )
    }

    @Test
    fun chronos_nav_pill_reduced_motion_screenshot() {
        captureNavPillSurface(
            fileName = "chronos_nav_pill_reduced_motion.png",
            darkTheme = false,
            highContrast = false,
            reduceMotion = true
        )
    }

    @Test
    fun chronos_nav_pill_large_font_screenshot() {
        captureNavPillSurface(
            fileName = "chronos_nav_pill_large_font.png",
            darkTheme = false,
            highContrast = false,
            reduceMotion = true,
            fontScale = 1.3f
        )
    }

    private fun captureSkeletonSurface(
        fileName: String,
        darkTheme: Boolean,
        highContrast: Boolean,
        reduceMotion: Boolean,
        fontScale: Float = 1f
    ) {
        composeTestRule.setContent {
            ScreenshotSurface(
                darkTheme = darkTheme,
                highContrast = highContrast,
                fontScale = fontScale
            ) {
                ChronosBackground(
                    modifier = Modifier.fillMaxSize(),
                    darkTheme = darkTheme,
                    highContrast = highContrast
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ChronosSpacing.Standard),
                        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
                    ) {
                        ChronosSectionHeader(
                            title = "Loading",
                            subtitle = "Skeleton first paint"
                        )
                        ChronosShimmerPlaceholder(
                            rows = 3,
                            reduceMotionEnabled = reduceMotion,
                            highContrastEnabled = highContrast
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(screenshotPath(fileName))
    }

    private fun captureNavPillSurface(
        fileName: String,
        darkTheme: Boolean,
        highContrast: Boolean,
        reduceMotion: Boolean,
        fontScale: Float = 1f
    ) {
        composeTestRule.setContent {
            ScreenshotSurface(
                darkTheme = darkTheme,
                highContrast = highContrast,
                fontScale = fontScale
            ) {
                ChronosBackground(
                    modifier = Modifier.fillMaxSize(),
                    darkTheme = darkTheme,
                    highContrast = highContrast
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ChronosSpacing.Standard),
                        verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)
                    ) {
                        ChronosSectionHeader(
                            title = "Shell chrome",
                            subtitle = if (reduceMotion) {
                                "Nav pill · reduced motion"
                            } else {
                                "secondaryContainer selection pill"
                            }
                        )
                        NavPillFixture()
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(screenshotPath(fileName))
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

/**
 * Compact shell-bar fixture that mirrors Workstream B pill colors
 * (`secondaryContainer` / `onSecondaryContainer`) without pulling in app navigation.
 */
@Composable
private fun NavPillFixture() {
    val pillShape = RoundedCornerShape(ChronosGlassTokens.StandardRadius)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = pillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = ChronosSpacing.Micro
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChronosSpacing.Small, vertical = ChronosSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavPillItem(label = "Today", selected = true, modifier = Modifier.weight(1f))
            NavPillItem(label = "Plan", selected = false, modifier = Modifier.weight(1f))
            NavPillItem(label = "Focus", selected = false, modifier = Modifier.weight(1f), badge = "•")
        }
    }
}

@Composable
private fun NavPillItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val shape = RoundedCornerShape(ChronosGlassTokens.StandardRadius)
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .heightIn(min = ChronosSpacing.Hero)
            .background(background, shape)
            .padding(horizontal = ChronosSpacing.Compact, vertical = ChronosSpacing.Small),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Micro),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            if (badge != null) {
                Text(
                    text = badge,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = fontScale)
    ) {
        ChronosTheme(
            darkTheme = darkTheme,
            dynamicColor = false,
            highContrastEnabled = highContrast
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    content()
                }
            }
        }
    }
}
