package com.ChronosFlow.VBCR.core.domain.wear

/**
 * Shared Wearable Data Layer contract for mirroring the phone's Material theme palette to the
 * watch. Companion to [WearDaySummaryContract] and [WearFocusContract]: the phone publisher and
 * the watch listener both reference these constants so the two stay in lock-step.
 *
 * The phone publishes its *dynamic dark* accent palette (the watch renders on AMOLED black, so
 * the dark variant is always the right one) as a single Long array of ARGB color values in the
 * `IDX_*` order below. When the phone cannot provide a dynamic palette (pre-Android 12) it
 * deletes the item instead, and the watch falls back to its own theming.
 */
object WearThemeContract {
    /** Data Layer item path for the mirrored theme palette. */
    const val THEME_PATH = "/chronos/theme"

    /** Long array: ARGB colors in `IDX_*` order; exactly [PALETTE_SIZE] entries. */
    const val KEY_PALETTE = "palette"

    const val IDX_PRIMARY = 0
    const val IDX_ON_PRIMARY = 1
    const val IDX_PRIMARY_CONTAINER = 2
    const val IDX_ON_PRIMARY_CONTAINER = 3
    const val IDX_SECONDARY = 4
    const val IDX_ON_SECONDARY = 5
    const val IDX_SECONDARY_CONTAINER = 6
    const val IDX_ON_SECONDARY_CONTAINER = 7
    const val IDX_TERTIARY = 8
    const val IDX_ON_TERTIARY = 9
    const val IDX_TERTIARY_CONTAINER = 10
    const val IDX_ON_TERTIARY_CONTAINER = 11

    /** Total entries in a valid palette; receivers must reject other sizes. */
    const val PALETTE_SIZE = 12
}
