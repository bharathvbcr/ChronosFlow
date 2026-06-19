package com.ChronosFlow.VBCR.feature.daydial

import java.util.Locale

/**
 * Single source of truth for the day-dial's minute-of-day parsing and formatting.
 *
 * These were previously copy-pasted across [DayDialBlockEditorFields], [SheetContent],
 * [DayDialTemplateState], TodayTab, PlanTab, and DayDialTemplateUtils — with two subtly
 * different format styles that could drift apart. Consolidated here so every surface shares
 * one parser and one of each formatter.
 */

private const val MINUTES_IN_DAY = 1440

/**
 * Parse a 24-hour `"HH:MM"` string into a minute-of-day in `0..1439`, or `null` when the
 * text is malformed or out of range. Used for time-entry fields and template start times.
 */
internal fun parseMinuteOfDay(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

/**
 * Format a minute-of-day as a zero-padded 24-hour `"HH:MM"` label (e.g. `"09:05"`, `"14:30"`).
 * This is the editor/input style; for human-facing schedule rows use [formatClockLabel].
 */
internal fun formatMinuteOfDay(minute: Int): String {
    val normalized = ((minute % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY
    val h = (normalized / 60) % 24
    val m = normalized % 60
    return String.format(Locale.getDefault(), "%02d:%02d", h, m)
}

/**
 * Format a minute-of-day as a 12-hour clock label with an AM/PM suffix
 * (e.g. `"9:05 AM"`, `"2:30 PM"`). This is the human-facing schedule style used on the
 * Today and Plan tabs; for editor fields use [formatMinuteOfDay].
 */
internal fun formatClockLabel(minute: Int): String {
    val normalized = ((minute % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY
    val hour = normalized / 60
    val m = normalized % 60
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return "%d:%02d %s".format(displayHour, m, suffix)
}
