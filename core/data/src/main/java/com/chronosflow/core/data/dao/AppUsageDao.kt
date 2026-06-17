package com.chronosflow.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chronosflow.core.data.model.AppUsageDayEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage_days WHERE date = :date LIMIT 1")
    fun observeForDate(date: LocalDate): Flow<AppUsageDayEntity?>

    @Query("SELECT * FROM app_usage_days WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<AppUsageDayEntity>>

    @Query("SELECT * FROM app_usage_days WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<AppUsageDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: AppUsageDayEntity)
}
