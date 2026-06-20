package com.ChronosFlow.VBCR.core.ui

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared [DateTimeFormatter] instances for display throughout the app.
 * Using a single source of truth avoids pattern drift across composables.
 *
 * All formatters use [Locale.getDefault] so dates respect the user's locale on creation.
 * Do not cache these across locale changes; re-obtain from this object instead.
 */
object ChronosDateFormatters {
    /** "Mon, Jun 3" — short weekday + month + day with no year. */
    val shortWeekdayDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

    /** "Monday, Jun 3" — full weekday + month + day, e.g. for AI plan reason strings. */
    val fullWeekdayDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

    /** "Jun 3" — short month + day only. */
    val shortDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    /** "June 3, 2026" — full month + day + year. */
    val fullDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

    /** "Jun 3, 2026" — abbreviated month + day + year. */
    val mediumDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    /** "Mon, Jun 3, 2026" — weekday + abbreviated month + day + year (used by date pickers). */
    val fullWeekdayMediumDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

    /** "June 2026" — month + year for calendar headers. */
    val monthYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    /** "3:45 PM" — 12-hour clock with AM/PM indicator. */
    val time: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
}
