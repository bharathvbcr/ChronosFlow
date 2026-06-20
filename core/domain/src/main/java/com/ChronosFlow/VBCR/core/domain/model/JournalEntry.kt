package com.ChronosFlow.VBCR.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate

/** A daily reflection entry. One [isPrimary] entry per day acts as the day's main reflection. */
@Immutable
data class JournalEntry(
    val id: String,
    val entryDate: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val body: String,
    val promptType: String? = null,
    val moodCheckInId: String? = null,
    val isPrimary: Boolean = true,
    val dayRating: Int? = null,
    val entryMinuteOfDay: Int? = null
)
