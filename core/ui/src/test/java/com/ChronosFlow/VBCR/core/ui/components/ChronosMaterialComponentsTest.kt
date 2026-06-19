package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class ChronosMaterialComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sectionTitleRendersTitleAndSubtitle() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosSectionTitle(
                    title = "Plan",
                    subtitle = "Today"
                )
            }
        }

        composeTestRule.onNodeWithText("Plan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun topBarInvokesOnBackForNavigationTap() {
        var backCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                ChronosTopBar(
                    title = "Tasks",
                    onBack = { backCount++ }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, backCount)
        }
    }

    @Test
    fun screenScaffoldShowsTitleAndChildContent() {
        composeTestRule.setContent {
            MaterialTheme {
                ChronosScreenScaffold(title = "Today", onBack = null) {
                    Text("Body content")
                }
            }
        }

        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Body content").assertIsDisplayed()
    }

    @Test
    fun tooltipIconButtonExecutesAction() {
        var clicked = false

        composeTestRule.setContent {
            MaterialTheme {
                ChronosTooltipIconButton(
                    onClick = { clicked = true },
                    tooltip = "Help",
                    contentDescription = "Help action"
                ) {
                    Text("More")
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Help action").performClick()

        composeTestRule.runOnIdle {
            assertEquals(true, clicked)
        }
    }

    @Test
    fun listCardPropagatesOnClickAndRendersContent() {
        var selected = false

        composeTestRule.setContent {
            MaterialTheme {
                ChronosListCard(onClick = { selected = true }) {
                    Text("Open tasks")
                }
            }
        }

        composeTestRule.onNodeWithText("Open tasks").assertIsDisplayed().performClick()

        composeTestRule.runOnIdle {
            assertEquals(true, selected)
        }
    }

    @Test
    fun settingsRowDisplaysContentAndTogglesBooleanState() {
        var checked = false
        var changedCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                ChronosSettingsRow(
                    title = "Show debug",
                    subtitle = "Toggles an optional screen",
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        changedCount++
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Show debug").assertIsDisplayed()
        composeTestRule.onNodeWithText("Toggles an optional screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show debug").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, changedCount)
            assertEquals(true, checked)
        }
    }
}
