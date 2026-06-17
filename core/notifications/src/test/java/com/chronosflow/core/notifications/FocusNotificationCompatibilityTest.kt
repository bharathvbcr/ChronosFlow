package com.chronosflow.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusNotificationCompatibilityTest {
    @Test
    fun `live updates are gated to api 36 and newer`() {
        assertFalse(canRenderLiveUpdatesForSdk(35))
        assertTrue(canRenderLiveUpdatesForSdk(36))
        assertTrue(canRenderLiveUpdatesForSdk(37))
    }

    @Test
    fun `progress clamps when remaining time is larger than total`() {
        assertEquals(120 to 0, focusNotificationProgress(totalSeconds = 120, timeLeftSeconds = 180))
    }

    @Test
    fun `progress clamps when remaining time is negative`() {
        assertEquals(120 to 120, focusNotificationProgress(totalSeconds = 120, timeLeftSeconds = -30))
    }

    @Test
    fun `progress keeps one second minimum for invalid totals`() {
        assertEquals(1 to 1, focusNotificationProgress(totalSeconds = 0, timeLeftSeconds = 0))
    }

    @Test
    fun `redacted decision hides session details`() {
        val decision = resolveLiveUpdateDecision(
            sdkInt = 36,
            title = "Tax prep",
            text = "10:00 remaining",
            redactSensitiveTitles = true,
            promotedNotificationsAllowed = true,
            canPostPromotedNotifications = false
        )

        assertEquals(PrivacyRedaction.GENERIC_FOCUS_TITLE, decision.redactedTitle)
        assertEquals(FocusNotificationContent.REDACTED_BODY, decision.redactedText)
    }
}
