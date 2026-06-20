package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ChronosFlow.VBCR.core.data.model.JournalAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalAttachmentDao {
    @Query("SELECT * FROM journal_attachments WHERE journalEntryId = :entryId ORDER BY sortOrder ASC, createdAt ASC")
    fun observeForEntry(entryId: String): Flow<List<JournalAttachmentEntity>>

    /** Distinct ids of entries that have at least one attachment — drives the camera badge in lists. */
    @Query("SELECT DISTINCT journalEntryId FROM journal_attachments")
    fun observeAttachedEntryIds(): Flow<List<String>>

    /** Every attachment, ordered for display — grouped by entry to render thumbnails inline in lists. */
    @Query("SELECT * FROM journal_attachments ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<JournalAttachmentEntity>>

    @Query("SELECT COUNT(*) FROM journal_attachments WHERE journalEntryId = :entryId")
    suspend fun countForEntry(entryId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalAttachmentEntity)

    /**
     * Atomically reads the current attachment count for [entryId] and inserts [attachment] with
     * [JournalAttachmentEntity.sortOrder] set to that count. Wrapping both reads in a single
     * [Transaction] prevents two concurrent [addAttachment] calls from observing the same count and
     * writing duplicate sort-order values.
     */
    @Transaction
    suspend fun insertAttachmentWithOrder(attachment: JournalAttachmentEntity, entryId: String) {
        val count = countForEntry(entryId)
        insert(attachment.copy(sortOrder = count))
    }

    @Query("DELETE FROM journal_attachments WHERE id = :id")
    suspend fun deleteById(id: String)
}
