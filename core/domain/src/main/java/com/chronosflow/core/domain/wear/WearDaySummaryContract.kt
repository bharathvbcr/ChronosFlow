package com.chronosflow.core.domain.wear

/**
 * Shared Wearable Data Layer contract for mirroring a daily summary (schedule, tasks, habits,
 * medications) from the phone to the watch. Companion to [WearFocusContract]: the phone
 * publisher and the watch listener both reference these constants so the two stay in lock-step.
 *
 * Times are carried as minute-of-day values; the watch formats them locally. List payloads are
 * carried as String arrays where each entry packs the fields the watch needs — including the
 * entity id — joined by [FIELD_SEP]. The id lets the watch act on a specific task/habit/dose
 * via [WearActionContract] without holding any domain logic of its own.
 *
 * When the user opts into redacting sensitive titles, the phone drops the list payloads
 * entirely (times and counts still flow); the watch then shows counts only and cannot offer
 * per-item actions for that surface.
 */
object WearDaySummaryContract {
    /** Data Layer item path for the day summary. */
    const val DAY_SUMMARY_PATH = "/chronos/day_summary"

    /** Field separator inside a packed list entry (ASCII unit separator; never in titles). */
    const val FIELD_SEP = "\u001F"

    /** String: title of the block happening right now; absent when nothing is scheduled. */
    const val KEY_NOW_TITLE = "now_title"

    /** Int: minute-of-day when the current block ends; may exceed 1439 across midnight. */
    const val KEY_NOW_END_MINUTE = "now_end_minute"

    /** String: title of the next upcoming block; absent when the day is done. */
    const val KEY_NEXT_TITLE = "next_title"

    /** Int: minute-of-day when the next block starts. */
    const val KEY_NEXT_START_MINUTE = "next_start_minute"

    /** Int: total number of open tasks. */
    const val KEY_OPEN_TASK_COUNT = "open_task_count"

    /** String array: open tasks packed as `id FIELD_SEP title`. Empty when redacted. */
    const val KEY_TASK_ENTRIES = "task_entries"

    /** Int: active habits completed today. */
    const val KEY_HABITS_DONE = "habits_done"

    /** Int: total active habits. */
    const val KEY_HABITS_TOTAL = "habits_total"

    /** String array: habits packed as `id FIELD_SEP done(0/1) FIELD_SEP streak FIELD_SEP title`. */
    const val KEY_HABIT_ENTRIES = "habit_entries"

    /** Int: active medication doses still pending (not taken) today. */
    const val KEY_MEDS_DUE_COUNT = "meds_due_count"

    /**
     * String array: medications packed as
     * `id FIELD_SEP taken(0/1) FIELD_SEP reminderMinute FIELD_SEP doseLabel FIELD_SEP name`.
     * Empty when redacted.
     */
    const val KEY_MED_ENTRIES = "med_entries"

    /** Maximum entries the phone publishes per list so the payload stays small. */
    const val MAX_ENTRIES = 6

    /**
     * String array: today's current + upcoming blocks packed as
     * `startMinute FIELD_SEP endMinute` — times only, no titles, so it survives redaction.
     * Drawn as the watch's day-dial ring.
     */
    const val KEY_BLOCK_ENTRIES = "block_entries"

    /** Maximum block entries published for the day dial. */
    const val MAX_BLOCK_ENTRIES = 16

    /**
     * String: one-line AI day digest pre-generated on the phone (cache-only — the watch never
     * runs inference). Dropped entirely when titles are redacted, since the digest text can
     * mention the next block's title.
     */
    const val KEY_DIGEST = "digest"
}
