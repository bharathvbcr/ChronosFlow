package com.chronosflow.core.domain.model

/**
 * Shared classification of a block's [TimeBlock.category] string for glanceable surfaces (phone
 * widgets, the Wear day summary). Centralised so the phone bridge and the watch agree on what a
 * "break" is and where offering a focus session makes sense — mirroring the phone live
 * notification's break/event split and "Start focus" gating.
 */
object BlockCategories {
    /** Canonical break category (case-insensitive); breaks are surfaced apart from real events. */
    const val BREAK = "break"

    /** Whether [category] marks a break rather than a substantive event. */
    fun isBreak(category: String): Boolean = category.trim().equals(BREAK, ignoreCase = true)

    /**
     * Whether a block warrants a "Start focus" affordance. Rest-style categories (break / sleep /
     * meal) are excluded — kicking off a focus timer there is contradictory; everything else
     * (work, study, exercise, routine, calendar, custom/blank) keeps it.
     */
    fun supportsFocus(category: String): Boolean = when (category.trim().lowercase()) {
        BREAK, "sleep", "meal", "food" -> false
        else -> true
    }
}
