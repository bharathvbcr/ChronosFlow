package com.ChronosFlow.VBCR.feature.focus

/**
 * Elapsed focus minutes from timer snapshot (at least 1, capped by planned block length).
 */
internal fun focusElapsedMinutes(
    totalSeconds: Int,
    timeLeftSeconds: Int,
    plannedDurationMinutes: Int
): Int {
    val elapsedSeconds = (totalSeconds - timeLeftSeconds).coerceAtLeast(60)
    return (elapsedSeconds / 60)
        .coerceAtLeast(1)
        .coerceAtMost(plannedDurationMinutes.coerceAtLeast(1))
}
