package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ChronosFlow.VBCR.core.ui.theme.ChronosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChronosSkeletonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun shimmerPlaceholderExposesLoadingSemantics() {
        composeTestRule.setContent {
            ChronosTheme(darkTheme = false, dynamicColor = false) {
                ChronosShimmerPlaceholder(
                    modifier = Modifier.fillMaxWidth(),
                    rows = 2,
                    reduceMotionEnabled = true,
                    highContrastEnabled = false
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun skeletonRendersUnderHighContrastWithoutCrash() {
        composeTestRule.setContent {
            ChronosTheme(darkTheme = true, dynamicColor = false, highContrastEnabled = true) {
                MaterialTheme {
                    ChronosSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        reduceMotionEnabled = false,
                        highContrastEnabled = true
                    )
                }
            }
        }

        // Skeleton is non-textual; successful composition is the assertion.
        composeTestRule.waitForIdle()
    }
}
