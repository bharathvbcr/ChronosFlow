package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.JournalAttachment
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface JournalRepository {
    fun observeForDate(date: LocalDate): Flow<List<JournalEntry>>
    fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<JournalEntry>>
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<JournalEntry>
    suspend fun getById(id: String): JournalEntry?

    /** Inserts or updates an entry. If [JournalEntry.isPrimary] is true, demotes other entries for the day. */
    suspend fun save(entry: JournalEntry)
    suspend fun delete(id: String)

    fun observeAllAttachments(): Flow<List<JournalAttachment>>
    fun observeAttachments(entryId: String): Flow<List<JournalAttachment>>
    suspend fun addAttachment(entryId: String, uri: String, mimeType: String?)
    suspend fun removeAttachment(id: String)
}
