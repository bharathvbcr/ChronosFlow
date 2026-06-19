package com.ChronosFlow.VBCR.navigation

import com.ChronosFlow.VBCR.core.notifications.NotificationLaunch
import com.ChronosFlow.VBCR.core.notifications.SECTION_FOCUS
import com.ChronosFlow.VBCR.core.notifications.SECTION_MEDICATION
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationNavigationSpecTest {
    @Test
    fun `day launches resolve to the routed day tab`() {
        assertEquals(
            ChronosRoute.Day(ChronosRoute.Day.TARGET_TODAY),
            routeForNotificationLaunch(
                NotificationLaunch(section = com.ChronosFlow.VBCR.core.notifications.SECTION_DAY, dayTarget = ChronosRoute.Day.TARGET_TODAY)
            )
        )
    }

    @Test
    fun `focus launches resolve to the focus planner day tab`() {
        assertEquals(
            ChronosRoute.Day(ChronosRoute.Day.TARGET_FOCUS_PLANNER),
            routeForNotificationLaunch(
                NotificationLaunch(section = SECTION_FOCUS, focusBlockId = "block-42")
            )
        )
    }

    @Test
    fun `secondary sections resolve to their own top-level route`() {
        assertEquals(
            ChronosRoute.Medication(),
            routeForNotificationLaunch(NotificationLaunch(section = SECTION_MEDICATION))
        )
    }
}
