package com.ChronosFlow.VBCR.core.data.model

import java.time.Instant
import java.time.LocalDate

/**
 * Lightweight projection of [JournalEntryEntity] for list/summary views.
 *
 * Omits the potentially large [JournalEntryEntity.body] column so that
 * history queries do not load full entry text into memory when only
 * metadata (date, mood, prompt type) is needed.
 */
data class JournalEntrySummary(
    val id: String,
    val entryDate: LocalDate,
    val moodCheckInId: String?,
    val promptType: String?,
    val isPrimary: Boolean,
    val dayRating: Int?,
    val entryMinuteOfDay: Int?,
    val createdAt: Instant,
    val updatedAt: Instant
)
