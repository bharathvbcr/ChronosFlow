package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.InboxItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxItemDao {
    @Query("SELECT * FROM inbox_items WHERE triaged = 0 ORDER BY sortOrder ASC, createdAt DESC")
    fun observeUntriaged(): Flow<List<InboxItemEntity>>

    @Query("SELECT * FROM inbox_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<InboxItemEntity>>

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    suspend fun getById(id: String): InboxItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InboxItemEntity)

    @Query("UPDATE inbox_items SET triaged = 1, triagedTo = :outcome, triagedRefId = :refId WHERE id = :id")
    suspend fun markTriaged(id: String, outcome: String, refId: String?)

    @Query("DELETE FROM inbox_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
