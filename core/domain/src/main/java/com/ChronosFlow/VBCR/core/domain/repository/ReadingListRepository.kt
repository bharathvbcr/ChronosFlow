package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.ReadingItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ReadingListRepository {
    /** All non-archived items, ordered for the reading queue. */
    fun observeActive(): Flow<List<ReadingItem>>
    fun observeByStatus(status: ReadingStatus): Flow<List<ReadingItem>>
    suspend fun getById(id: String): ReadingItem?

    /** Existing item with the same [url], if any — used to avoid duplicate saves. */
    suspend fun findByUrl(url: String): ReadingItem?

    /** Inserts or updates an item. */
    suspend fun save(item: ReadingItem)
    suspend fun updateStatus(id: String, status: ReadingStatus)
    suspend fun setReminder(id: String, reminderAt: Instant?)
    suspend fun delete(id: String)
}
