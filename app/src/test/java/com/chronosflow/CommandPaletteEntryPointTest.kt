package com.chronosflow

import com.chronosflow.navigation.ChronosRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteEntryPointTest {
    @Test
    fun `floating palette action covers non day visible routes`() {
        assertFalse(shouldShowFloatingCommandPaletteAction(null))
        assertFalse(shouldShowFloatingCommandPaletteAction(ChronosRoute.Day.route))

        listOf(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER),
            ChronosRoute.Tasks.route,
            ChronosRoute.Habits.route,
            ChronosRoute.Medication.route,
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS)
        ).forEach { route ->
            assertTrue("Expected floating palette action for $route", shouldShowFloatingCommandPaletteAction(route))
        }
    }
}

