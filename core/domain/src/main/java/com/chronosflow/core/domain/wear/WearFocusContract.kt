package com.chronosflow.core.domain.wear

/**
 * Shared Wearable Data Layer contract for mirroring the active focus session from the phone
 * to the watch. Both the phone publisher and the watch listener reference these constants so
 * the two stay in lock-step.
 *
 * Timing is carried as a wall-clock epoch (System.currentTimeMillis based) rather than
 * SystemClock.elapsedRealtime, because the elapsed-realtime base is not comparable across two
 * physical devices. The watch converts [KEY_PLANNED_END_AT_MILLIS] into its own local timer.
 */
object WearFocusContract {
    /** Data Layer item path for the current focus session. */
    const val FOCUS_PATH = "/chronos/focus_session"

    /** Boolean: a focus session is currently active (running or paused). */
    const val KEY_ACTIVE = "active"

    /** Boolean: the active session is paused. */
    const val KEY_PAUSED = "paused"

    /** String: the focus block title (already privacy-redacted by the phone when required). */
    const val KEY_TITLE = "title"

    /** Long: wall-clock epoch millis at which the running timer reaches zero. */
    const val KEY_PLANNED_END_AT_MILLIS = "planned_end_at_millis"

    /** Int: frozen seconds remaining; only meaningful while [KEY_PAUSED] is true. */
    const val KEY_PAUSED_TIME_LEFT_SECONDS = "paused_time_left_seconds"

    /** Int: total session length in seconds. */
    const val KEY_TOTAL_SECONDS = "total_seconds"
}
