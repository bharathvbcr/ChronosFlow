package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChronosSurfacesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun backgroundRendersChildWithDarkHighContrastProfile() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosBackground(darkTheme = true, highContrast = true) {
                    Text("Backdrop")
                }
            }
        }

        composeTestRule.onNodeWithText("Backdrop").assertIsDisplayed()
    }

    @Test
    fun backgroundRendersChildWithDefaultProfile() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosBackground(darkTheme = false, highContrast = false) {
                    Text("Backdrop")
                }
            }
        }

        composeTestRule.onNodeWithText("Backdrop").assertIsDisplayed()
    }

    @Test
    fun glassPanelRendersChildAndCanBeDisabled() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosGlassPanel(enabled = false) {
                    Text("Glass")
                }
            }
        }

        composeTestRule.onNodeWithText("Glass").assertIsDisplayed()
    }

    @Test
    fun sectionHeaderShowsTitleSubtitleAndTrailingContent() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosSectionHeader(
                    title = "Overview",
                    subtitle = "Today",
                    trailing = { Text("Trailing") }
                )
            }
        }

        composeTestRule.onNodeWithText("Overview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trailing").assertIsDisplayed()
    }

    @Test
    fun actionTileExecutesClickAndRendersCopy() {
        var clicked = false

        composeTestRule.setContent {
            MaterialTheme {
                ChronosActionTile(
                    label = "Go",
                    description = "Open panel",
                    icon = Icons.Default.Check,
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Go").assertIsDisplayed().performClick()

        composeTestRule.runOnIdle {
            assertEquals(true, clicked)
        }
    }

    @Test
    fun emptyStateDisplaysActionNode() {
        var actionTriggered = false

        composeTestRule.setContent {
            MaterialTheme {
                ChronosEmptyState(
                    title = "No Results",
                    message = "Try another search",
                    action = {
                        Button(onClick = { actionTriggered = true }) {
                            Text("Retry")
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("No Results").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try another search").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed().performClick()

        composeTestRule.runOnIdle {
            assertEquals(true, actionTriggered)
        }
    }

    @Test
    fun metricTileShowsLabelAndValue() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosMetricTile(label = "Focus", value = "72")
            }
        }

        composeTestRule.onNodeWithText("Focus").assertIsDisplayed()
        composeTestRule.onNodeWithText("72").assertIsDisplayed()
    }

    @Test
    fun warningBannerShowsMessage() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosWarningBanner(
                    title = "Heads up",
                    message = "Keep your session secure"
                )
            }
        }

        composeTestRule.onNodeWithText("Heads up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep your session secure").assertIsDisplayed()
    }
}
