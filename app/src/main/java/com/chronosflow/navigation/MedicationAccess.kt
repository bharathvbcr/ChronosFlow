package com.chronosflow.navigation

import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.chronosflow.AppLockViewModel
import java.net.URLDecoder

@Suppress("UNUSED_PARAMETER")
fun NavHostController.navigateToMedication(
    activity: FragmentActivity,
    appLockViewModel: AppLockViewModel,
    target: String? = null,
    capture: String? = null
) {
    // SensitiveRouteGate owns the unlock UI; entering the route avoids a no-op when device auth is unavailable.
    val route = ChronosRoute.Medication.createRoute(target = target, capture = capture)
    navigateSingleTop(route)
}

fun guardedMedicationOpener(
    activity: FragmentActivity,
    appLockViewModel: AppLockViewModel,
    navController: NavHostController,
    target: String? = null,
    capture: String? = null
): () -> Unit = {
    navController.navigateToMedication(activity, appLockViewModel, target, capture)
}

fun isMedicationRoute(route: String): Boolean =
    route == ChronosRoute.Medication.route ||
        route.startsWith("${ChronosRoute.Medication.route}?")

fun NavHostController.navigateShellRoute(
    activity: FragmentActivity,
    appLockViewModel: AppLockViewModel,
    route: String
) {
    if (isMedicationRoute(route)) {
        val target = route.queryValue("target")
        val capture = route.queryValue("capture")
        navigateToMedication(activity, appLockViewModel, target, capture)
    } else {
        navigateSingleTop(route)
    }
}

private fun String.queryValue(name: String): String? {
    val query = substringAfter('?', missingDelimiterValue = "")
    if (query.isBlank()) return null
    return query
        .split('&')
        .firstOrNull { part -> part.substringBefore('=') == name }
        ?.substringAfter('=', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
}
