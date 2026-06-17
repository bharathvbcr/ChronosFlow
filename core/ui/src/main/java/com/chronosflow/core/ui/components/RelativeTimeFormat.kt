package com.chronosflow.core.ui.components

import java.util.concurrent.TimeUnit

/**
 * Compact "Synced X ago" hint for the last successful calendar sync. Pure and clock-injected
 * ([nowMillis] is passed in) so it can be unit-tested and recomputed on recomposition.
 *
 * @param lastSyncAtMillis epoch millis of the last sync, or null if never synced.
 * @param nowMillis the current wall-clock time in epoch millis.
 */
fun formatLastSyncedLabel(lastSyncAtMillis: Long?, nowMillis: Long): String {
    if (lastSyncAtMillis == null) return "Not synced yet"
    val elapsedMillis = nowMillis - lastSyncAtMillis
    if (elapsedMillis < 0L) return "Synced just now" // guard against clock skew

    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
    if (minutes < 1L) return "Synced just now"
    if (minutes < 60L) return "Synced $minutes min ago"

    val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
    if (hours < 24L) return "Synced $hours hr ago"

    val days = TimeUnit.MILLISECONDS.toDays(elapsedMillis)
    return if (days == 1L) "Synced yesterday" else "Synced $days days ago"
}
