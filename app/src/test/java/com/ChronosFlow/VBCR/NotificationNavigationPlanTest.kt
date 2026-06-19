package com.ChronosFlow.VBCR

import android.app.Application
import android.content.Intent
import com.ChronosFlow.VBCR.core.notifications.EXTRA_DAY_TARGET
import com.ChronosFlow.VBCR.core.notifications.EXTRA_INITIAL_SECTION
import com.ChronosFlow.VBCR.core.notifications.NotificationLaunch
import com.ChronosFlow.VBCR.core.notifications.SECTION_MEDICATION
import com.ChronosFlow.VBCR.core.notifications.SECTION_TASKS
import com.ChronosFlow.VBCR.core.notifications.TASK_LAUNCH_TARGET_ADD
import com.ChronosFlow.VBCR.navigation.ChronosRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        assertEquals(NotificationLaunch(section = SECTION_MEDICATION), plan.notificationLaunch)
    }

    @Test
    fun `regular app launch keeps default start route without notification target`() {
        val intent = Intent().apply {
            putExtra(EXTRA_DAY_TARGET, ChronosRoute.Day.TARGET_PLAN)
        }

        val plan = buildNotificationNavigationPlan(intent)

        assertNull(plan.notificationLaunch)
    }

    @Test
    fun `shared text from another app opens task capture sheet`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Pick up dry cleaning tomorrow")
        }

        val plan = buildNotificationNavigationPlan(intent)
        val launch = plan.notificationLaunch

        assertNotNull(launch)
        assertEquals(SECTION_TASKS, launch!!.section)
        assertEquals(TASK_LAUNCH_TARGET_ADD, launch.target)
        assertEquals("Pick up dry cleaning tomorrow", launch.capture)
        // The capture must route to the Tasks add sheet, pre-filled.
        val route = ChronosRoute.routeForNotificationLaunch(launch) as ChronosRoute.Tasks
        assertEquals(ChronosRoute.TARGET_ADD, route.target)
        assertEquals("Pick up dry cleaning tomorrow", route.capture)
    }

    @Test
    fun `process-text selection opens task capture sheet`() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, "Call the dentist")
        }

        val launch = buildNotificationNavigationPlan(intent).notificationLaunch

        assertNotNull(launch)
        assertEquals(SECTION_TASKS, launch!!.section)
        assertEquals("Call the dentist", launch.capture)
    }
}
