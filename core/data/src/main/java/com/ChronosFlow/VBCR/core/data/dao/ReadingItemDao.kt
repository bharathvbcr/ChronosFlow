package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.ReadingItemEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ReadingItemDao {
    @Query("SELECT * FROM reading_items WHERE status != 'ARCHIVED' ORDER BY sortOrder ASC, addedAt DESC")
    fun observeActive(): Flow<List<ReadingItemEntity>>

    @Query("SELECT * FROM reading_items WHERE status = :status ORDER BY sortOrder ASC, addedAt DESC")
    fun observeByStatus(status: String): Flow<List<ReadingItemEntity>>

    @Query("SELECT * FROM reading_items WHERE id = :id")
    suspend fun getById(id: String): ReadingItemEntity?

    @Query("SELECT * FROM reading_items WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): ReadingItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReadingItemEntity)

    @Query("UPDATE reading_items SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Instant)

    @Query("UPDATE reading_items SET reminderAt = :reminderAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateReminder(id: String, reminderAt: Instant?, updatedAt: Instant)

    @Query("DELETE FROM reading_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
