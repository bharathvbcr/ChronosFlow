package com.chronosflow.core.domain.wear

/**
 * Shared Wearable Data Layer contract for the watch acting back on the phone. The day-summary
 * and focus mirrors flow phone → watch ([WearDaySummaryContract], [WearFocusContract]); this is
 * the return channel watch → phone, carried over the [com.google.android.gms.wearable.MessageClient].
 *
 * The watch sends one message at [ACTION_PATH] whose payload is `type FIELD_SEP arg` (UTF-8).
 * The phone's listener decodes it and routes to the same use cases the home-screen widgets use,
 * so a tap on the wrist and a tap on a widget take the exact same path through the app.
 */
object WearActionContract {
    /** MessageClient path for every watch-originated action. */
    const val ACTION_PATH = "/chronos/action"

    private const val FIELD_SEP = "\u001F"

    /** Focus transport control; [arg] is one of the FOCUS_* constants. */
    const val TYPE_FOCUS = "focus"

    /** Mark a habit done for today; [arg] is the habit id. */
    const val TYPE_HABIT = "habit"

    /** Toggle a task's completion; [arg] is the task id. */
    const val TYPE_TASK = "task"

    /** Acknowledge a medication dose as taken; [arg] is the medication plan id. */
    const val TYPE_DOSE = "dose"

    /** Mark a planned time block complete; [arg] is the block id. */
    const val TYPE_BLOCK = "block"

    /**
     * Ask the phone to re-publish the day-summary mirror now; [arg] is unused (empty). Sent by the
     * watch when it opens with a missing or stale mirror, so the phone pushes a fresh summary even
     * when the phone app is foregrounded (its other publish triggers only fire on background / the
     * widget-refresh worker). The listener wakes the phone process and re-runs the same publish.
     */
    const val TYPE_SYNC = "sync"

    const val FOCUS_START = "start"
    const val FOCUS_PAUSE = "pause"
    const val FOCUS_RESUME = "resume"
    const val FOCUS_STOP = "stop"

    private const val FOCUS_START_SECONDS_SEP = ":"

    /**
     * Builds the focus-start [arg]. With a positive [seconds] the watch is asking for a session
     * of that exact length (e.g. "start:1500"); otherwise it is a plain "start" that defers to
     * the phone's default duration.
     */
    fun focusStart(seconds: Int?): String =
        if (seconds != null && seconds > 0) "$FOCUS_START$FOCUS_START_SECONDS_SEP$seconds" else FOCUS_START

    /** True when [arg] is any focus-start (with or without an explicit duration). */
    fun isFocusStart(arg: String): Boolean =
        arg == FOCUS_START || arg.startsWith("$FOCUS_START$FOCUS_START_SECONDS_SEP")

    /** The explicit duration in a focus-start [arg], or null when none was requested. */
    fun focusStartSeconds(arg: String): Int? =
        arg.substringAfter("$FOCUS_START$FOCUS_START_SECONDS_SEP", "").toIntOrNull()?.takeIf { it > 0 }

    /** Packs an action into the on-wire payload. */
    fun encode(type: String, arg: String): ByteArray =
        "$type$FIELD_SEP$arg".toByteArray(Charsets.UTF_8)

    /** Unpacks a payload into (type, arg), or null when it is malformed. */
    fun decode(payload: ByteArray): Pair<String, String>? {
        val parts = String(payload, Charsets.UTF_8).split(FIELD_SEP, limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) return null
        return parts[0] to parts[1]
    }
}
