package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.CalendarEvent
import com.chronosflow.core.domain.model.TimeBlock
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface CalendarEventRepository {
    fun observeEventsBetween(start: Instant, end: Instant): Flow<List<CalendarEvent>>
    suspend fun getCalendarEventById(id: Long): CalendarEvent?
    suspend fun saveCalendarEvent(event: CalendarEvent)
    suspend fun deleteCalendarEvent(event: CalendarEvent)
    suspend fun syncFromDeviceCalendar(start: Instant, end: Instant)
    suspend fun exportTimeBlock(timeBlock: TimeBlock): Long?
    suspend fun updateExportedTimeBlock(timeBlock: TimeBlock): Boolean
    suspend fun deleteExportedTimeBlock(calendarEventId: Long): Boolean
}
