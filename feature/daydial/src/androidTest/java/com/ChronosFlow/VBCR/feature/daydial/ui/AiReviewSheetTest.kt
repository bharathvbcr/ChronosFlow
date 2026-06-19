package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ChronosFlow.VBCR.feature.daydial.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AiReviewSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bulkActionsAreVisibleAndInvokeCallbacks() {
        val events = mutableListOf<String>()
        composeTestRule.setContent {
            MaterialTheme {
                AiReviewSheet(
                    suggestions = listOf(reviewSuggestion()),
                    onApplyAll = { events += "applyAll" },
                    onDismissAll = { events += "dismissAll" },
                    onAccept = { events += "accept:$it" },
                    onReject = { events += "reject:$it" },
                    onModify = { id, _, _, _ -> events += "modify:$id" }
                )
            }
        }

        composeTestRule.onNodeWithText("Apply All").assertExists().performClick()
        composeTestRule.onNodeWithText("Dismiss All").assertExists().performClick()

        assertEquals(listOf("applyAll", "dismissAll"), events)
    }

    @Test
    fun perSuggestionActionsRemainAvailable() {
        val events = mutableListOf<String>()
        composeTestRule.setContent {
            MaterialTheme {
                AiReviewSheet(
                    suggestions = listOf(reviewSuggestion()),
                    onApplyAll = {},
                    onDismissAll = {},
                    onAccept = { events += "accept:$it" },
                    onReject = { events += "reject:$it" },
                    onModify = { id, title, start, duration -> events += "modify:$id:$title:$start:$duration" }
                )
            }
        }

        composeTestRule.onNodeWithText("Accept").performClick()
        composeTestRule.onNodeWithText("Reject").performClick()
        // Editing is folded behind the Adjust section; expand it to reach Save changes.
        composeTestRule.onNodeWithText("Adjust").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Save changes").performClick()

        assertEquals(
            listOf("accept:suggestion-1", "reject:suggestion-1", "modify:suggestion-1:Deep work:540:60"),
            events
        )
    }

    private fun reviewSuggestion(): TimeBlockUiModel = TimeBlockUiModel(
        id = "suggestion-1",
        title = "Deep work",
        startMinuteOfDay = 9 * 60,
        durationMinutes = 60,
        color = Color(0xFF64B5F6),
        provenance = "AI_SUGGESTED",
        flexibility = "OPTIONAL",
        isProtected = true
    )
}
