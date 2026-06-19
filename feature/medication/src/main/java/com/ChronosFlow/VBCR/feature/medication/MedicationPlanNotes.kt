package com.ChronosFlow.VBCR.feature.medication

/**
 * Stores optional second daily reminder in notes without a DB migration.
 * Format: [[reminder2:540]] user-visible notes...
 */
internal data class MedicationPlanNotes(
    val displayNotes: String?,
    val secondaryReminderMinute: Int?,
    val mealTiming: String?
)

private val reminder2Regex = Regex("""\[\[reminder2:(\d{1,4})]]\s*""")
private val timingRegex = Regex("""\[\[timing:([a-z_]+)]]\s*""")

internal fun parseMedicationPlanNotes(raw: String?): MedicationPlanNotes {
    if (raw.isNullOrBlank()) {
        return MedicationPlanNotes(displayNotes = null, secondaryReminderMinute = null, mealTiming = null)
    }
    val match = reminder2Regex.find(raw)
    val secondary = match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 1439)
    val timing = timingRegex.find(raw)?.groupValues?.getOrNull(1)?.let(::mealTimingLabel)
    val display = raw
        .replace(reminder2Regex, "")
        .replace(timingRegex, "")
        .trim()
        .takeIf { it.isNotBlank() }
    return MedicationPlanNotes(displayNotes = display, secondaryReminderMinute = secondary, mealTiming = timing)
}

internal fun encodeMedicationPlanNotes(
    displayNotes: String?,
    secondaryReminderMinute: Int?,
    mealTiming: String? = null
): String? {
    val marker = secondaryReminderMinute?.let { "[[reminder2:$it]]" }.orEmpty()
    val timingMarker = mealTimingMetadata(mealTiming)?.let { "[[timing:$it]]" }.orEmpty()
    val body = displayNotes?.trim().orEmpty()
    val combined = listOf(marker, timingMarker, body)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
    return combined.takeIf { it.isNotBlank() }
}

private fun mealTimingLabel(value: String): String? = when (value) {
    "with_food" -> "With food"
    "before_bed" -> "Before bed"
    "anytime" -> "Anytime"
    else -> null
}

private fun mealTimingMetadata(value: String?): String? = when (value) {
    "With food" -> "with_food"
    "Before bed" -> "before_bed"
    "Anytime" -> "anytime"
    else -> null
}
