package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.CalendarEventEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE startAt < :end AND endAt > :start ORDER BY startAt ASC")
    fun observeEventsBetween(start: Instant, end: Instant): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id ORDER BY startAt ASC LIMIT 1")
    suspend fun getCalendarEventById(id: Long): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEvent(event: CalendarEventEntity)

    @Delete
    suspend fun deleteCalendarEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE startAt < :end AND endAt > :start")
    suspend fun deleteEventsBetween(start: Instant, end: Instant)
}
