package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ChronosFlow.VBCR.feature.daydial.delegate.CompanionTrendSections
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Insights trend section on Robolectric to guard the trend-range chips: all three
 * windows (including the 7-day option) appear and tapping one forwards the coerced selection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class InsightsTrendSectionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun trendRangeChipsRenderAndForwardSelection() {
        val selected = mutableListOf<Int>()

        composeTestRule.setContent {
            MaterialTheme {
                InsightsTrendSections(
                    trendRangeDays = 14,
                    trends = CompanionTrendSections(),
                    journalEntry = null,
                    sleepTrack = null,
                    onTrendRangeSelected = { selected += it },
                    onOpenJournal = {},
                    onOpenSleepLog = {}
                )
            }
        }

        composeTestRule.onNodeWithText("7d").assertIsDisplayed()
        composeTestRule.onNodeWithText("14d").assertIsDisplayed()
        composeTestRule.onNodeWithText("30d").assertIsDisplayed()

        composeTestRule.onNodeWithText("7d").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(7), selected)
    }
}
