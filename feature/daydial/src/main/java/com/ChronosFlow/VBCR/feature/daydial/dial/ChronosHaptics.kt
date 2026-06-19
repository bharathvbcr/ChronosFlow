package com.ChronosFlow.VBCR.feature.daydial.dial

import com.ChronosFlow.VBCR.feature.daydial.model.PlannerHapticCue

enum class ChronosHapticCue {
    SNAP,
    WARNING_CONFLICT,
    LOCKED_CONFLICT,
    SUCCESSFUL_COMMIT,
    COMMAND_ACCEPTED,
    COMMAND_REJECTED
}

object ChronosHaptics {
    fun fromPlannerCue(cue: PlannerHapticCue): ChronosHapticCue? = when (cue) {
        PlannerHapticCue.SNAP -> ChronosHapticCue.SNAP
        PlannerHapticCue.CONFLICT_BOUNDARY -> ChronosHapticCue.WARNING_CONFLICT
        PlannerHapticCue.LOCKED_COLLISION -> ChronosHapticCue.LOCKED_CONFLICT
        PlannerHapticCue.SUCCESSFUL_DROP -> ChronosHapticCue.SUCCESSFUL_COMMIT
        PlannerHapticCue.NONE -> null
    }

    fun shouldPerform(cue: ChronosHapticCue, hapticsEnabled: Boolean, reducedMotion: Boolean): Boolean {
        if (!hapticsEnabled) return false
        return when (cue) {
            ChronosHapticCue.SNAP -> true
            ChronosHapticCue.WARNING_CONFLICT,
            ChronosHapticCue.LOCKED_CONFLICT,
            ChronosHapticCue.SUCCESSFUL_COMMIT -> !reducedMotion
            ChronosHapticCue.COMMAND_ACCEPTED,
            ChronosHapticCue.COMMAND_REJECTED -> true
        }
    }
}
