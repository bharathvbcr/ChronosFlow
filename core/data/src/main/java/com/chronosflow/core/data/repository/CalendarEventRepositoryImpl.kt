package com.chronosflow.core.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.chronosflow.core.data.dao.CalendarEventDao
import com.chronosflow.core.data.dao.TimeBlockDao
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_MARKER_MINUTES
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.CalendarEvent
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.model.classifyImportedEventEnergy
import com.chronosflow.core.domain.repository.CalendarEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

class CalendarEventRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val calendarEventDao: CalendarEventDao,
    private val timeBlockDao: TimeBlockDao,
    private val calendarPlatform: DeviceCalendarPlatform
) : CalendarEventRepository {
    override fun observeEventsBetween(start: Instant, end: Instant): Flow<List<CalendarEvent>> {
        return calendarEventDao.observeEventsBetween(start, end).map { events -> events.map { it.toDomain() } }
    }

    override suspend fun getCalendarEventById(id: Long): CalendarEvent? {
        return calendarEventDao.getCalendarEventById(id)?.toDomain()
    }

    override suspend fun saveCalendarEvent(event: CalendarEvent) {
        calendarEventDao.insertCalendarEvent(event.toEntity())
    }

    override suspend fun deleteCalendarEvent(event: CalendarEvent) {
        calendarEventDao.deleteCalendarEvent(event.toEntity())
    }

    override suspend fun syncFromDeviceCalendar(start: Instant, end: Instant) {
        if (appContext.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val syncZone = ZoneId.systemDefault()
        val startDate = start.atZone(syncZone).toLocalDate()
        val endDate = end.minusMillis(1).atZone(syncZone).toLocalDate()
        // Expand the window to full local days: the imported-block wipe below is
        // date-granular, so a mid-day window start would otherwise delete events
        // imported earlier the same day without re-importing them.
        val windowStart = startDate.atStartOfDay(syncZone).toInstant()
        val windowEnd = endDate.plusDays(1).atStartOfDay(syncZone).toInstant()
        timeBlockDao.deleteImportedBlocksBetween(startDate, endDate)
        // The mirror table is a snapshot of the synced window; clearing it first
        // drops events that were deleted or moved on the device.
        calendarEventDao.deleteEventsBetween(windowStart, windowEnd)

        val contentResolver = appContext.contentResolver
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_TIMEZONE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY
        )

        val cursor = contentResolver.query(
            calendarPlatform.instancesUri(windowStart, windowEnd),
            projection,
            null,
            null,
            null
        )

        cursor?.use { c ->
            while (c.moveToNext()) {
                val eventId = c.getLong(0)
                val title = c.getString(1) ?: "Untitled Event"
                val description = c.getString(2)
                val begin = c.getLong(3)
                val endMillis = c.getLong(4)
                val timezone = c.getString(5) ?: syncZone.id
                val location = c.getString(6)
                val allDay = c.getInt(7) == 1

                val domainEvent = CalendarEvent(
                    id = eventId,
                    title = title,
                    description = description,
                    startAt = Instant.ofEpochMilli(begin),
                    endAt = Instant.ofEpochMilli(endMillis),
                    timezone = timezone,
                    location = location,
                    externalId = "device_event_$eventId",
                    isAllDay = allDay
                )
                saveCalendarEvent(domainEvent)
                domainEvent.toImportedTimeBlocks(windowStart, windowEnd, syncZone).forEach { importedBlock ->
                    timeBlockDao.insertTimeBlock(importedBlock.toEntity())
                }
            }
        }
    }

    override suspend fun exportTimeBlock(timeBlock: TimeBlock): Long? {
        if (!hasCalendarAccess(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) {
            return null
        }
        val calendarId = calendarPlatform.findWritableCalendarId(appContext) ?: return null
        val uri = appContext.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI,
            calendarPlatform.buildEventValues(timeBlock, calendarId)
        ) ?: return null
        return calendarPlatform.parseEventId(uri)
    }

    override suspend fun updateExportedTimeBlock(timeBlock: TimeBlock): Boolean {
        val eventId = timeBlock.calendarEventId ?: return false
        if (!hasCalendarAccess(Manifest.permission.WRITE_CALENDAR)) {
            return false
        }
        return appContext.contentResolver.update(
            calendarPlatform.eventUri(eventId),
            calendarPlatform.buildEventValues(timeBlock),
            null,
            null
        ) > 0
    }

    override suspend fun deleteExportedTimeBlock(calendarEventId: Long): Boolean {
        if (!hasCalendarAccess(Manifest.permission.WRITE_CALENDAR)) {
            return false
        }
        return appContext.contentResolver.delete(
            calendarPlatform.eventUri(calendarEventId),
            null,
            null
        ) > 0
    }

    private fun hasCalendarAccess(vararg permissions: String): Boolean {
        return permissions.all { permission ->
            appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun CalendarEvent.toImportedTimeBlocks(
        syncWindowStart: Instant,
        syncWindowEnd: Instant,
        syncZone: ZoneId
    ): List<TimeBlock> {
        if (isAllDay) {
            return toImportedAllDayTimeBlocks(syncWindowStart, syncWindowEnd, syncZone)
        }

        val clippedStart = maxOf(startAt, syncWindowStart)
        val clippedEnd = minOf(endAt, syncWindowEnd)
        if (!clippedStart.isBefore(clippedEnd)) {
            return emptyList()
        }

        // Split events crossing local midnight into one block per day so each
        // dial day shows its own segment instead of one block overflowing 24h.
        val firstDate = clippedStart.atZone(syncZone).toLocalDate()
        val lastDate = clippedEnd.minusMillis(1).atZone(syncZone).toLocalDate()
        return generateSequence(firstDate) { date ->
            date.plusDays(1).takeUnless { it.isAfter(lastDate) }
        }.mapNotNull { date ->
            val dayStart = date.atStartOfDay(syncZone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(syncZone).toInstant()
            val segmentStart = maxOf(clippedStart, dayStart)
            val segmentEnd = minOf(clippedEnd, dayEnd)
            if (!segmentStart.isBefore(segmentEnd)) {
                return@mapNotNull null
            }

            val localStart = segmentStart.atZone(syncZone)
            val startMinuteOfDay = localStart.hour * 60 + localStart.minute
            val durationMinutes = Duration.between(segmentStart, segmentEnd)
                .toMinutes()
                .coerceAtLeast(1)
                .coerceAtMost(1440)
                .toInt()

            TimeBlock(
                // The start minute keeps same-day instances of one recurring
                // event (which share the device EVENT_ID) from colliding.
                id = "calendar-import-$id-$date-$startMinuteOfDay",
                date = date,
                title = title,
                category = CALENDAR_EVENT_CATEGORY,
                startMinuteOfDay = startMinuteOfDay,
                durationMinutes = durationMinutes,
                timezone = syncZone.id,
                provenance = BlockProvenance.CALENDAR_IMPORTED,
                flexibility = BlockFlexibility.FIXED,
                energyLevel = classifyImportedEventEnergy(title, description),
                source = BlockProvenance.CALENDAR_IMPORTED.name,
                taskId = null,
                calendarEventId = id,
                medicationPlanId = null,
                habitId = null,
                isLocked = true,
                isProtected = true,
                recurrenceRuleId = null,
                actualStartMinuteOfDay = null,
                actualEndMinuteOfDay = null,
                createdAt = segmentStart,
                updatedAt = segmentEnd
            )
        }.toList()
    }

    private fun CalendarEvent.toImportedAllDayTimeBlocks(
        syncWindowStart: Instant,
        syncWindowEnd: Instant,
        syncZone: ZoneId
    ): List<TimeBlock> {
        val syncStartDate = syncWindowStart.atZone(syncZone).toLocalDate()
        val syncEndDate = syncWindowEnd.minusMillis(1).atZone(syncZone).toLocalDate()
        val eventStartDate = startAt.atZone(ZoneOffset.UTC).toLocalDate()
        val eventLastDate = allDayEventLastDate(eventStartDate)
        val firstImportDate = maxOf(syncStartDate, eventStartDate)
        val lastImportDate = minOf(syncEndDate, eventLastDate)

        if (firstImportDate.isAfter(lastImportDate)) {
            return emptyList()
        }

        return generateSequence(firstImportDate) { date ->
            date.plusDays(1).takeUnless { it.isAfter(lastImportDate) }
        }.map { importDate ->
            val localStart = importDate.atStartOfDay(syncZone)
            val localEnd = localStart.plusMinutes(ALL_DAY_CALENDAR_MARKER_MINUTES.toLong())
            TimeBlock(
                id = "calendar-import-$id-$importDate",
                date = importDate,
                title = title,
                category = ALL_DAY_CALENDAR_EVENT_CATEGORY,
                startMinuteOfDay = 0,
                durationMinutes = ALL_DAY_CALENDAR_MARKER_MINUTES,
                timezone = syncZone.id,
                provenance = BlockProvenance.CALENDAR_IMPORTED,
                flexibility = BlockFlexibility.OPTIONAL,
                energyLevel = EnergyIntensity.LOW,
                source = ALL_DAY_CALENDAR_EVENT_CATEGORY,
                taskId = null,
                calendarEventId = id,
                medicationPlanId = null,
                habitId = null,
                isLocked = false,
                isProtected = false,
                recurrenceRuleId = null,
                actualStartMinuteOfDay = null,
                actualEndMinuteOfDay = null,
                createdAt = localStart.toInstant(),
                updatedAt = localEnd.toInstant()
            )
        }.toList()
    }

    private fun CalendarEvent.allDayEventLastDate(eventStartDate: LocalDate): LocalDate {
        val exclusiveEndDate = endAt.atZone(ZoneOffset.UTC).toLocalDate()
        val lastDate = exclusiveEndDate.minusDays(1)
        return if (lastDate.isBefore(eventStartDate)) eventStartDate else lastDate
    }
}
