package com.chronosflow.core.domain.model

/**
 * Heuristic energy classification for imported calendar events so the AI
 * planner and energy-correlation engine can reason about them instead of
 * treating every import as MODERATE. Lives in the domain layer because the
 * data layer cannot depend on core:ai (it would be circular), and import runs
 * in background sync where GenAI is unavailable anyway.
 */
private val HIGH_ENERGY_KEYWORDS = listOf(
    "meeting", "interview", "presentation", "review", "1:1", "one-on-one",
    "standup", "stand-up", "sync", "demo", "planning", "workshop", "exam",
    "deadline", "negotiation", "pitch", "onsite", "on-site"
)

private val LOW_ENERGY_KEYWORDS = listOf(
    "lunch", "dinner", "breakfast", "brunch", "coffee", "break", "social",
    "party", "birthday", "holiday", "vacation", "travel", "flight", "commute",
    "walk", "errand", "appointment reminder", "out of office", "ooo"
)

fun classifyImportedEventEnergy(title: String, description: String? = null): EnergyIntensity {
    val haystack = buildString {
        append(title.lowercase())
        description?.let {
            append(' ')
            append(it.lowercase())
        }
    }
    return when {
        LOW_ENERGY_KEYWORDS.any { haystack.contains(it) } -> EnergyIntensity.LOW
        HIGH_ENERGY_KEYWORDS.any { haystack.contains(it) } -> EnergyIntensity.HIGH
        else -> EnergyIntensity.MODERATE
    }
}
