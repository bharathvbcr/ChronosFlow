package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.SleepTrackEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SleepTrackDao {
    @Query("SELECT * FROM sleep_tracks WHERE date = :date LIMIT 1")
    fun observeForDate(date: LocalDate): Flow<SleepTrackEntity?>

    /** Direct suspend lookup for a single night — use instead of observeForDate().first() in loops. */
    @Query("SELECT * FROM sleep_tracks WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: LocalDate): SleepTrackEntity?

    @Query("SELECT * FROM sleep_tracks WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<SleepTrackEntity>

    @Query("SELECT * FROM sleep_tracks WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<SleepTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: SleepTrackEntity)

    @Query("DELETE FROM sleep_tracks WHERE id = :id")
    suspend fun deleteById(id: String)
}
