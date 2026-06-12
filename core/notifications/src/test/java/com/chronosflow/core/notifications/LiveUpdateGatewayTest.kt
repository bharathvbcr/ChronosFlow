package com.chronosflow.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdateGatewayTest {
    @Test
    fun `api 37 selects metric style when promoted allowed`() {
        val decision = resolveLiveUpdateDecision(
            sdkInt = 37,
            title = "Deep work",
            text = "12:00 remaining",
            redactSensitiveTitles = false,
            promotedNotificationsAllowed = true,
            canPostPromotedNotifications = true
        )

        assertEquals(LiveUpdateStyle.METRIC, decision.style)
        assertTrue(decision.canUsePromotedOngoing)
    }

    @Test
    fun `api 37 falls back to progress when promoted permission missing`() {
        val decision = resolveLiveUpdateDecision(
            sdkInt = 37,
            title = "Deep work",
            text = "12:00 remaining",
            redactSensitiveTitles = false,
            promotedNotificationsAllowed = true,
            canPostPromotedNotifications = false
        )

        assertEquals(LiveUpdateStyle.PROGRESS, decision.style)
        assertFalse(decision.canUsePromotedOngoing)
    }

    @Test
    fun `api 36 uses progress style`() {
        val decision = resolveLiveUpdateDecision(
            sdkInt = 36,
            title = "Deep work",
            text = "12:00 remaining",
            redactSensitiveTitles = false,
            promotedNotificationsAllowed = true,
            canPostPromotedNotifications = false
        )

        assertEquals(LiveUpdateStyle.PROGRESS, decision.style)
    }

    @Test
    fun `privacy redaction uses generic title`() {
        val title = PrivacyRedaction.focusNotificationTitle("Deep work: taxes", redactSensitiveTitles = true)
        assertEquals(PrivacyRedaction.GENERIC_FOCUS_TITLE, title)
    }

    @Test
    fun `metric style gated to api 37`() {
        assertFalse(canUseMetricStyleForSdk(36))
        assertTrue(canUseMetricStyleForSdk(37))
    }

    @Test
    fun `active sessions use running timer metrics`() {
        assertEquals(LiveMetricTimerMode.RUNNING, liveMetricTimerMode(isPaused = false))
    }

    @Test
    fun `paused sessions surface resume as primary action`() {
        assertEquals(NotificationPrimaryAction.RESUME, notificationPrimaryAction(isPaused = true))
    }

    @Test
    fun `self updating metric timer only applies to running promoted sessions on api 37`() {
        assertTrue(
            usesSelfUpdatingMetricTimer(
                sdkInt = 37,
                isRunning = true,
                canUsePromotedOngoing = true
            )
        )
        assertFalse(
            usesSelfUpdatingMetricTimer(
                sdkInt = 37,
                isRunning = false,
                canUsePromotedOngoing = true
            )
        )
        assertFalse(
            usesSelfUpdatingMetricTimer(
                sdkInt = 36,
                isRunning = true,
                canUsePromotedOngoing = true
            )
        )
    }

    @Test
    fun `focus notification content formats remaining time`() {
        assertEquals("25:05", FocusNotificationContent.formatTimeLeft(1_505))
        assertEquals("00:00", FocusNotificationContent.formatTimeLeft(-10))
    }

    @Test
    fun `live pill compacts remaining time by magnitude`() {
        assertEquals("1h 5m", FocusNotificationContent.pillTimeText(3_900))
        assertEquals("12m", FocusNotificationContent.pillTimeText(12 * 60))
        assertEquals("45s", FocusNotificationContent.pillTimeText(45))
        assertEquals("0s", FocusNotificationContent.pillTimeText(-10))
    }
}
