package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.CalendarEvent
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface CalendarEventRepository {
    fun observeEventsBetween(start: Instant, end: Instant): Flow<List<CalendarEvent>>
    suspend fun getCalendarEventById(id: Long): CalendarEvent?
    suspend fun saveCalendarEvent(event: CalendarEvent)
    suspend fun deleteCalendarEvent(event: CalendarEvent)
    suspend fun syncFromDeviceCalendar(start: Instant, end: Instant)
    /** Epoch millis of the last successful device-calendar sync (null if never), as a live stream. */
    fun observeLastDeviceSyncAtMillis(): Flow<Long?>
    suspend fun exportTimeBlock(timeBlock: TimeBlock): Long?
    suspend fun updateExportedTimeBlock(timeBlock: TimeBlock): Boolean
    suspend fun deleteExportedTimeBlock(calendarEventId: Long): Boolean
}
