package com.chronosflow.wear.presentation

/** Formatting helpers shared across the watch app screens. */
internal object WearFormat {

    /** Minute-of-day (may exceed 1439 across midnight) → "HH:mm". */
    fun minuteOfDay(minuteOfDay: Int): String {
        val safe = ((minuteOfDay % 1440) + 1440) % 1440
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    /** Seconds → "m:ss" (or "h:mm:ss" past an hour). */
    fun mmss(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
