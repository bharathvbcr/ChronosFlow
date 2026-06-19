package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Structural guard for the focus-notification tint. Unlike the pure-helper tests, this builds the
 * real [android.app.Notification] and asserts its actual `color`, so a reintroduced double-`setColor`
 * (the merge-artifact bug fixed across the renderers/notifiers) — where the wrong setter wins — would
 * fail here. Pinned below API 36 so the deterministic compat path runs and `notification.color`
 * reflects the bar state directly (on the live path the chrome color stays the steady running accent).
 */
@RunWith(RobolectricTestRunner::class)
class FocusNotificationColorRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun renderer(): FocusProgressNotificationRenderer {
        val gateway = LiveUpdateGateway(context)
        return FocusProgressNotificationRenderer(context, LiveUpdateRenderer(gateway))
    }

    private fun colorFor(timeLeftSeconds: Int, totalSeconds: Int, isPaused: Boolean): Int =
        renderer().build(
            channelId = "test_focus_channel",
            title = "Deep work",
            text = "remaining",
            timeLeftSeconds = timeLeftSeconds,
            totalSeconds = totalSeconds,
            isPaused = isPaused
        ).color

    @Config(sdk = [34])
    @Test
    fun `compat renderer tints notification per bar state on android 12 plus`() {
        val running = colorFor(timeLeftSeconds = 25 * 60, totalSeconds = 25 * 60, isPaused = false)
        val paused = colorFor(timeLeftSeconds = 25 * 60, totalSeconds = 25 * 60, isPaused = true)
        val endingSoon = colorFor(timeLeftSeconds = 30, totalSeconds = 25 * 60, isPaused = false)

        // Each state must render the color its resource selector resolves to.
        assertEquals(expected(FocusBarState.RUNNING), running)
        assertEquals(expected(FocusBarState.PAUSED), paused)
        assertEquals(expected(FocusBarState.ENDING_SOON), endingSoon)

        // The final stretch is always the warm critical tint, and the states are visually distinct.
        assertEquals(ContextCompat.getColor(context, R.color.notification_accent_critical), endingSoon)
        assertNotEquals(running, paused)
        assertNotEquals(running, endingSoon)
    }

    @Config(sdk = [29])
    @Test
    fun `compat renderer falls back to brand violet and grey below android 12`() {
        val running = colorFor(timeLeftSeconds = 25 * 60, totalSeconds = 25 * 60, isPaused = false)
        val paused = colorFor(timeLeftSeconds = 25 * 60, totalSeconds = 25 * 60, isPaused = true)

        assertEquals(ContextCompat.getColor(context, R.color.focus_progress_accent), running)
        assertEquals(ContextCompat.getColor(context, R.color.focus_progress_paused), paused)
    }

    private fun expected(state: FocusBarState): Int =
        ContextCompat.getColor(context, focusProgressBarColorRes(state, Build.VERSION.SDK_INT))
}
