package com.ChronosFlow.VBCR.wear.presentation

/** Formatting helpers shared across the watch app screens. */
internal object WearFormat {

    /** Minute-of-day (may exceed 1439 across midnight) → "HH:mm". */
    fun minuteOfDay(minuteOfDay: Int): String {
        val safe = ((minuteOfDay % 1440) + 1440) % 1440
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    /**
     * A block's full time window, e.g. "09:00–09:30" (24h, watch-local) — shows when the block runs
     * at a glance, more than the bare end time. Mirrors the phone notification's time-window line.
     */
    fun windowLabel(startMinute: Int, endMinute: Int): String =
        "${minuteOfDay(startMinute)}–${minuteOfDay(endMinute)}"

    /** Seconds → "m:ss" (or "h:mm:ss" past an hour). */
    fun mmss(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** A whole-minute span as compact words: 45 → "45m", 90 → "1h 30m", 120 → "2h". */
    fun minutesWords(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        val h = safe / 60
        val m = safe % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    /**
     * How much longer the block ending at [endMinute] runs, relative to [nowMinute] — e.g.
     * "23m left" / "1h 5m left". Reads "ending" once the end is reached so a glance never shows a
     * negative or zero countdown.
     */
    fun remainingLabel(endMinute: Int, nowMinute: Int): String {
        val remaining = endMinute - nowMinute
        return if (remaining <= 0) "ending" else "${minutesWords(remaining)} left"
    }

    /**
     * How soon the block starting at [startMinute] begins, relative to [nowMinute] — e.g.
     * "in 15m" / "in 1h 5m", or "now" once it is due.
     */
    fun startsInLabel(startMinute: Int, nowMinute: Int): String {
        val delta = startMinute - nowMinute
        return if (delta <= 0) "now" else "in ${minutesWords(delta)}"
    }

    /** A schedule synced more than this long ago is treated as possibly out of date. */
    private const val STALE_THRESHOLD_MILLIS = 60L * 60_000L

    /**
     * A "Synced Nm/Nh ago" hint to show when the mirrored day is stale enough that its
     * time-relative claims may no longer hold, or null when the data is fresh ([receivedAtMillis]
     * within [STALE_THRESHOLD_MILLIS]) or was never synced on this device ([receivedAtMillis] <= 0,
     * where the empty state already speaks for itself). [nowMillis] is the current wall clock.
     */
    fun syncAgeLabel(receivedAtMillis: Long, nowMillis: Long): String? {
        if (receivedAtMillis <= 0L) return null
        val ageMillis = nowMillis - receivedAtMillis
        if (ageMillis < STALE_THRESHOLD_MILLIS) return null
        val ageMinutes = ageMillis / 60_000L
        val span = if (ageMinutes >= 120L) "${ageMinutes / 60L}h" else "${ageMinutes}m"
        return "Synced $span ago"
    }
}
