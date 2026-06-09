package com.chronosflow

import android.app.Application
import android.content.Intent
import com.chronosflow.core.notifications.EXTRA_DAY_TARGET
import com.chronosflow.core.notifications.EXTRA_INITIAL_SECTION
import com.chronosflow.core.notifications.NotificationLaunch
import com.chronosflow.core.notifications.SECTION_MEDICATION
import com.chronosflow.navigation.ChronosRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class NotificationNavigationPlanTest {
    @Test
    fun `notification launch keeps default start route and defers navigation target`() {
        val intent = Intent().apply {
            putExtra(EXTRA_INITIAL_SECTION, SECTION_MEDICATION)
        }

        val plan = buildNotificationNavigationPlan(intent)

        assertEquals(ChronosRoute.Day.createRoute(), plan.startRoute)
        assertEquals(NotificationLaunch(section = SECTION_MEDICATION), plan.notificationLaunch)
    }

    @Test
    fun `regular app launch keeps default start route without notification target`() {
        val intent = Intent().apply {
            putExtra(EXTRA_DAY_TARGET, ChronosRoute.Day.TARGET_PLAN)
        }

        val plan = buildNotificationNavigationPlan(intent)

        assertEquals(ChronosRoute.Day.createRoute(), plan.startRoute)
        assertNull(plan.notificationLaunch)
    }
}
