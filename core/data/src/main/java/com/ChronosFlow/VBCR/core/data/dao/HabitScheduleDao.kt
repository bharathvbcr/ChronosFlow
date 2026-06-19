package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.HabitScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitScheduleDao {
    @Query("SELECT * FROM habit_schedules")
    fun observeAllSchedules(): Flow<List<HabitScheduleEntity>>

    @Query("SELECT * FROM habit_schedules WHERE habitId = :habitId")
    suspend fun getScheduleForHabit(habitId: String): HabitScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: HabitScheduleEntity)
}
