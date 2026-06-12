package com.chronosflow.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chronosflow.core.data.model.SleepTrackEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SleepTrackDao {
    @Query("SELECT * FROM sleep_tracks WHERE date = :date LIMIT 1")
    fun observeForDate(date: LocalDate): Flow<SleepTrackEntity?>

    @Query("SELECT * FROM sleep_tracks WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<SleepTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: SleepTrackEntity)

    @Query("DELETE FROM sleep_tracks WHERE id = :id")
    suspend fun deleteById(id: String)
}
