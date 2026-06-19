package com.ChronosFlow.VBCR.navigation

import com.ChronosFlow.VBCR.core.notifications.NotificationLaunch
import com.ChronosFlow.VBCR.core.notifications.SECTION_DAY
import com.ChronosFlow.VBCR.core.notifications.SECTION_FOCUS
import com.ChronosFlow.VBCR.core.notifications.SECTION_MEDICATION
import com.ChronosFlow.VBCR.core.notifications.SECTION_REVIEW
import com.ChronosFlow.VBCR.core.notifications.SECTION_TASKS
import com.ChronosFlow.VBCR.core.notifications.TASK_LAUNCH_TARGET_CONTEXT
import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosRouteNotificationTest {
    @Test
    fun `notification launch maps to nav routes`() {
        assertEquals(
            ChronosRoute.Medication(),
            ChronosRoute.routeForNotificationLaunch(NotificationLaunch(SECTION_MEDICATION))
        )
        assertEquals(
            ChronosRoute.Day(ChronosRoute.Day.TARGET_INSIGHTS),
            ChronosRoute.routeForNotificationLaunch(NotificationLaunch(SECTION_REVIEW))
        )
        assertEquals(
            ChronosRoute.Tasks(),
            ChronosRoute.routeForNotificationLaunch(NotificationLaunch(SECTION_TASKS))
        )
        assertEquals(
            ChronosRoute.Tasks(taskId = "task-9", target = TASK_LAUNCH_TARGET_CONTEXT),
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(
                    section = SECTION_TASKS,
                    taskId = "task-9",
                    target = TASK_LAUNCH_TARGET_CONTEXT
                )
            )
        )
        assertEquals(
            ChronosRoute.Day(ChronosRoute.Day.TARGET_FOCUS_PLANNER),
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(SECTION_FOCUS, focusBlockId = "block-9")
            )
        )
        assertEquals(
            ChronosRoute.Day("today"),
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(SECTION_DAY, dayTarget = "today")
            )
        )
    }
}
