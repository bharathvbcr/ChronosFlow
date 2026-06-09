package com.chronosflow.core.data.dao

import androidx.room.*
import com.chronosflow.core.data.model.TimeBlockEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TimeBlockDao {
    @Query("SELECT * FROM time_blocks WHERE date = :date ORDER BY startMinuteOfDay ASC")
    fun getTimeBlocksByDate(date: LocalDate): Flow<List<TimeBlockEntity>>

    @Query("SELECT * FROM time_blocks ORDER BY date ASC, startMinuteOfDay ASC")
    suspend fun getAllTimeBlocks(): List<TimeBlockEntity>

    @Query("SELECT * FROM time_blocks WHERE id = :id")
    suspend fun getById(id: String): TimeBlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeBlock(timeBlock: TimeBlockEntity)

    @Update
    suspend fun updateTimeBlock(timeBlock: TimeBlockEntity)

    @Delete
    suspend fun deleteTimeBlock(timeBlock: TimeBlockEntity)

    @Query(
        """
        DELETE FROM time_blocks
        WHERE provenance = 'CALENDAR_IMPORTED'
          AND date >= :startDate
          AND date <= :endDate
        """
    )
    suspend fun deleteImportedBlocksBetween(startDate: LocalDate, endDate: LocalDate)

    @Query("DELETE FROM time_blocks WHERE date = :date")
    suspend fun clearDay(date: LocalDate)
}
