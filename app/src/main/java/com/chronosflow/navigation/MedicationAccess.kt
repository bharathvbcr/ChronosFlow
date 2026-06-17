package com.chronosflow.navigation

import androidx.fragment.app.FragmentActivity
import com.chronosflow.AppLockViewModel

/**
 * Navigate to the medication section. [SensitiveRouteGate] (rendered inside the Medication entry)
 * owns the unlock UI, so entering the route is safe even when device auth is unavailable. The
 * [activity] / [appLockViewModel] parameters are retained for call-site compatibility.
 */
@Suppress("UNUSED_PARAMETER")
fun ChronosNavigationState.navigateToMedication(
    activity: FragmentActivity,
    appLockViewModel: AppLockViewModel,
    target: String? = null,
    capture: String? = null
) {
    navigate(ChronosRoute.Medication(target = target, capture = capture))
}

@Suppress("UNUSED_PARAMETER")
fun guardedMedicationOpener(
    activity: FragmentActivity,
    appLockViewModel: AppLockViewModel,
    navState: ChronosNavigationState,
    target: String? = null,
    capture: String? = null
): () -> Unit = {
    navState.navigateToMedication(activity, appLockViewModel, target, capture)
}

fun isMedicationRoute(route: ChronosRoute): Boolean = route is ChronosRoute.Medication
