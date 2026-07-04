package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.HabitEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitEventDao {
    @Query("SELECT * FROM habit_events WHERE recordedAt >= :cutoff ORDER BY recordedAt DESC")
    fun observeAllEvents(cutoff: Long): Flow<List<HabitEventEntity>>

    @Query("SELECT * FROM habit_events WHERE habitId = :habitId ORDER BY recordedAt DESC LIMIT :limit")
    fun getRecentEventsForHabit(habitId: String, limit: Int = 10): Flow<List<HabitEventEntity>>

    @Query("SELECT * FROM habit_events WHERE habitId = :habitId ORDER BY recordedAt DESC")
    suspend fun getEventsForHabit(habitId: String): List<HabitEventEntity>

    @Query("SELECT * FROM habit_events WHERE eventDate BETWEEN :start AND :end ORDER BY eventDate ASC")
    fun observeEventsBetween(start: java.time.LocalDate, end: java.time.LocalDate): Flow<List<HabitEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: HabitEventEntity)

    @Query("DELETE FROM habit_events WHERE id = :id")
    suspend fun deleteEventById(id: String)
}
