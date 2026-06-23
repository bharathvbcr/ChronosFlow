package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.InboxItemDao
import com.ChronosFlow.VBCR.core.data.model.InboxItemEntity
import com.ChronosFlow.VBCR.core.domain.model.CaptureSource
import com.ChronosFlow.VBCR.core.domain.model.InboxItem
import com.ChronosFlow.VBCR.core.domain.model.TriageOutcome
import com.ChronosFlow.VBCR.core.domain.repository.InboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class InboxRepositoryImpl @Inject constructor(
    private val inboxItemDao: InboxItemDao
) : InboxRepository {
    override fun observeUntriaged(): Flow<List<InboxItem>> =
        inboxItemDao.observeUntriaged().map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<InboxItem>> =
        inboxItemDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): InboxItem? = inboxItemDao.getById(id)?.toDomain()

    override suspend fun save(item: InboxItem) = inboxItemDao.upsert(item.toEntity())

    override suspend fun markTriaged(id: String, outcome: TriageOutcome, refId: String?) =
        inboxItemDao.markTriaged(id, outcome.name, refId)

    override suspend fun delete(id: String) = inboxItemDao.deleteById(id)
}

private fun InboxItemEntity.toDomain(): InboxItem = InboxItem(
    id = id,
    text = text,
    url = url,
    source = runCatching { CaptureSource.valueOf(source) }.getOrDefault(CaptureSource.MANUAL),
    createdAt = createdAt,
    triaged = triaged,
    triagedTo = triagedTo?.let { value -> runCatching { TriageOutcome.valueOf(value) }.getOrNull() },
    triagedRefId = triagedRefId,
    sortOrder = sortOrder
)

private fun InboxItem.toEntity(): InboxItemEntity = InboxItemEntity(
    id = id,
    text = text,
    url = url,
    source = source.name,
    createdAt = createdAt,
    triaged = triaged,
    triagedTo = triagedTo?.name,
    triagedRefId = triagedRefId,
    sortOrder = sortOrder
)
