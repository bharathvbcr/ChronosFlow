package com.chronosflow.navigation

import com.chronosflow.core.notifications.NotificationLaunch
import com.chronosflow.core.notifications.SECTION_DAY
import com.chronosflow.core.notifications.SECTION_FOCUS
import com.chronosflow.core.notifications.SECTION_MEDICATION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationNavigationSpecTest {
    @Test
    fun `day launches replace existing day destination`() {
        val spec = buildNotificationNavigationSpec(
            NotificationLaunch(section = SECTION_DAY, dayTarget = ChronosRoute.Day.TARGET_TODAY)
        )

        assertEquals("${SECTION_DAY}?target=today", spec.route)
        assertEquals(ChronosRoute.Day.route, spec.popUpToRoute)
        assertTrue(spec.inclusive)
    }

    @Test
    fun `focus launches rebuild destination above day`() {
        val spec = buildNotificationNavigationSpec(
            NotificationLaunch(section = SECTION_FOCUS, focusBlockId = "block-42")
        )

        assertEquals(
            "${SECTION_DAY}?target=${ChronosRoute.Day.TARGET_FOCUS_PLANNER}",
            spec.route
        )
        assertEquals(ChronosRoute.Day.route, spec.popUpToRoute)
        assertFalse(spec.inclusive)
    }

    @Test
    fun `secondary sections preserve day as the back stack anchor`() {
        val spec = buildNotificationNavigationSpec(
            NotificationLaunch(section = SECTION_MEDICATION)
        )

        assertEquals(ChronosRoute.Medication.route, spec.route)
        assertEquals(ChronosRoute.Day.route, spec.popUpToRoute)
        assertFalse(spec.inclusive)
    }
}
