package com.ChronosFlow.VBCR.feature.daydial.ui

internal data class JournalPrompt(val key: String, val label: String, val question: String)

/**
 * Legacy `promptType` sentinel for an entry composed from the full guided scaffold (all prompts),
 * as opposed to a single-prompt key. Kept distinct from the [JournalPrompts] keys; the timeline maps
 * it to its own "Guided" label. Retained so older guided entries still render a label.
 */
internal const val GuidedPromptType = "guided"

/**
 * Legacy guided-reflection prompts. The composer no longer offers these chips, but the keys persist
 * on older entries' `promptType`, so the timeline keeps mapping them to display labels.
 */
internal val JournalPrompts = listOf(
    JournalPrompt("went_well", "Went well", "What went well today?"),
    JournalPrompt("drained", "Drained me", "What drained you today?"),
    JournalPrompt("tomorrow", "Tomorrow", "One thing to make tomorrow better?")
)

/** Whitespace-delimited word count for the journal body, used for the live writing meter. */
internal fun journalWordCount(body: String): Int =
    body.trim().split(Regex("\\s+")).count { it.isNotBlank() }
