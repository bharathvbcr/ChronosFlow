package com.ChronosFlow.VBCR.core.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class DeviceCalendarPlatform @Inject constructor() {
    fun instancesUri(start: Instant, end: Instant): Uri {
        return CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, start.toEpochMilli())
            ContentUris.appendId(this, end.toEpochMilli())
        }.build()
    }

    fun findWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        // Prefer the account's primary calendar over whichever happens to sort first.
        val sortOrder = "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"

        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    fun buildEventValues(timeBlock: TimeBlock, calendarId: Long? = null): ContentValues {
        val startInstant = blockStartInstant(timeBlock)
        val endInstant = startInstant.plusSeconds(timeBlock.durationMinutes.toLong() * 60L)
        return ContentValues().apply {
            calendarId?.let { put(CalendarContract.Events.CALENDAR_ID, it) }
            put(CalendarContract.Events.TITLE, timeBlock.title)
            put(CalendarContract.Events.DESCRIPTION, "Exported from ChronosFlow")
            put(CalendarContract.Events.DTSTART, startInstant.toEpochMilli())
            put(CalendarContract.Events.DTEND, endInstant.toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, resolveZoneId(timeBlock).id)
            put(CalendarContract.Events.ALL_DAY, 0)
        }
    }

    fun parseEventId(uri: Uri): Long = ContentUris.parseId(uri)

    fun eventUri(eventId: Long): Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

    private fun blockStartInstant(timeBlock: TimeBlock): Instant {
        val zoneId = resolveZoneId(timeBlock)
        return timeBlock.date
            .atStartOfDay(zoneId)
            .plusMinutes(timeBlock.startMinuteOfDay.toLong())
            .toInstant()
    }

    private fun resolveZoneId(timeBlock: TimeBlock): ZoneId {
        return runCatching { ZoneId.of(timeBlock.timezone) }.getOrDefault(ZoneId.systemDefault())
    }
}
