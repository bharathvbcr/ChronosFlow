package com.ChronosFlow.VBCR.core.domain.model

import java.time.LocalDate

/**
 * A day's screen-time totals derived from Android usage stats, split by [UsageCategory].
 * Stored per day so Insights can chart "focused vs. wasted time" trends without re-querying
 * the system on every render.
 */
data class AppUsageDay(
    val date: LocalDate,
    val productiveMinutes: Int,
    val distractingMinutes: Int,
    val neutralMinutes: Int
) {
    val totalMinutes: Int get() = productiveMinutes + distractingMinutes + neutralMinutes

    /** Share of tracked screen time spent in productive apps, 0f..1f (0 when nothing was tracked). */
    val focusRatio: Float
        get() = if (totalMinutes == 0) 0f else productiveMinutes.toFloat() / totalMinutes
}

/**
 * Per-app foreground time for a single day. Surfaced live for the current day (top apps) but not
 * persisted — only the aggregated [AppUsageDay] is stored.
 */
data class AppUsageSample(
    val packageName: String,
    val label: String,
    val category: UsageCategory,
    val minutes: Int
)

/** Focus-ratio comparison between the earlier and recent halves of a window, as whole percents. */
data class FocusRatioTrend(
    val earlierPercent: Int,
    val recentPercent: Int
) {
    val deltaPercent: Int get() = recentPercent - earlierPercent
    val isImproving: Boolean get() = deltaPercent > 0
}

/**
 * Direction of focus over an ascending-by-date [days] window: the focus ratio of its recent half vs
 * its earlier half. Returns null when the window is too short or either half has no tracked time,
 * so callers can hide the insight rather than show a misleading 0%.
 */
fun focusRatioTrend(days: List<AppUsageDay>): FocusRatioTrend? {
    if (days.size < 2) return null
    val mid = days.size / 2
    val earlier = halfFocusPercent(days.subList(0, mid)) ?: return null
    val recent = halfFocusPercent(days.subList(mid, days.size)) ?: return null
    return FocusRatioTrend(earlierPercent = earlier, recentPercent = recent)
}

private fun halfFocusPercent(days: List<AppUsageDay>): Int? {
    val total = days.sumOf { it.totalMinutes }
    if (total == 0) return null
    return days.sumOf { it.productiveMinutes } * 100 / total
}

/** Today's distracting time alongside the window's usual (other-days) average, for a nudge. */
data class DistractionNudge(
    val todayDistractingMinutes: Int,
    val averageDistractingMinutes: Int
)

/**
 * A nudge when today's distracting time runs notably (>25%) above the window's usual level,
 * averaged over the *other* tracked days. Null when today isn't present or there's no baseline yet.
 */
fun distractionNudge(days: List<AppUsageDay>, today: LocalDate): DistractionNudge? {
    val todayRow = days.firstOrNull { it.date == today } ?: return null
    val others = days.filter { it.date != today && it.totalMinutes > 0 }
    if (others.isEmpty()) return null
    val average = others.sumOf { it.distractingMinutes } / others.size
    if (average <= 0 || todayRow.distractingMinutes <= average * 5 / 4) return null
    return DistractionNudge(todayRow.distractingMinutes, average)
}

/** The day with the most focused (productive) time in the window, or null if none had any. */
fun bestFocusDay(days: List<AppUsageDay>): AppUsageDay? =
    days.filter { it.productiveMinutes > 0 }.maxByOrNull { it.productiveMinutes }
