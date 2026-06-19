package com.ChronosFlow.VBCR.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdateGatewayTest {
    @Test
    fun `api 37 selects thick progress bar even when promoted allowed`() {
        val decision = resolveLiveUpdateDecision(
            sdkInt = 37,
            title = "Deep work",
            text = "12:00 remaining",
            redactSensitiveTitles = false,
            promotedNotificationsAllowed = true,
            canPostPromotedNotifications = true
        )

        // A non-timer surface (metricCountdown defaults false) keeps the thick ProgressStyle bar;
        // promotion still applies so the bar can surface as a live update.
        assertEquals(LiveUpdateStyle.PROGRESS, decision.style)
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
    fun `flat timer on api 37 uses the metric style big countdown`() {
        val decision = resolveLiveUpdateDecision(
            sdkInt = 37,
            title = "Deep work",
            text = "12:00 remaining",
            redactSensitiveTitles = false,
            promotedNotificationsAllowed = true,
            canPostPromotedNotifications = true,
            metricCountdown = true
        )

        assertEquals(LiveUpdateStyle.METRIC, decision.style)
        // Promotion still applies so the metric surfaces as a live update on the chip / AOD.
        assertTrue(decision.canUsePromotedOngoing)
    }

    @Test
    fun `segmented session keeps the progress bar even on api 37`() {
        // A split Pomodoro session leaves metricCountdown false so the per-phase segmented bar wins.
        val decision = resolveLiveUpdateDecision(
            sdkInt = 37,
            title = "Deep work",
            text = "12:00 remaining",
            redactSensitiveTitles = false,
            promotedNotificationsAllowed = true,
            canPostPromotedNotifications = true,
            metricCountdown = false
        )

        assertEquals(LiveUpdateStyle.PROGRESS, decision.style)
    }

    @Test
    fun `metric style is gated to api 37 — api 36 flat timer stays on progress`() {
        val decision = resolveLiveUpdateDecision(
            sdkInt = 36,
            title = "Deep work",
            text = "12:00 remaining",
            redactSensitiveTitles = false,
            promotedNotificationsAllowed = true,
            canPostPromotedNotifications = false,
            metricCountdown = true
        )

        assertEquals(LiveUpdateStyle.PROGRESS, decision.style)
    }

    @Test
    fun `promotion is withheld when promoted updates are disallowed even though the device permits them`() {
        // The redaction / opt-out path passes promotedNotificationsAllowed = false; the gate must
        // honour it regardless of the device-level permission so a withheld update never gets promoted.
        val decision = resolveLiveUpdateDecision(
            sdkInt = 37,
            title = "Deep work",
            text = "12:00 remaining",
            redactSensitiveTitles = false,
            promotedNotificationsAllowed = false,
            canPostPromotedNotifications = true
        )

        assertFalse(decision.canUsePromotedOngoing)
        // Style selection is independent of promotion — the bar still renders, just unpromoted.
        assertEquals(LiveUpdateStyle.PROGRESS, decision.style)
    }

    @Test
    fun `live countdown shows only for a running timer with time left`() {
        assertTrue(shouldUseLiveCountdown(isPaused = false, timeLeftSeconds = 25 * 60))
        // Paused must freeze — a live count-down chronometer would keep ticking past the pause.
        assertFalse(shouldUseLiveCountdown(isPaused = true, timeLeftSeconds = 25 * 60))
        // A finished or over-run timer has nothing left to count down.
        assertFalse(shouldUseLiveCountdown(isPaused = false, timeLeftSeconds = 0))
        assertFalse(shouldUseLiveCountdown(isPaused = false, timeLeftSeconds = -30))
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
    fun `paused sessions surface resume as primary action`() {
        assertEquals(NotificationPrimaryAction.RESUME, notificationPrimaryAction(isPaused = true))
    }

    @Test
    fun `running progress bar uses material you accent on android 12 plus and brand violet below`() {
        assertEquals(android.R.color.system_accent1_500, focusProgressBarColorRes(FocusBarState.RUNNING, sdkInt = 31))
        assertEquals(R.color.focus_progress_accent, focusProgressBarColorRes(FocusBarState.RUNNING, sdkInt = 30))
    }

    @Test
    fun `paused progress bar uses muted tone`() {
        assertEquals(android.R.color.system_neutral1_400, focusProgressBarColorRes(FocusBarState.PAUSED, sdkInt = 31))
        assertEquals(R.color.focus_progress_paused, focusProgressBarColorRes(FocusBarState.PAUSED, sdkInt = 30))
    }

    @Test
    fun `ending soon progress bar uses warm critical tint regardless of sdk`() {
        assertEquals(R.color.notification_accent_critical, focusProgressBarColorRes(FocusBarState.ENDING_SOON, sdkInt = 31))
        assertEquals(R.color.notification_accent_critical, focusProgressBarColorRes(FocusBarState.ENDING_SOON, sdkInt = 30))
    }

    @Test
    fun `bar state enters ending soon in the final minute but pause takes precedence`() {
        assertEquals(FocusBarState.RUNNING, focusBarState(isPaused = false, timeLeftSeconds = 25 * 60))
        assertEquals(FocusBarState.ENDING_SOON, focusBarState(isPaused = false, timeLeftSeconds = 60))
        assertEquals(FocusBarState.ENDING_SOON, focusBarState(isPaused = false, timeLeftSeconds = 1))
        // A finished/zeroed timer is not "ending soon" — it is about to clear.
        assertEquals(FocusBarState.RUNNING, focusBarState(isPaused = false, timeLeftSeconds = 0))
        // Pause overrides the ending-soon emphasis even in the final minute.
        assertEquals(FocusBarState.PAUSED, focusBarState(isPaused = true, timeLeftSeconds = 30))
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
