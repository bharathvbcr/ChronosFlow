package com.chronosflow.core.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Structural guard for [FocusCompletionNotifier]: posts the real notifications and asserts the
 * captured `color` is the focus accent. The completion/boundary builders previously carried a dead
 * `setColor(chronosflow_brand_accent)` immediately overridden by `setColor(notification_accent)`;
 * this fails if such a double-setter is reintroduced with the wrong color winning.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusCompletionNotifierColorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun postedColor(): Int {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(manager).allNotifications
        assertEquals(1, posted.size)
        return posted[0].color
    }

    @Test
    fun `completion notification uses the focus accent`() {
        FocusCompletionNotifier.show(
            context = context,
            blockTitle = "Deep work",
            redactSensitiveTitles = false,
            nextStepLine = null
        )
        assertEquals(ContextCompat.getColor(context, R.color.notification_accent), postedColor())
    }

    @Test
    fun `phase boundary notification uses the focus accent`() {
        FocusCompletionNotifier.showPhaseBoundary(
            context = context,
            blockTitle = "Deep work",
            body = "Time for a 5m break — tap to continue",
            redactSensitiveTitles = false
        )
        assertEquals(ContextCompat.getColor(context, R.color.notification_accent), postedColor())
    }
}
