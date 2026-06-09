package com.chronosflow.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChronosNavGraphTaskRouteTest {
    @Test
    fun `tasks day dial shortcut always targets today`() {
        val route = taskScreenDayDialRoute()

        assertEquals(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TODAY),
            route
        )
        assertNotEquals(ChronosRoute.Day.createRoute(), route)
    }
}
