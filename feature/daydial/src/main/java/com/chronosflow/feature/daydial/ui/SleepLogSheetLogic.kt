package com.chronosflow.feature.daydial.ui

import com.chronosflow.core.ui.components.formatDisplayMinute

/**
 * Pure sleep-log helpers backing [SleepLogSheetContent]. Kept free of Compose so the duration
 * math, plausibility hints, and accessibility strings can be unit-tested directly
 * (see SleepLogSheetLogicTest).
 */

private const val MINUTES_PER_DAY = 24 * 60
private const val RECOMMENDED_SLEEP_MINUTES = 8 * 60
private const val MIN_PLAUSIBLE_MINUTES = 3 * 60
private const val MAX_PLAUSIBLE_MINUTES = 12 * 60
private const val DISRUPTED_INTERRUPTIONS = 3

private val sleepQualityLabels = listOf("Poor", "Fair", "Okay", "Good", "Great")

/** Minutes asleep from [bedMinute] to [wakeMinute], wrapping past midnight; 0 when they match. */
internal fun sleepDurationMinutes(bedMinute: Int, wakeMinute: Int): Int =
    ((wakeMinute - bedMinute) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY

/** Compact "7h 30m" / "8h" / "45m" rendering of a minute span. */
internal fun formatSleepDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}

/** "8h in bed" once both ends are set and the span is positive, else null. */
internal fun sleepDurationSummary(bedMinute: Int?, wakeMinute: Int?): String? {
    if (bedMinute == null || wakeMinute == null) return null
    val duration = sleepDurationMinutes(bedMinute, wakeMinute)
    if (duration <= 0) return null
    return "${formatSleepDuration(duration)} in bed"
}

/** Advisory nudge when the entered span looks implausible (or the two times match). */
internal fun sleepDurationHint(bedMinute: Int?, wakeMinute: Int?): String? {
    if (bedMinute == null || wakeMinute == null) return null
    if (bedMinute == wakeMinute) return "Bed and wake times match — set different times."
    val duration = sleepDurationMinutes(bedMinute, wakeMinute)
    return when {
        duration > MAX_PLAUSIBLE_MINUTES -> "Over 12 hours — double-check the AM/PM on your times."
        duration < MIN_PLAUSIBLE_MINUTES -> "That's a short night — check the times if that's not right."
        else -> null
    }
}

/** Word label for a 1–5 quality rating, clamping out-of-range input. */
internal fun sleepQualityLabel(quality: Int): String =
    sleepQualityLabels[quality.coerceIn(1, 5) - 1]

/** "11:00 PM → 7:00 AM (next day)" once both ends are set, else null. */
internal fun sleepWindowLabel(bedMinute: Int?, wakeMinute: Int?): String? {
    if (bedMinute == null || wakeMinute == null) return null
    val suffix = if (wakeMinute < bedMinute) " (next day)" else ""
    return "${formatDisplayMinute(bedMinute)} → ${formatDisplayMinute(wakeMinute)}$suffix"
}

/** How the span compares to an 8-hour night, e.g. "1h 30m short of 8 hours". */
internal fun sleepVsRecommendedLabel(bedMinute: Int?, wakeMinute: Int?): String? {
    if (bedMinute == null || wakeMinute == null) return null
    val duration = sleepDurationMinutes(bedMinute, wakeMinute)
    if (duration <= 0) return null
    val diff = duration - RECOMMENDED_SLEEP_MINUTES
    return when {
        diff == 0 -> "On target for 8 hours"
        diff < 0 -> "${formatSleepDuration(-diff)} short of 8 hours"
        else -> "${formatSleepDuration(diff)} over 8 hours"
    }
}

/** Screen-reader description pairing the rating number with its word label. */
internal fun sleepQualityContentDescription(quality: Int): String =
    "Sleep quality $quality of 5, ${sleepQualityLabel(quality)}"

/** One-word summary blending self-rated quality with interruption count. */
internal fun sleepRestfulnessLabel(quality: Int, interruptions: Int): String = when {
    interruptions >= DISRUPTED_INTERRUPTIONS -> "Disrupted night"
    quality >= 4 -> "Restful night"
    quality <= 2 -> "Rough night"
    else -> "Average night"
}

/** Pluralised interruption count for screen readers, floored at zero. */
internal fun sleepInterruptionsContentDescription(interruptions: Int): String {
    val count = interruptions.coerceAtLeast(0)
    return "$count ${if (count == 1) "interruption" else "interruptions"}"
}
