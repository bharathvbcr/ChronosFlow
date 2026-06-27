package com.ChronosFlow.VBCR.feature.daydial.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import com.ChronosFlow.VBCR.feature.focus.FocusService
import com.ChronosFlow.VBCR.feature.focus.sendFocusServiceCommand
import java.util.UUID

/**
 * Debug-only helpers that drive the live focus notification through a few-second phase boundary and
 * completion, so the in-place handoff (live timer → "tap to continue" prompt → completion, all on the
 * single notification id 4201 / `chronos_focus_timer` channel) can be eyeballed on a device without
 * waiting out a real Pomodoro interval.
 *
 * These start [FocusService] directly (no in-app session), so tapping the boundary prompt won't run a
 * "next phase" — the point is to verify the notification visuals + single-channel behavior. The full
 * tap-to-continue advance is covered by the FocusPhaseAdvanceBus path + its unit tests.
 *
 * Gated behind [isDebuggableBuild] at the call site so they never reach a release build.
 */

/** True only for debuggable builds — gates the demo actions out of release. */
internal fun Context.isDebuggableBuild(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

/**
 * Starts a ~[seconds]-second intermediate split phase. After it elapses the service transitions the
 * live notification into the "tap to continue" boundary prompt in place.
 */
internal fun Context.startFocusSplitBoundaryDemo(seconds: Int = 5) {
    sendFocusServiceCommand(
        action = FocusService.ACTION_START,
        timeLeft = seconds,
        totalSeconds = seconds,
        sessionId = "focus-demo-${UUID.randomUUID()}",
        terminal = false,
        boundaryLabel = "Time for a 5m break — tap to continue",
        phasePlan = "F1,B1,F1",
        phaseIndex = 0
    )
}

/**
 * Starts a ~[seconds]-second terminal phase. After it elapses the service folds the completion
 * celebration into the same live notification slot.
 */
internal fun Context.startFocusCompletionDemo(seconds: Int = 5) {
    sendFocusServiceCommand(
        action = FocusService.ACTION_START,
        timeLeft = seconds,
        totalSeconds = seconds,
        sessionId = "focus-demo-${UUID.randomUUID()}",
        terminal = true
    )
}
