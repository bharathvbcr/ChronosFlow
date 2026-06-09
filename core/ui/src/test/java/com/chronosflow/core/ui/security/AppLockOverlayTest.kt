package com.chronosflow.core.ui.security

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.chronosflow.core.ui.theme.ChronosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppLockOverlayTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appLockOverlayRendersOnThemedBackdrop() {
        composeTestRule.setContent {
            ChronosTheme {
                AppLockOverlay(
                    title = "ChronosFlow is locked",
                    subtitle = "Use your fingerprint, face, or device PIN to continue",
                    canAuthenticate = false,
                    errorMessage = null,
                    onUnlock = {}
                )
            }
        }

        composeTestRule.onNodeWithText("ChronosFlow is locked").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set up device lock").assertIsDisplayed()
    }
}
