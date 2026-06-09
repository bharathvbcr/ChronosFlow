package com.chronosflow.navigation

import android.app.Application
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ChronosNavigationBackStackTest {
    @Test
    fun `review day target keeps previous day entry for system back`() {
        val navController = testNavController()

        navController.navigateDayTarget(ChronosRoute.Day.TARGET_INSIGHTS)

        assertCurrentDayTarget(navController, ChronosRoute.Day.TARGET_INSIGHTS)
        assertTrue(navController.popBackStack())
        assertCurrentDayTarget(navController, null)
    }

    @Test
    fun `insights shell route keeps previous day entry for system back`() {
        val navController = testNavController()

        navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS))

        assertCurrentDayTarget(navController, ChronosRoute.Day.TARGET_INSIGHTS)
        assertTrue(navController.popBackStack())
        assertCurrentDayTarget(navController, null)
    }

    private fun testNavController(): TestNavHostController {
        val navController = TestNavHostController(ApplicationProvider.getApplicationContext<Application>())
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        navController.graph = navController.createGraph(startDestination = ChronosRoute.Day.route) {
            composable(
                route = ChronosRoute.Day.route,
                arguments = listOf(navArgument("target") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) {}
            composable(ChronosRoute.Tasks.route) {}
        }
        return navController
    }

    private fun assertCurrentDayTarget(navController: TestNavHostController, expectedTarget: String?) {
        assertEquals(ChronosRoute.Day.route, navController.currentDestination?.route)
        assertEquals(expectedTarget, navController.currentBackStackEntry?.arguments?.getString("target"))
        if (expectedTarget == null) {
            assertNull(navController.currentBackStackEntry?.arguments?.getString("target"))
        }
    }
}
