package com.chronosflow

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_and_displays_day_dial() {
        composeTestRule.onNodeWithText("Today").assertExists()
        composeTestRule.onNodeWithContentDescription("Plan").performClick()
        composeTestRule.onNodeWithText("Generate").assertExists()
        composeTestRule.onNodeWithContentDescription("Add a block manually").assertExists()
    }

    @Test
    fun primary_layout_focuses_day_planning_and_focus() {
        composeTestRule.onNodeWithContentDescription("Today").assertExists()
        composeTestRule.onNodeWithContentDescription("Plan").assertExists()
        composeTestRule.onNodeWithContentDescription("Focus").assertExists()
        composeTestRule.onNodeWithText("Insights").assertDoesNotExist()
        composeTestRule.onNodeWithText("Review").assertDoesNotExist()
        composeTestRule.onNodeWithText("Habits").assertDoesNotExist()
        composeTestRule.onNodeWithText("Meds").assertDoesNotExist()
    }

    @Test
    fun quick_add_menu_exposes_inline_command_input() {
        composeTestRule.onNodeWithContentDescription("Open quick create").performClick()

        composeTestRule.onNode(hasSetTextAction()).assertExists()
        composeTestRule.onNodeWithContentDescription("Quick add typed command", useUnmergedTree = true).assertExists()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("call mom tomorrow")

        composeTestRule.onNodeWithText("Ready to add task.").assertExists()
        composeTestRule.onNodeWithContentDescription("Quick add task", useUnmergedTree = true).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Call Mom", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Call Mom", useUnmergedTree = true).assertExists()
    }

    @Test
    fun command_palette_lists_active_direction_commands() {
        composeTestRule.onNodeWithContentDescription("Open command palette").performClick()

        composeTestRule.onNodeWithText("Command palette").assertExists()

        assertCommandAvailableViaSearch("daily", "Open daily dial")
        assertCommandAvailableViaSearch("plan", "Open plan")
        assertCommandAvailableViaSearch("planning", "Open planning tools")
        assertCommandAvailableViaSearch("template", "Open templates")
        assertCommandAvailableViaSearch("notification", "Open notification settings")
        assertCommandAvailableViaSearch("focus", "Open focus")
        assertCommandAvailableViaSearch("task", "Open tasks")
        assertCommandUnavailableViaSearch("habit", "Open habits")
        assertCommandUnavailableViaSearch("medication", "Open medication")
    }

    private fun assertCommandAvailableViaSearch(query: String, commandTitle: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(query)
        composeTestRule.onNodeWithText(commandTitle).assertExists()
    }

    private fun assertCommandUnavailableViaSearch(query: String, commandTitle: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(query)
        composeTestRule.onNodeWithText(commandTitle).assertDoesNotExist()
    }

    @Test
    fun command_palette_focus_command_remains_available() {
        composeTestRule.onNodeWithContentDescription("Open command palette").performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("focus")
        composeTestRule.onNodeWithText("Open focus").performClick()

        composeTestRule.onNodeWithText("Ready to focus").assertExists()
    }
}
