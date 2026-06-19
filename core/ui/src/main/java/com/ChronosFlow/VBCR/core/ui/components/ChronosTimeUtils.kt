package com.ChronosFlow.VBCR.core.ui.components

import java.util.Locale

/** Parses HH:mm, H:mm, 9:00 AM, or 21:00 into minutes from midnight. */
fun parseFlexibleMinute(value: String): Int? {
    val normalized = value.trim().uppercase(Locale.getDefault())
    val amPm = when {
        normalized.endsWith("AM") -> "AM"
        normalized.endsWith("PM") -> "PM"
        else -> null
    }
    val time = normalized.removeSuffix("AM").removeSuffix("PM").trim()
    val parts = time.split(":")
    if (parts.isEmpty() || parts.size > 2) return null
    val rawHour = parts[0].toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    if (minute !in 0..59) return null
    val hour = when (amPm) {
        "AM" -> if (rawHour == 12) 0 else rawHour
        "PM" -> if (rawHour == 12) 12 else rawHour + 12
        else -> rawHour
    }
    return (hour * 60 + minute).takeIf { hour in 0..23 }
}

/** 12-hour clock label, e.g. 8:00 AM. */
fun formatDisplayMinute(minute: Int): String {
    val normalized = minute.floorMod(24 * 60)
    val hour = normalized / 60
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return "$displayHour:${(normalized % 60).toString().padStart(2, '0')} $suffix"
}

/** 24-hour clock for compact inputs, e.g. 08:00. */
fun formatClockMinute(minute: Int): String {
    val normalized = minute.floorMod(24 * 60)
    return String.format(Locale.getDefault(), "%02d:%02d", normalized / 60, normalized % 60)
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
