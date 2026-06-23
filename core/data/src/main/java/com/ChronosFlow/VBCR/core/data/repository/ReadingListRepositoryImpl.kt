package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.ReadingItemDao
import com.ChronosFlow.VBCR.core.data.model.ReadingItemEntity
import com.ChronosFlow.VBCR.core.domain.model.ReadingItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingMetadataState
import com.ChronosFlow.VBCR.core.domain.model.ReadingStatus
import com.ChronosFlow.VBCR.core.domain.repository.ReadingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class ReadingListRepositoryImpl @Inject constructor(
    private val readingItemDao: ReadingItemDao
) : ReadingListRepository {
    override fun observeActive(): Flow<List<ReadingItem>> =
        readingItemDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeByStatus(status: ReadingStatus): Flow<List<ReadingItem>> =
        readingItemDao.observeByStatus(status.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): ReadingItem? = readingItemDao.getById(id)?.toDomain()

    override suspend fun findByUrl(url: String): ReadingItem? = readingItemDao.findByUrl(url)?.toDomain()

    override suspend fun save(item: ReadingItem) = readingItemDao.upsert(item.toEntity())

    override suspend fun updateStatus(id: String, status: ReadingStatus) =
        readingItemDao.updateStatus(id, status.name, Instant.now())

    override suspend fun setReminder(id: String, reminderAt: Instant?) =
        readingItemDao.updateReminder(id, reminderAt, Instant.now())

    override suspend fun delete(id: String) = readingItemDao.deleteById(id)
}

private fun ReadingItemEntity.toDomain(): ReadingItem = ReadingItem(
    id = id,
    url = url,
    title = title,
    domain = domain,
    faviconPath = faviconPath,
    estimatedReadMinutes = estimatedReadMinutes,
    wordCount = wordCount,
    status = runCatching { ReadingStatus.valueOf(status) }.getOrDefault(ReadingStatus.UNREAD),
    metadataState = runCatching { ReadingMetadataState.valueOf(metadataState) }
        .getOrDefault(ReadingMetadataState.PENDING),
    notes = notes,
    reminderAt = reminderAt,
    addedAt = addedAt,
    updatedAt = updatedAt,
    lastOpenedAt = lastOpenedAt,
    sortOrder = sortOrder
)

private fun ReadingItem.toEntity(): ReadingItemEntity = ReadingItemEntity(
    id = id,
    url = url,
    title = title,
    domain = domain,
    faviconPath = faviconPath,
    estimatedReadMinutes = estimatedReadMinutes,
    wordCount = wordCount,
    status = status.name,
    metadataState = metadataState.name,
    notes = notes,
    reminderAt = reminderAt,
    addedAt = addedAt,
    updatedAt = updatedAt,
    lastOpenedAt = lastOpenedAt,
    sortOrder = sortOrder
)
