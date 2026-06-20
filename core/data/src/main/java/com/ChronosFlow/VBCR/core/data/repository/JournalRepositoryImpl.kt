package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.JournalAttachmentDao
import com.ChronosFlow.VBCR.core.data.dao.JournalEntryDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.data.model.JournalAttachmentEntity
import com.ChronosFlow.VBCR.core.domain.model.JournalAttachment
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import com.ChronosFlow.VBCR.core.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class JournalRepositoryImpl @Inject constructor(
    private val journalEntryDao: JournalEntryDao,
    private val journalAttachmentDao: JournalAttachmentDao
) : JournalRepository {
    override fun observeForDate(date: LocalDate): Flow<List<JournalEntry>> =
        journalEntryDao.observeForDate(date).map { list -> list.map { it.toDomain() } }

    override fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<JournalEntry>> =
        journalEntryDao.observeForDateRange(start, end).map { list -> list.map { it.toDomain() } }

    override suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<JournalEntry> =
        journalEntryDao.getForDateRange(start, end).map { it.toDomain() }

    override suspend fun getById(id: String): JournalEntry? = journalEntryDao.getById(id)?.toDomain()

    override suspend fun save(entry: JournalEntry) {
        journalEntryDao.insertAsPrimary(entry.toEntity())
    }

    override suspend fun delete(id: String) = journalEntryDao.deleteById(id)

    override fun observeAllAttachments(): Flow<List<JournalAttachment>> =
        journalAttachmentDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeAttachments(entryId: String): Flow<List<JournalAttachment>> =
        journalAttachmentDao.observeForEntry(entryId).map { list -> list.map { it.toDomain() } }

    override suspend fun addAttachment(entryId: String, uri: String, mimeType: String?) {
        // sortOrder is set atomically inside insertAttachmentWithOrder to avoid a race where two
        // concurrent adds both read the same count and write duplicate sort-order values.
        journalAttachmentDao.insertAttachmentWithOrder(
            attachment = JournalAttachmentEntity(
                id = UUID.randomUUID().toString(),
                journalEntryId = entryId,
                uri = uri,
                mimeType = mimeType,
                createdAt = Instant.now(),
                sortOrder = 0 // overwritten atomically by insertAttachmentWithOrder
            ),
            entryId = entryId
        )
    }

    override suspend fun removeAttachment(id: String) = journalAttachmentDao.deleteById(id)
}

private fun JournalAttachmentEntity.toDomain(): JournalAttachment = JournalAttachment(
    id = id,
    journalEntryId = journalEntryId,
    uri = uri,
    mimeType = mimeType
)
