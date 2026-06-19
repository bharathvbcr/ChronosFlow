package com.ChronosFlow.VBCR.core.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Predictive-back handler for local UI surfaces that are not owned by Navigation Compose.
 *
 * Route-level back should normally be handled by NavHost so Navigation Compose can drive
 * scrubbed pop transitions. Use this wrapper for transient surfaces such as drawers,
 * menus, and sheets that need custom lifecycle callbacks.
 */
@Composable
fun ChronosPredictiveBackHandler(
    enabled: Boolean,
    onBackStarted: (BackEventCompat) -> Unit = {},
    onBackProgressed: (BackEventCompat) -> Unit = {},
    onBackCancelled: () -> Unit = {},
    onBackInvoked: () -> Unit
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        var started = false
        try {
            progress.collect { backEvent ->
                if (!started) {
                    started = true
                    onBackStarted(backEvent)
                }
                onBackProgressed(backEvent)
            }
            onBackInvoked()
        } catch (e: CancellationException) {
            onBackCancelled()
            throw e
        }
    }
}

/**
 * Compatibility alias for callers that use a progress-aware local surface.
 *
 * [onBackStarted] is called on the first [BackEventCompat].
 * [onBackProgressed] is called continuously with the current [BackEventCompat].
 * [onBackInvoked] is called once the user commits the gesture.
 * [onBackCancelled] is called if the user reverses the gesture before committing.
 */
@Composable
fun ChronosPredictiveBackHandlerWithProgress(
    enabled: Boolean,
    onBackStarted: (BackEventCompat) -> Unit = {},
    onBackProgressed: (BackEventCompat) -> Unit = {},
    onBackCancelled: () -> Unit = {},
    onBackInvoked: () -> Unit
) {
    ChronosPredictiveBackHandler(
        enabled = enabled,
        onBackStarted = onBackStarted,
        onBackProgressed = onBackProgressed,
        onBackCancelled = onBackCancelled,
        onBackInvoked = onBackInvoked
    )
}
