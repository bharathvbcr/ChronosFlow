package com.chronosflow.navigation

import androidx.navigation.NavHostController
import com.chronosflow.core.notifications.NotificationLaunch
import com.chronosflow.core.ui.settings.ChronosFeatureFlags

internal data class NotificationNavigationSpec(
    val route: String,
    val popUpToRoute: String,
    val inclusive: Boolean
)

internal fun buildNotificationNavigationSpec(
    launch: NotificationLaunch,
    featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
): NotificationNavigationSpec {
    val route = ChronosRoute.routeForNotificationLaunch(launch, featureFlags)
    val isDayLaunch = launch.section == ChronosRoute.Day.section
    return NotificationNavigationSpec(
        route = route,
        popUpToRoute = ChronosRoute.Day.route,
        inclusive = isDayLaunch
    )
}

fun NavHostController.navigateFromNotificationLaunch(
    launch: NotificationLaunch,
    featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
) {
    val spec = buildNotificationNavigationSpec(launch, featureFlags)
    navigate(spec.route) {
        launchSingleTop = false
        restoreState = false
        popUpTo(spec.popUpToRoute) {
            inclusive = spec.inclusive
            saveState = false
        }
    }
}
