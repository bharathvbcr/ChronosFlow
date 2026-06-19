package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render-level guard for the unified journal composer. It composes the same [JournalEntryComposer]
 * the add-FAB sheet and the Journal page's "New entry" both use, hosted inside a vertical scroll
 * (mirroring the sheet host). Verifies the redesign composes without a measure crash, that the
 * essential affordances lead the layout, that the rich extras stay tucked behind "Add details", and
 * that a save round-trips the body. Runs on Robolectric so it has internal access and runs with the
 * JVM unit-test suite (no device required).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class JournalEntryComposerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun newEntryLeadsWithMoodAndReflectionInsideAScrollHost() {
        setComposer()

        // Composes without the infinity-height crash, and the essentials lead.
        composeTestRule.onNodeWithText("How was your day?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your reflection").assertIsDisplayed()
        composeTestRule.onNodeWithText("New entry").assertIsDisplayed()
        // The rich extras live behind one collapsed section, so the default view stays clean.
        composeTestRule.onNodeWithText("Add details").assertExists()
        composeTestRule.onNodeWithText("Time (optional)").assertDoesNotExist()
    }

    @Test
    fun addDetailsExpandsToRevealTheRichExtras() {
        setComposer()

        composeTestRule.onNodeWithContentDescription("Expand Add details").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Time (optional)").assertExists()
        composeTestRule.onNodeWithText("Highlights").assertExists()
    }

    @Test
    fun typingAReflectionAndSavingRoundTripsTheBody() {
        var savedBody: String? = null
        setComposer(onSaveBody = { savedBody = it })

        // The reflection field is the only text field while "Add details" is collapsed.
        composeTestRule.onNode(hasSetTextAction()).performTextInput("A good, full day")
        composeTestRule.onNodeWithText("Save entry").performClick()
        composeTestRule.waitForIdle()

        assertEquals("A good, full day", savedBody)
    }

    private fun setComposer(onSaveBody: (String) -> Unit = {}) {
        composeTestRule.setContent {
            MaterialTheme {
                // Mirror the sheet host: a bounded, scrolling column. The composer must add no scroll
                // of its own (the bug that broke the add-FAB sheet was a second nested verticalScroll).
                Box(modifier = Modifier.fillMaxWidth().height(640.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        JournalEntryComposer(
                            editing = null,
                            initialDate = LocalDate.of(2026, 6, 18),
                            streak = 0,
                            onSave = { _, _, body, _, _, _, _ -> onSaveBody(body) },
                            onDelete = null
                        )
                    }
                }
            }
        }
    }
}
