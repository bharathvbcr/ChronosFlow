package com.ChronosFlow.VBCR.navigation

import com.ChronosFlow.VBCR.core.notifications.NotificationLaunch
import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags

/**
 * Resolve the type-safe route a notification launch should navigate to. Day-target launches resolve
 * to the Day key with the requested tab; everything else maps to its top-level section.
 */
internal fun routeForNotificationLaunch(
    launch: NotificationLaunch,
    featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
): ChronosRoute = ChronosRoute.routeForNotificationLaunch(launch, featureFlags)

fun ChronosNavigationState.navigateFromNotificationLaunch(
    launch: NotificationLaunch,
    featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
) {
    navigate(routeForNotificationLaunch(launch, featureFlags))
}
