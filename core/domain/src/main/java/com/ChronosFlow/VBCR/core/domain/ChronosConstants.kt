package com.ChronosFlow.VBCR.core.domain

/**
 * Application-wide numeric constants extracted from business logic.
 * Centralising them here prevents the same threshold from drifting across modules.
 */
object ChronosConstants {
    // ── Habit streak ──────────────────────────────────────────────────────────
    /** Completing a habit within this many days of the last completion continues the streak. */
    const val HABIT_STREAK_WINDOW_DAYS = 2

    // ── Health Connect sync ───────────────────────────────────────────────────
    /**
     * Number of days covered by a full Health Connect reconcile window.
     * Wider than the old 3-day window; reconciles are rare (incremental pulls carry steady state)
     * so the cost buys resilience to multi-day gaps.
     */
    const val HC_SYNC_WINDOW_DAYS = 30

    // ── Worker retry ──────────────────────────────────────────────────────────
    /** Maximum number of times a background sync worker should retry on transient failure. */
    const val MAX_WORKER_RETRY_COUNT = 5

    // ── Shared-text import ────────────────────────────────────────────────────
    /**
     * Maximum number of UTF-8 characters read from a shared text file before truncating.
     * Guards against OOM when a huge or binary file is shared into the task importer.
     */
    const val MAX_SHARED_TEXT_LENGTH = 10_000

    // ── Thumbnail decoding ────────────────────────────────────────────────────
    /** Target dimension (px) used when downsampling journal attachment thumbnails. */
    const val THUMBNAIL_TARGET_PX = 320
}
