package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the notification body formatters — in particular the privacy-redaction fallback, where a
 * redacted session must drop the real countdown/title in favor of the generic [REDACTED_BODY]. The
 * formatters need a [Context] for their string resources, so this runs under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusNotificationContentBodyTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `running body shows remaining time when not redacted`() {
        assertEquals(
            "25:00 remaining",
            FocusNotificationContent.runningBody(context, timeLeftSeconds = 25 * 60, redactSensitiveTitles = false)
        )
    }

    @Test
    fun `paused body shows paused prefix and remaining time when not redacted`() {
        assertEquals(
            "Paused · 25:00 remaining",
            FocusNotificationContent.pausedBody(context, timeLeftSeconds = 25 * 60, redactSensitiveTitles = false)
        )
    }

    @Test
    fun `both bodies fall back to the generic redacted string when titles are redacted`() {
        assertEquals(
            FocusNotificationContent.REDACTED_BODY,
            FocusNotificationContent.runningBody(context, timeLeftSeconds = 25 * 60, redactSensitiveTitles = true)
        )
        assertEquals(
            FocusNotificationContent.REDACTED_BODY,
            FocusNotificationContent.pausedBody(context, timeLeftSeconds = 25 * 60, redactSensitiveTitles = true)
        )
    }
}
