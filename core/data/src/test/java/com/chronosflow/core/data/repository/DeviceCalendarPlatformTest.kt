package com.chronosflow.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceCalendarPlatformTest {
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private val platform = DeviceCalendarPlatform()

    @Test
    fun `instances uri appends range to calendar instance query`() {
        val start = Instant.parse("2026-01-02T10:00:00Z")
        val end = Instant.parse("2026-01-02T11:30:00Z")

        val uri = platform.instancesUri(start, end)

        assertTrue(uri.toString().startsWith(CalendarContract.Instances.CONTENT_URI.toString()))
        assertTrue(uri.toString().endsWith("/${start.toEpochMilli()}/${end.toEpochMilli()}"))
    }

    @Test
    fun `find writable calendar id prefers the primary calendar`() {
        val cursor: Cursor = mockk()
        every { context.contentResolver } returns contentResolver
        every {
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                any(),
                any(),
                any(),
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
            )
        } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getLong(0) } returns 77L
        every { cursor.close() } just runs

        val result = platform.findWritableCalendarId(context)

        assertEquals(77L, result)
        verify {
            cursor.moveToFirst()
            cursor.getLong(0)
            cursor.close()
        }
    }

    @Test
    fun `find writable calendar id returns null when no rows exist`() {
        val cursor: Cursor = mockk()
        every { context.contentResolver } returns contentResolver
        every { contentResolver.query(CalendarContract.Calendars.CONTENT_URI, any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns false
        every { cursor.close() } just runs

        val result = platform.findWritableCalendarId(context)

        assertEquals(null, result)
        verify {
            cursor.moveToFirst()
            cursor.close()
        }
    }

    @Test
    fun `build event values includes calendar metadata for running block`() {
        val timeBlock = timeBlock("America/Chicago")
        val values = platform.buildEventValues(timeBlock, calendarId = 42L)
        val expectedStart = timeBlock.date.atStartOfDay(ZoneId.of("America/Chicago")).plusMinutes(120L).toInstant()
        val expectedEnd = expectedStart.plusSeconds(30L * 60L)

        assertEquals(42L, values.getAsLong(CalendarContract.Events.CALENDAR_ID))
        assertEquals(timeBlock.title, values.getAsString(CalendarContract.Events.TITLE))
        assertEquals("Exported from ChronosFlow", values.getAsString(CalendarContract.Events.DESCRIPTION))
        assertEquals("America/Chicago", values.getAsString(CalendarContract.Events.EVENT_TIMEZONE))
        assertEquals(expectedStart.toEpochMilli(), values.getAsLong(CalendarContract.Events.DTSTART))
        assertEquals(expectedEnd.toEpochMilli(), values.getAsLong(CalendarContract.Events.DTEND))
        assertEquals(0, values.getAsInteger(CalendarContract.Events.ALL_DAY))
    }

    @Test
    fun `build event values uses system timezone for unsupported timezone strings`() {
        val fallbackZone = ZoneId.systemDefault()
        val timeBlock = timeBlock("not-a-valid/timezone")
        val values = platform.buildEventValues(timeBlock)

        assertEquals(fallbackZone.id, values.getAsString(CalendarContract.Events.EVENT_TIMEZONE))
    }

    @Test
    fun `parse event id from uri`() {
        val uri = Uri.parse("content://calendar/events/123")
        assertEquals(123L, platform.parseEventId(uri))
    }

    @Test
    fun `event uri appends event identifier`() {
        assertEquals(
            Uri.parse("content://com.android.calendar/events/99"),
            platform.eventUri(99L)
        )
    }

    private fun timeBlock(timezone: String): TimeBlock = TimeBlock(
        id = "time-block-1",
        date = LocalDate.parse("2026-01-02"),
        title = "Focus session",
        category = "WORK",
        startMinuteOfDay = 120,
        durationMinutes = 30,
        timezone = timezone,
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.RESIZABLE,
        energyLevel = EnergyIntensity.MODERATE,
        source = "USER",
        taskId = null,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.parse("2026-01-02T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-02T00:00:01Z")
    )
}
