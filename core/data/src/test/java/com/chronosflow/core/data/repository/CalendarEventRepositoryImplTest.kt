package com.chronosflow.core.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import app.cash.turbine.test
import com.chronosflow.core.data.dao.TimeBlockDao
import com.chronosflow.core.data.dao.CalendarEventDao
import com.chronosflow.core.data.model.CalendarEventEntity
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_MARKER_MINUTES
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.CalendarEvent
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class CalendarEventRepositoryImplTest {
    private val context: Context = mockk()
    private val calendarEventDao: CalendarEventDao = mockk()
    private val timeBlockDao: TimeBlockDao = mockk()
    private val calendarPlatform: DeviceCalendarPlatform = mockk()
    private lateinit var repository: CalendarEventRepositoryImpl

    @Before
    fun setup() {
        repository = CalendarEventRepositoryImpl(context, calendarEventDao, timeBlockDao, calendarPlatform)
    }

    @Test
    fun `observeEventsBetween maps entities to domain models`() = runTest {
        val start = Instant.parse("2026-05-08T14:00:00Z")
        val end = Instant.parse("2026-05-08T15:00:00Z")
        every { calendarEventDao.observeEventsBetween(start, end) } returns flowOf(
            listOf(
                CalendarEventEntity(
                    id = 42L,
                    title = "Planning review",
                    description = "Weekly planning",
                    startAt = start,
                    endAt = end,
                    timezone = "America/Chicago",
                    location = "Studio",
                    externalId = "calendar-42",
                    isAllDay = false
                )
            )
        )

        repository.observeEventsBetween(start, end).test {
            val events = awaitItem()
            assertEquals(1, events.size)
            assertEquals(42L, events.first().id)
            assertEquals("Planning review", events.first().title)
            awaitComplete()
        }
    }

    @Test
    fun `saveCalendarEvent calls DAO insert`() = runTest {
        val start = Instant.parse("2026-05-08T14:00:00Z")
        val end = Instant.parse("2026-05-08T15:00:00Z")
        coEvery { calendarEventDao.insertCalendarEvent(any()) } returns Unit

        repository.saveCalendarEvent(
            CalendarEvent(
                id = 42L,
                title = "Planning review",
                description = "Weekly planning",
                startAt = start,
                endAt = end,
                timezone = "America/Chicago",
                location = "Studio",
                externalId = "calendar-42",
                isAllDay = false
            )
        )

        coVerify {
            calendarEventDao.insertCalendarEvent(
                match { it.id == 42L && it.title == "Planning review" && it.externalId == "calendar-42" }
            )
        }
    }

    @Test
    fun `syncFromDeviceCalendar imports synced events into planner blocks`() = runTest {
        val contentResolver = mockk<ContentResolver>()
        val cursor = mockk<Cursor>()
        val instancesUri = mockk<Uri>()
        val zoneId = ZoneId.systemDefault()
        val syncedDate = LocalDate.parse("2026-05-08")
        val start = syncedDate.atStartOfDay(zoneId).toInstant()
        val end = syncedDate.plusDays(1).atStartOfDay(zoneId).toInstant()
        val eventStart = syncedDate.atTime(9, 0).atZone(zoneId).toInstant()
        val eventEnd = syncedDate.atTime(10, 0).atZone(zoneId).toInstant()

        every { context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) } returns PackageManager.PERMISSION_GRANTED
        every { context.contentResolver } returns contentResolver
        every { calendarPlatform.instancesUri(start, end) } returns instancesUri
        every {
            contentResolver.query(instancesUri, any(), any(), any(), any())
        } returns cursor
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getLong(0) } returns 42L
        every { cursor.getString(1) } returns "Planning review"
        every { cursor.getString(2) } returns "Weekly planning"
        every { cursor.getLong(3) } returns eventStart.toEpochMilli()
        every { cursor.getLong(4) } returns eventEnd.toEpochMilli()
        every { cursor.getString(5) } returns "America/Chicago"
        every { cursor.getString(6) } returns "Studio"
        every { cursor.getInt(7) } returns 0
        every { cursor.close() } just runs
        coEvery { calendarEventDao.insertCalendarEvent(any()) } returns Unit
        coEvery { timeBlockDao.deleteImportedBlocksBetween(any(), any()) } returns Unit
        coEvery { timeBlockDao.insertTimeBlock(any()) } returns Unit

        repository.syncFromDeviceCalendar(start, end)

        coVerify {
            timeBlockDao.deleteImportedBlocksBetween(
                syncedDate,
                syncedDate
            )
        }
        coVerify {
            calendarEventDao.insertCalendarEvent(
                match { entity ->
                    entity.id == 42L &&
                        entity.title == "Planning review" &&
                        entity.externalId == "device_event_42"
                }
            )
        }
        coVerify {
            timeBlockDao.insertTimeBlock(
                match { entity ->
                    entity.id == "calendar-import-42-2026-05-08" &&
                        entity.title == "Planning review" &&
                        entity.category == "CALENDAR" &&
                        entity.provenance == BlockProvenance.CALENDAR_IMPORTED.name &&
                        entity.source == BlockProvenance.CALENDAR_IMPORTED.name &&
                        entity.calendarEventId == 42L &&
                        entity.isLocked &&
                        entity.isProtected
                }
            )
        }
    }

    @Test
    fun `syncFromDeviceCalendar imports all day events as non blocking markers`() = runTest {
        val contentResolver = mockk<ContentResolver>()
        val cursor = mockk<Cursor>()
        val instancesUri = mockk<Uri>()
        val zoneId = ZoneId.systemDefault()
        val syncedDate = LocalDate.parse("2026-05-08")
        val start = syncedDate.atStartOfDay(zoneId).toInstant()
        val end = syncedDate.plusDays(1).atStartOfDay(zoneId).toInstant()
        val eventStart = Instant.parse("2026-05-08T00:00:00Z")
        val eventEnd = Instant.parse("2026-05-09T00:00:00Z")

        every { context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) } returns PackageManager.PERMISSION_GRANTED
        every { context.contentResolver } returns contentResolver
        every { calendarPlatform.instancesUri(start, end) } returns instancesUri
        every {
            contentResolver.query(instancesUri, any(), any(), any(), any())
        } returns cursor
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getLong(0) } returns 77L
        every { cursor.getString(1) } returns "Birthday"
        every { cursor.getString(2) } returns null
        every { cursor.getLong(3) } returns eventStart.toEpochMilli()
        every { cursor.getLong(4) } returns eventEnd.toEpochMilli()
        every { cursor.getString(5) } returns "UTC"
        every { cursor.getString(6) } returns null
        every { cursor.getInt(7) } returns 1
        every { cursor.close() } just runs
        coEvery { calendarEventDao.insertCalendarEvent(any()) } returns Unit
        coEvery { timeBlockDao.deleteImportedBlocksBetween(any(), any()) } returns Unit
        coEvery { timeBlockDao.insertTimeBlock(any()) } returns Unit

        repository.syncFromDeviceCalendar(start, end)

        coVerify {
            timeBlockDao.insertTimeBlock(
                match { entity ->
                    entity.id == "calendar-import-77-2026-05-08" &&
                        entity.title == "Birthday" &&
                        entity.category == ALL_DAY_CALENDAR_EVENT_CATEGORY &&
                        entity.startMinuteOfDay == 0 &&
                        entity.durationMinutes == ALL_DAY_CALENDAR_MARKER_MINUTES &&
                        entity.flexibility == BlockFlexibility.OPTIONAL.name &&
                        entity.energyLevel == EnergyIntensity.LOW.level &&
                        entity.provenance == BlockProvenance.CALENDAR_IMPORTED.name &&
                        entity.source == ALL_DAY_CALENDAR_EVENT_CATEGORY &&
                        entity.calendarEventId == 77L &&
                        !entity.isLocked &&
                        !entity.isProtected
                }
            )
        }
    }

    @Test
    fun `syncFromDeviceCalendar imports each overlapping day for multi day all day events`() = runTest {
        val previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val contentResolver = mockk<ContentResolver>()
            val cursor = mockk<Cursor>()
            val instancesUri = mockk<Uri>()
            val zoneId = ZoneId.systemDefault()
            val syncedDate = LocalDate.parse("2026-05-08")
            val start = syncedDate.atStartOfDay(zoneId).toInstant()
            val end = syncedDate.plusDays(3).atStartOfDay(zoneId).toInstant()
            val eventStart = Instant.parse("2026-05-08T00:00:00Z")
            val eventEnd = Instant.parse("2026-05-11T00:00:00Z")

            every { context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) } returns PackageManager.PERMISSION_GRANTED
            every { context.contentResolver } returns contentResolver
            every { calendarPlatform.instancesUri(start, end) } returns instancesUri
            every {
                contentResolver.query(instancesUri, any(), any(), any(), any())
            } returns cursor
            every { cursor.moveToNext() } returnsMany listOf(true, false)
            every { cursor.getLong(0) } returns 88L
            every { cursor.getString(1) } returns "Festival"
            every { cursor.getString(2) } returns null
            every { cursor.getLong(3) } returns eventStart.toEpochMilli()
            every { cursor.getLong(4) } returns eventEnd.toEpochMilli()
            every { cursor.getString(5) } returns "UTC"
            every { cursor.getString(6) } returns null
            every { cursor.getInt(7) } returns 1
            every { cursor.close() } just runs
            coEvery { calendarEventDao.insertCalendarEvent(any()) } returns Unit
            coEvery { timeBlockDao.deleteImportedBlocksBetween(any(), any()) } returns Unit
            coEvery { timeBlockDao.insertTimeBlock(any()) } returns Unit

            repository.syncFromDeviceCalendar(start, end)

            coVerify(exactly = 3) { timeBlockDao.insertTimeBlock(any()) }
            coVerify { timeBlockDao.insertTimeBlock(match { it.id == "calendar-import-88-2026-05-08" }) }
            coVerify { timeBlockDao.insertTimeBlock(match { it.id == "calendar-import-88-2026-05-09" }) }
            coVerify { timeBlockDao.insertTimeBlock(match { it.id == "calendar-import-88-2026-05-10" }) }
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun `syncFromDeviceCalendar skips imports when calendar read access is missing`() = runTest {
        val zoneId = ZoneId.systemDefault()
        val syncedDate = LocalDate.parse("2026-05-08")
        val start = syncedDate.atStartOfDay(zoneId).toInstant()
        val end = syncedDate.plusDays(1).atStartOfDay(zoneId).toInstant()

        every { context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) } returns PackageManager.PERMISSION_DENIED

        repository.syncFromDeviceCalendar(start, end)

        coVerify(exactly = 0) { calendarEventDao.insertCalendarEvent(any()) }
        coVerify(exactly = 0) { timeBlockDao.deleteImportedBlocksBetween(any(), any()) }
        coVerify(exactly = 0) { timeBlockDao.insertTimeBlock(any()) }
    }

    @Test
    fun `syncFromDeviceCalendar does not import next day all day event into previous local day`() = runTest {
        val previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))
        try {
            val contentResolver = mockk<ContentResolver>()
            val cursor = mockk<Cursor>()
            val instancesUri = mockk<Uri>()
            val zoneId = ZoneId.systemDefault()
            val syncedDate = LocalDate.parse("2026-05-08")
            val start = syncedDate.atStartOfDay(zoneId).toInstant()
            val end = syncedDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            val eventStart = Instant.parse("2026-05-09T00:00:00Z")
            val eventEnd = Instant.parse("2026-05-10T00:00:00Z")

            every { context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) } returns PackageManager.PERMISSION_GRANTED
            every { context.contentResolver } returns contentResolver
            every { calendarPlatform.instancesUri(start, end) } returns instancesUri
            every {
                contentResolver.query(instancesUri, any(), any(), any(), any())
            } returns cursor
            every { cursor.moveToNext() } returnsMany listOf(true, false)
            every { cursor.getLong(0) } returns 99L
            every { cursor.getString(1) } returns "Next day holiday"
            every { cursor.getString(2) } returns null
            every { cursor.getLong(3) } returns eventStart.toEpochMilli()
            every { cursor.getLong(4) } returns eventEnd.toEpochMilli()
            every { cursor.getString(5) } returns "UTC"
            every { cursor.getString(6) } returns null
            every { cursor.getInt(7) } returns 1
            every { cursor.close() } just runs
            coEvery { calendarEventDao.insertCalendarEvent(any()) } returns Unit
            coEvery { timeBlockDao.deleteImportedBlocksBetween(any(), any()) } returns Unit

            repository.syncFromDeviceCalendar(start, end)

            coVerify {
                timeBlockDao.deleteImportedBlocksBetween(syncedDate, syncedDate)
            }
            coVerify {
                calendarEventDao.insertCalendarEvent(
                    match { entity ->
                        entity.id == 99L &&
                            entity.title == "Next day holiday" &&
                            entity.isAllDay
                    }
                )
            }
            coVerify(exactly = 0) { timeBlockDao.insertTimeBlock(any()) }
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun `exportTimeBlock inserts device calendar event and returns linked id`() = runTest {
        val contentResolver = mockk<ContentResolver>()
        val eventValues = mockk<ContentValues>()
        val insertedUri = mockk<Uri>()

        every { context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) } returns PackageManager.PERMISSION_GRANTED
        every { context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) } returns PackageManager.PERMISSION_GRANTED
        every { context.contentResolver } returns contentResolver
        every { calendarPlatform.findWritableCalendarId(context) } returns 7L
        every { calendarPlatform.buildEventValues(any(), 7L) } returns eventValues
        every { calendarPlatform.parseEventId(insertedUri) } returns 99L
        every { contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues) } returns insertedUri

        val result = repository.exportTimeBlock(timeBlock(calendarEventId = null))

        assertEquals(99L, result)
        verify {
            calendarPlatform.findWritableCalendarId(context)
            calendarPlatform.buildEventValues(any(), 7L)
            calendarPlatform.parseEventId(insertedUri)
        }
    }

    @Test
    fun `updateExportedTimeBlock updates linked device calendar event`() = runTest {
        val contentResolver = mockk<ContentResolver>()
        val eventValues = mockk<ContentValues>()
        val eventUri = mockk<Uri>()

        every { context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) } returns PackageManager.PERMISSION_GRANTED
        every { context.contentResolver } returns contentResolver
        every { calendarPlatform.eventUri(42L) } returns eventUri
        every { calendarPlatform.buildEventValues(any(), null) } returns eventValues
        every { contentResolver.update(eventUri, eventValues, isNull(), isNull()) } returns 1

        val result = repository.updateExportedTimeBlock(timeBlock(calendarEventId = 42L))

        assertTrue(result)
        verify {
            calendarPlatform.eventUri(42L)
            calendarPlatform.buildEventValues(any(), null)
        }
    }

    @Test
    fun `deleteExportedTimeBlock deletes linked device calendar event`() = runTest {
        val contentResolver = mockk<ContentResolver>()
        val eventUri = mockk<Uri>()

        every { context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) } returns PackageManager.PERMISSION_GRANTED
        every { context.contentResolver } returns contentResolver
        every { calendarPlatform.eventUri(42L) } returns eventUri
        every { contentResolver.delete(eventUri, isNull(), isNull()) } returns 1

        val result = repository.deleteExportedTimeBlock(42L)

        assertTrue(result)
        verify { calendarPlatform.eventUri(42L) }
    }

    @Test
    fun `exportTimeBlock returns null when calendar write access is missing`() = runTest {
        every { context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) } returns PackageManager.PERMISSION_GRANTED
        every { context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) } returns PackageManager.PERMISSION_DENIED

        val result = repository.exportTimeBlock(timeBlock(calendarEventId = null))

        assertEquals(null, result)
    }

    @Test
    fun `updateExportedTimeBlock returns false when block is not linked`() = runTest {
        every { context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) } returns PackageManager.PERMISSION_GRANTED

        val result = repository.updateExportedTimeBlock(timeBlock(calendarEventId = null))

        assertFalse(result)
    }

    private fun timeBlock(calendarEventId: Long?): TimeBlock {
        val start = Instant.parse("2026-05-08T14:00:00Z")
        val end = Instant.parse("2026-05-08T15:00:00Z")
        return TimeBlock(
            id = "block-1",
            date = LocalDate.parse("2026-05-08"),
            title = "Deep work",
            category = "WORK",
            startMinuteOfDay = 9 * 60,
            durationMinutes = 60,
            timezone = ZoneId.of("America/Chicago").id,
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "TEST",
            taskId = null,
            calendarEventId = calendarEventId,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = start,
            updatedAt = end
        )
    }
}
