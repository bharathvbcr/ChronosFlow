package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ChronosFlow.VBCR.core.data.model.JournalEntryEntity
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
}
