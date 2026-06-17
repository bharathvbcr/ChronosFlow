package com.chronosflow.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Runs [onTrigger] exactly once when a one-shot navigation target becomes [active], and re-fires it
 * whenever [requestGeneration] changes — even if [active] and the [discriminators] are unchanged.
 *
 * Why the generation matters: a sub-section's transient target (e.g. the "add" sheet request) rides
 * in its Navigation 3 `NavKey` (`Tasks(target = "add")`). Re-navigating to the same target therefore
 * produces a *value-equal* key that `NavDisplay` treats as "no change" — the entry never recomposes,
 * so a one-shot guarded only by the key silently stops firing on the second and later requests (the
 * "add FAB does nothing the second time" bug). The caller bumps [requestGeneration]
 * (`ChronosNavigationState.sectionTargetGeneration`) on every sub-section navigate so the one-shot
 * still re-fires. See `docs/navigation-architecture.md`.
 *
 * The consumed flag is [rememberSaveable] so a configuration change / process death does **not**
 * re-fire it (which would reopen a sheet the user already dismissed); only a genuine new request
 * (generation or [discriminators] change) re-fires.
 *
 * [discriminators] are extra inputs that should reset the one-shot when they change (e.g. a prefill
 * capture string). For targets that must wait for asynchronous state before they can act (e.g. a
 * context sheet that needs its item loaded first), keep that retry logic inline rather than using
 * this helper — it always consumes on the first fire.
 */
@Composable
fun OneShotNavTrigger(
    active: Boolean,
    requestGeneration: Int,
    vararg discriminators: Any?,
    onTrigger: () -> Unit,
) {
    var consumed by rememberSaveable(active, requestGeneration, *discriminators) {
        mutableStateOf(false)
    }
    LaunchedEffect(active, requestGeneration, consumed, *discriminators) {
        if (!active || consumed) return@LaunchedEffect
        consumed = true
        onTrigger()
    }
}
