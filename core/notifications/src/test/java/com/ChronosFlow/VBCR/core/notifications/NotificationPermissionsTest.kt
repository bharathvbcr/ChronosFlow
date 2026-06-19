package com.ChronosFlow.VBCR.core.notifications

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `promotion nudge fires only when standard granted but promotion still missing on api 37`() {
        // The gap that actually keeps the live update off the chip / always-on display.
        assertTrue(
            NotificationPermissions.shouldNudgeForPromotion(
                sdkInt = 37,
                hasStandardPermission = true,
                hasPromotedPermission = false
            )
        )
        // Already promoted — nothing to nudge.
        assertFalse(
            NotificationPermissions.shouldNudgeForPromotion(
                sdkInt = 37,
                hasStandardPermission = true,
                hasPromotedPermission = true
            )
        )
        // No standard permission yet — the standard request comes first, so do not nudge for promotion.
        assertFalse(
            NotificationPermissions.shouldNudgeForPromotion(
                sdkInt = 37,
                hasStandardPermission = false,
                hasPromotedPermission = false
            )
        )
        // Below API 37 there is no promotion to grant.
        assertFalse(
            NotificationPermissions.shouldNudgeForPromotion(
                sdkInt = 36,
                hasStandardPermission = true,
                hasPromotedPermission = false
            )
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
