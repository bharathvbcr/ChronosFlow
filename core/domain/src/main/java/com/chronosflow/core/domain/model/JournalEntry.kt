package com.chronosflow.core.domain.model

import java.time.Instant
import java.time.LocalDate

/** A daily reflection entry. One [isPrimary] entry per day acts as the day's main reflection. */
data class JournalEntry(
    val id: String,
    val entryDate: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val body: String,
    val promptType: String? = null,
    val moodCheckInId: String? = null,
    val isPrimary: Boolean = true
)
