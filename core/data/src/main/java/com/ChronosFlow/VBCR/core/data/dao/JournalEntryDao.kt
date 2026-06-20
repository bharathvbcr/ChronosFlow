package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ChronosFlow.VBCR.core.data.model.JournalEntryEntity
import com.ChronosFlow.VBCR.core.data.model.JournalEntrySummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries WHERE entryDate = :date ORDER BY isPrimary DESC, createdAt ASC")
    fun observeForDate(date: LocalDate): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE entryDate BETWEEN :start AND :end ORDER BY entryDate DESC, createdAt DESC")
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE entryDate BETWEEN :start AND :end ORDER BY entryDate DESC, createdAt DESC")
    fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getById(id: String): JournalEntryEntity?

    @Query("UPDATE journal_entries SET isPrimary = 0 WHERE entryDate = :date AND id != :keepId")
    suspend fun clearPrimaryForDate(date: LocalDate, keepId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntryEntity)

    @Transaction
    suspend fun insertAsPrimary(entry: JournalEntryEntity) {
        insert(entry)
        if (entry.isPrimary) clearPrimaryForDate(entry.entryDate, entry.id)
    }

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Paginated history load — avoids pulling an entire year into memory at once. */
    @Query("SELECT * FROM journal_entries ORDER BY entryDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getJournalEntriesPaged(limit: Int = 20, offset: Int = 0): List<JournalEntryEntity>

    /**
     * Lightweight projection for list/summary views that only need metadata,
     * not the potentially large [JournalEntryEntity.body] text.
     */
    @Query("SELECT id, entryDate, moodCheckInId, promptType, isPrimary, dayRating, entryMinuteOfDay, createdAt, updatedAt FROM journal_entries WHERE entryDate BETWEEN :start AND :end ORDER BY entryDate DESC, createdAt DESC")
    suspend fun getSummariesForDateRange(start: LocalDate, end: LocalDate): List<JournalEntrySummary>
}
