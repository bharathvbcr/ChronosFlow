package com.ChronosFlow.VBCR.feature.daydial.model

import java.time.LocalDate

/**
 * Rollup window for the Insights tab. Each non-day period is a trailing window of
 * [days] dates ending at the anchored (selected) date, so future days that have not
 * been executed yet never dilute completion.
 */
enum class InsightsPeriod(val label: String, val days: Int) {
    DAY("Day", 1),
    WEEK("Week", 7),
    MONTH("Month", 30);

    /** Dates covered by this period ending at [anchor], oldest first. */
    fun dateRange(anchor: LocalDate): List<LocalDate> =
        (days - 1 downTo 0).map { anchor.minusDays(it.toLong()) }
}
