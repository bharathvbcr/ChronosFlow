package com.chronosflow.core.notifications

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationPermissionsTest {
    @Test
    fun `required permissions include post notifications on api 33`() {
        assertArrayEquals(
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            NotificationPermissions.requiredPermissionsForSdk(33)
        )
    }

    @Test
    fun `required permissions include promoted permission on api 37`() {
        assertArrayEquals(
            arrayOf(
                android.Manifest.permission.POST_NOTIFICATIONS,
                NotificationPermissions.POST_PROMOTED_NOTIFICATIONS
            ),
            NotificationPermissions.requiredPermissionsForSdk(37)
        )
    }

    @Test
    fun `result message reports enabled when standard permission granted`() {
        val context = RuntimeEnvironment.getApplication()
        val message = NotificationPermissions.resultMessage(
            context,
            mapOf(android.Manifest.permission.POST_NOTIFICATIONS to true)
        )

        assertEquals(context.getString(R.string.notification_permission_enabled), message)
    }
}
