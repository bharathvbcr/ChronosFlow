package com.ChronosFlow.VBCR

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * First launch shows [com.ChronosFlow.VBCR.onboarding.ChronosOnboarding] (gated on the
     * KEY_ONBOARDING_COMPLETED setting), which sits in front of the day dial. Dismiss it via
     * "Skip" so each test starts on the dial. No-op once onboarding has already been completed
     * on the device (the setting persists across tests in the run).
     */
    @Before
    fun skipOnboardingIfShown() {
        // The shell renders after a brief defer, so wait until either onboarding ("Skip") or the
        // dial ("Today") is actually on screen before deciding — checking too early finds neither.
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithContentDescription("Today").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Skip").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule
                    .onAllNodesWithContentDescription("Today")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    @Test
    fun app_launches_and_displays_day_dial() {
        // "Today" renders in two places (nav pill + page header), so match the nav destination
        // by content description rather than the ambiguous visible text.
        composeTestRule.onNodeWithContentDescription("Today").assertExists()
        composeTestRule.onNodeWithContentDescription("Plan").performClick()
        composeTestRule.onNodeWithText("Generate").assertExists()
        composeTestRule.onNodeWithContentDescription("Add a block manually").assertExists()
    }

    @Test
    fun primary_layout_focuses_day_planning_and_focus() {
        composeTestRule.onNodeWithContentDescription("Today").assertExists()
        composeTestRule.onNodeWithContentDescription("Plan").assertExists()
        composeTestRule.onNodeWithContentDescription("Focus").assertExists()
        // Plan/Today/Focus are the primary destinations. Secondary destinations (Tasks, Habits,
        // Goals, Meds, Review) are intentionally hidden in the compact bottom bar but DO appear in
        // the expanded navigation rail on large screens, so their presence/absence is layout-
        // dependent and not asserted here. ("Insights" was renamed to "Review" — it no longer
        // surfaces as text anywhere.)
        composeTestRule.onNodeWithText("Insights").assertDoesNotExist()
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

        // Query by each command's most DISTINCTIVE keyword. The palette caps the rendered result
        // list, so broad shared terms ("plan", "focus") can push a specific command past the fold;
        // distinctive keywords match few commands and reliably surface the target. Habits, tasks,
        // and medication are reachable through the palette by design (the narrow primary surface is
        // Today/Plan/Focus, but standalone areas graduate to searchable commands). "Open templates"
        // was renamed to "Open routines".
        assertCommandAvailableViaSearch("daily", "Open daily dial")
        assertCommandAvailableViaSearch("schedule", "Open plan")
        assertCommandAvailableViaSearch("generate", "Open planning tools")
        assertCommandAvailableViaSearch("routines", "Open routines")
        assertCommandAvailableViaSearch("notification", "Open notification settings")
        assertCommandAvailableViaSearch("deep work", "Open focus")
        assertCommandAvailableViaSearch("inbox", "Open tasks")
        assertCommandAvailableViaSearch("streak", "Open habits")
        assertCommandAvailableViaSearch("adherence", "Open medication")
    }

    private fun assertCommandAvailableViaSearch(query: String, commandTitle: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(query)
        composeTestRule.onNodeWithText(commandTitle).assertExists()
    }

    @Test
    fun command_palette_focus_command_remains_available() {
        composeTestRule.onNodeWithContentDescription("Open command palette").performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("deep work")
        composeTestRule.onNodeWithText("Open focus").performClick()

        composeTestRule.onNodeWithText("Ready to focus").assertExists()
    }
}
