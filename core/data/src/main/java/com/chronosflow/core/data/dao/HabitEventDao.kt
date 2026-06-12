package com.chronosflow.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chronosflow.core.data.model.HabitEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitEventDao {
    @Query("SELECT * FROM habit_events ORDER BY recordedAt DESC")
    fun observeAllEvents(): Flow<List<HabitEventEntity>>

    @Query("SELECT * FROM habit_events WHERE habitId = :habitId ORDER BY recordedAt DESC")
    suspend fun getEventsForHabit(habitId: String): List<HabitEventEntity>

    @Query("SELECT * FROM habit_events WHERE eventDate BETWEEN :start AND :end ORDER BY eventDate ASC")
    suspend fun getEventsBetween(start: java.time.LocalDate, end: java.time.LocalDate): List<HabitEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: HabitEventEntity)
}
