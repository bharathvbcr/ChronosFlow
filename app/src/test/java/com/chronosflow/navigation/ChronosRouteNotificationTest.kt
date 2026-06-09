package com.chronosflow.navigation

import com.chronosflow.core.notifications.NotificationLaunch
import com.chronosflow.core.notifications.SECTION_DAY
import com.chronosflow.core.notifications.SECTION_FOCUS
import com.chronosflow.core.notifications.SECTION_MEDICATION
import com.chronosflow.core.notifications.SECTION_REVIEW
import com.chronosflow.core.notifications.SECTION_TASKS
import com.chronosflow.core.notifications.TASK_LAUNCH_TARGET_CONTEXT
import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosRouteNotificationTest {
    @Test
    fun `notification launch maps to nav routes`() {
        assertEquals(
            ChronosRoute.Medication.route,
            ChronosRoute.routeForNotificationLaunch(NotificationLaunch(SECTION_MEDICATION))
        )
        assertEquals(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS),
            ChronosRoute.routeForNotificationLaunch(NotificationLaunch(SECTION_REVIEW))
        )
        assertEquals(
            ChronosRoute.Tasks.route,
            ChronosRoute.routeForNotificationLaunch(NotificationLaunch(SECTION_TASKS))
        )
        assertEquals(
            "${SECTION_TASKS}?taskId=task-9&target=${TASK_LAUNCH_TARGET_CONTEXT}",
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(
                    section = SECTION_TASKS,
                    taskId = "task-9",
                    target = TASK_LAUNCH_TARGET_CONTEXT
                )
            )
        )
        assertEquals(
            "${ChronosRoute.Day.section}?target=${ChronosRoute.Day.TARGET_FOCUS_PLANNER}",
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(SECTION_FOCUS, focusBlockId = "block-9")
            )
        )
        assertEquals(
            "${SECTION_DAY}?target=today",
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(SECTION_DAY, dayTarget = "today")
            )
        )
    }
}
