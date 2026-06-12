package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.JournalEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface JournalRepository {
    fun observeForDate(date: LocalDate): Flow<List<JournalEntry>>
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<JournalEntry>
    suspend fun getById(id: String): JournalEntry?

    /** Inserts or updates an entry. If [JournalEntry.isPrimary] is true, demotes other entries for the day. */
    suspend fun save(entry: JournalEntry)
    suspend fun delete(id: String)
}
