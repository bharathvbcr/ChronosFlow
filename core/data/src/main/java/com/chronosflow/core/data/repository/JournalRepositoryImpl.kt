package com.chronosflow.core.data.repository

import com.chronosflow.core.data.dao.JournalEntryDao
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.domain.model.JournalEntry
import com.chronosflow.core.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class JournalRepositoryImpl @Inject constructor(
    private val journalEntryDao: JournalEntryDao
) : JournalRepository {
    override fun observeForDate(date: LocalDate): Flow<List<JournalEntry>> =
        journalEntryDao.observeForDate(date).map { list -> list.map { it.toDomain() } }

    override fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<JournalEntry>> =
        journalEntryDao.observeForDateRange(start, end).map { list -> list.map { it.toDomain() } }

    override suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<JournalEntry> =
        journalEntryDao.getForDateRange(start, end).map { it.toDomain() }

    override suspend fun getById(id: String): JournalEntry? = journalEntryDao.getById(id)?.toDomain()

    override suspend fun save(entry: JournalEntry) {
        journalEntryDao.insert(entry.toEntity())
        if (entry.isPrimary) {
            journalEntryDao.clearPrimaryForDate(entry.entryDate, entry.id)
        }
    }

    override suspend fun delete(id: String) = journalEntryDao.deleteById(id)
}
