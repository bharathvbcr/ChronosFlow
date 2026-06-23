package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.InboxItem
import com.ChronosFlow.VBCR.core.domain.model.TriageOutcome
import kotlinx.coroutines.flow.Flow

interface InboxRepository {
    fun observeUntriaged(): Flow<List<InboxItem>>
    fun observeAll(): Flow<List<InboxItem>>
    suspend fun getById(id: String): InboxItem?

    /** Inserts or updates an item. */
    suspend fun save(item: InboxItem)
    suspend fun markTriaged(id: String, outcome: TriageOutcome, refId: String?)
    suspend fun delete(id: String)
}
