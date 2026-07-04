package com.ChronosFlow.VBCR.core.ui.components

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.clearChronosUiSettingsStore
import com.ChronosFlow.VBCR.core.ui.settings.writeChronosUiBooleanSetting
import com.ChronosFlow.VBCR.core.ui.settings.writeChronosUiIntSetting
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CardEditorScaffoldComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    private val primarySection = CardEditorSection(
        id = "time",
        title = "Time",
        summary = "09:00 – 10:00",
        alwaysPrimary = true,
        hasValue = true,
        content = { Text("Time fields") },
    )

    private val secondarySection = CardEditorSection(
        id = "planner",
        title = "Planner",
        summary = "Movable",
        content = { Text("Planner fields") },
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(ChronosUiSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runTest {
            context.clearChronosUiSettingsStore()
        }
    }

    @Test
    fun cardEditorScaffold_showsLiveMoreOptionsCountForSecondarySections() {
        composeTestRule.setContent {
            MaterialTheme {
                CardEditorScaffold(
                    kind = "test",
                    stateKey = "live-count",
                    attributes = emptyList(),
                    sections = listOf(primarySection, secondarySection),
                    essentials = { Text("Title") },
                )
            }
        }

        composeTestRule.onNodeWithText("1 more option").assertIsDisplayed()
    }

    @Test
    fun cardEditorScaffold_moreOptionsCountRecomposesWhenSectionRevealed() {
        val revealController = CardEditorRevealController()

        composeTestRule.setContent {
            MaterialTheme {
                CardEditorScaffold(
                    kind = "test",
                    stateKey = "reveal-count",
                    attributes = emptyList(),
                    sections = listOf(primarySection, secondarySection),
                    revealController = revealController,
                    essentials = { Text("Title") },
                )
            }
        }

        composeTestRule.onNodeWithText("1 more option").assertIsDisplayed()

        revealController.reveal("planner")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("1 more option").assertDoesNotExist()
    }

    @Test
    fun cardEditorScaffold_moreOptionsHiddenWhenSectionPinned() = runTest {
        context.writeChronosUiBooleanSetting(editorSectionPinnedKey("test", "planner"), true)

        composeTestRule.setContent {
            MaterialTheme {
                CardEditorScaffold(
                    kind = "test",
                    stateKey = "pinned-count",
                    attributes = emptyList(),
                    sections = listOf(primarySection, secondarySection),
                    essentials = { Text("Title") },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("More options").assertDoesNotExist()
    }

    @Test
    fun cardEditorScaffold_moreOptionsHiddenWhenSectionAutoSurfaced() = runTest {
        context.writeChronosUiIntSetting(
            editorSectionUsageKey("test", "planner"),
            EDITOR_SECTION_AUTO_SURFACE_THRESHOLD,
        )

        composeTestRule.setContent {
            MaterialTheme {
                CardEditorScaffold(
                    kind = "test",
                    stateKey = "auto-surface-count",
                    attributes = emptyList(),
                    sections = listOf(primarySection, secondarySection),
                    essentials = { Text("Title") },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("More options").assertDoesNotExist()
    }
}
