package com.ChronosFlow.VBCR.feature.daydial

import java.time.LocalDate

/**
 * Once-per-date session gate for quiet device-calendar refreshes: the first
 * visit to a date syncs it, revisits within the same session don't churn the
 * imported blocks. The manual sync action stays available for forced refreshes.
 */
internal class CalendarAutoSyncGate {
    private val syncedDates = mutableSetOf<LocalDate>()

    /** Returns true exactly once per date for the lifetime of this gate. */
    fun shouldSync(date: LocalDate): Boolean = syncedDates.add(date)
}
