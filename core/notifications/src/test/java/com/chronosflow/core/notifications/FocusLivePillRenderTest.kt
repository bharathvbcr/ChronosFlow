package com.chronosflow.core.notifications

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Structural guard for the API-36 live-update pill (`shortCriticalText`). The pill is fed by
 * [FocusNotificationContent.pillText] after a redundant `setShortCriticalText` override was removed;
 * this asserts the built notification's actual pill text so reintroducing that override (which
 * dropped "Paused" and flattened the final-minute seconds) would fail here. Pinned to SDK 36 so the
 * live ProgressStyle path runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusLivePillRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun pillFor(timeLeftSeconds: Int, isPaused: Boolean): String? {
        val gateway = LiveUpdateGateway(context)
        val renderer = FocusProgressNotificationRenderer(context, LiveUpdateRenderer(gateway))
        return renderer.build(
            channelId = "test_focus_channel",
            title = "Deep work",
            text = "remaining",
            timeLeftSeconds = timeLeftSeconds,
            totalSeconds = 25 * 60,
            isPaused = isPaused
        ).shortCriticalText?.toString()
    }

    @Test
    fun `pill shows Paused when paused`() {
        assertEquals(
            context.getString(R.string.focus_notification_pill_paused),
            pillFor(timeLeftSeconds = 25 * 60, isPaused = true)
        )
    }

    @Test
    fun `pill shows magnitude-aware time while running`() {
        assertEquals("12m", pillFor(timeLeftSeconds = 12 * 60, isPaused = false))
    }

    @Test
    fun `pill counts down by seconds in the final minute`() {
        assertEquals("45s", pillFor(timeLeftSeconds = 45, isPaused = false))
    }

    private fun chromeColorFor(timeLeftSeconds: Int, isPaused: Boolean): Int {
        val gateway = LiveUpdateGateway(context)
        val renderer = FocusProgressNotificationRenderer(context, LiveUpdateRenderer(gateway))
        return renderer.build(
            channelId = "test_focus_channel",
            title = "Deep work",
            text = "remaining",
            timeLeftSeconds = timeLeftSeconds,
            totalSeconds = 25 * 60,
            isPaused = isPaused
        ).color
    }

    @Test
    fun `live path keeps chrome on the running accent regardless of state`() {
        // On the live path the segment carries the state color; the notification chrome stays on
        // the steady running accent (deliberately unlike the compat path). Guards that decision.
        val runningAccent =
            ContextCompat.getColor(context, focusProgressBarColorRes(FocusBarState.RUNNING, Build.VERSION.SDK_INT))
        assertEquals(runningAccent, chromeColorFor(timeLeftSeconds = 25 * 60, isPaused = false))
        assertEquals(runningAccent, chromeColorFor(timeLeftSeconds = 25 * 60, isPaused = true))
        assertEquals(runningAccent, chromeColorFor(timeLeftSeconds = 30, isPaused = false))
    }
}
