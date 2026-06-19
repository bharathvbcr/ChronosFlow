package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.ui.graphics.Color
import com.ChronosFlow.VBCR.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.feature.daydial.model.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayDialTemplateUtilsTest {

    @Test
    fun `formatDayDialMinute handles negative and overflow values`() {
        assertEquals("23:30", formatDayDialMinute(-30))
        assertEquals("00:00", formatDayDialMinute(1440))
    }

    @Test
    fun `parseDayDialBackupBlocks parses valid lines and ignores invalid ones`() {
        val backup = """
            block|Morning|08:30|25|WORK
            note|ignore
            block||09:15|999|MEETING
            block|BadTime|99:00|30|WORK
            block|Break|12:00|abc|WORK
        """.trimIndent()

        val result = parseDayDialBackupBlocks(backup)

        assertEquals(2, result.size)
        assertEquals("Morning", result[0].title)
        assertEquals(8 * 60 + 30, result[0].startMinute)
        assertEquals(25, result[0].durationMinutes)
        assertEquals("Imported Block", result[1].title)
        assertEquals(9 * 60 + 15, result[1].startMinute)
        assertEquals(240, result[1].durationMinutes)
    }

    @Test
    fun `inferDayDialCategory detects known title patterns first`() {
        val meetingBlock = sampleBlock(
            title = "Team Meeting",
            startMinute = 9 * 60,
            durationMinutes = 60,
            category = "WORK"
        )
        val breakBlock = sampleBlock(
            title = "Coffee Break",
            startMinute = 10 * 60,
            durationMinutes = 60,
            category = "WORK"
        )
        val unknown = sampleBlock(
            title = "Read",
            startMinute = 11 * 60,
            durationMinutes = 60,
            category = "WORK"
        )

        assertEquals("Meeting", inferDayDialCategory(meetingBlock))
        assertEquals("Break", inferDayDialCategory(breakBlock))
        assertEquals("Work", inferDayDialCategory(unknown))
    }

    @Test
    fun `inferDayDialCategory falls back to provenance`() {
        val aiBlock = sampleBlock(
            title = "Some task",
            startMinute = 10 * 60,
            durationMinutes = 60,
            provenance = BlockProvenance.AI_SUGGESTED.name
        )
        assertEquals("AI", inferDayDialCategory(aiBlock))
    }

    @Test
    fun `findActiveBlock returns null when not viewing today`() {
        val block = sampleBlock(startMinute = 9 * 60, durationMinutes = 60)

        assertNull(findActiveBlock(listOf(block), currentMinute = 9 * 60 + 30, forToday = false))
    }

    @Test
    fun `findActiveBlock returns current block when viewing today`() {
        val block = sampleBlock(startMinute = 9 * 60, durationMinutes = 60)

        assertEquals(block, findActiveBlock(listOf(block), currentMinute = 9 * 60 + 30, forToday = true))
    }

    @Test
    fun `findActiveBlock handles blocks that wrap past midnight`() {
        val block = sampleBlock(startMinute = 23 * 60 + 30, durationMinutes = 120)

        assertEquals(block, findActiveBlock(listOf(block), currentMinute = 23 * 60 + 45, forToday = true))
        assertEquals(block, findActiveBlock(listOf(block), currentMinute = 30, forToday = true))
        assertNull(findActiveBlock(listOf(block), currentMinute = 8 * 60, forToday = true))
    }

    @Test
    fun `findActiveBlock ignores completed block when viewing today`() {
        val block = sampleBlock(
            startMinute = 9 * 60,
            durationMinutes = 60,
            actualEndMinuteOfDay = 9 * 60 + 15
        )

        assertNull(findActiveBlock(listOf(block), currentMinute = 9 * 60 + 30, forToday = true))
    }

    @Test
    fun `findNextBlock returns first block when not viewing today`() {
        val early = sampleBlock(id = "early", startMinute = 8 * 60, durationMinutes = 30)
        val late = sampleBlock(id = "late", startMinute = 14 * 60, durationMinutes = 30)

        assertEquals(early, findNextBlock(listOf(late, early), currentMinute = 10 * 60, forToday = false))
    }

    @Test
    fun `findNextBlock returns null when today's remaining blocks have passed`() {
        val early = sampleBlock(id = "early", startMinute = 8 * 60, durationMinutes = 30)
        val late = sampleBlock(id = "late", startMinute = 14 * 60, durationMinutes = 30)

        assertNull(findNextBlock(listOf(late, early), currentMinute = 18 * 60, forToday = true))
    }

    @Test
    fun `findNextBlock skips completed blocks when viewing today`() {
        val completed = sampleBlock(
            id = "completed",
            startMinute = 11 * 60,
            durationMinutes = 60,
            actualEndMinuteOfDay = 11 * 60 + 15
        )
        val later = sampleBlock(id = "later", startMinute = 13 * 60, durationMinutes = 30)

        assertEquals(later, findNextBlock(listOf(completed, later), currentMinute = 10 * 60, forToday = true))
    }

    @Test
    fun `findActiveBlock ignores all day calendar imports`() {
        val allDay = sampleBlock(
            id = "birthday",
            startMinute = 0,
            durationMinutes = 1440,
            category = ALL_DAY_CALENDAR_EVENT_CATEGORY,
            provenance = BlockProvenance.CALENDAR_IMPORTED.name,
            calendarEventId = 77L
        )

        assertNull(findActiveBlock(listOf(allDay), currentMinute = 9 * 60, forToday = true))
    }

    @Test
    fun `findNextBlock ignores all day calendar imports when viewing future days`() {
        val allDay = sampleBlock(
            id = "festival",
            startMinute = 0,
            durationMinutes = 15,
            category = ALL_DAY_CALENDAR_EVENT_CATEGORY,
            provenance = BlockProvenance.CALENDAR_IMPORTED.name,
            calendarEventId = 88L
        )
        val morning = sampleBlock(id = "morning", startMinute = 9 * 60, durationMinutes = 60)

        assertEquals(morning, findNextBlock(listOf(allDay, morning), currentMinute = 10 * 60, forToday = false))
    }

    private fun sampleBlock(
        id: String = "block",
        title: String = "Block",
        startMinute: Int,
        durationMinutes: Int,
        actualEndMinuteOfDay: Int? = null,
        category: String = "WORK",
        provenance: String = BlockProvenance.USER_CREATED.name,
        calendarEventId: Long? = null
    ): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = id,
            title = title,
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            actualEndMinuteOfDay = actualEndMinuteOfDay,
            color = Color.Blue,
            provenance = provenance,
            calendarEventId = calendarEventId,
            category = category
        )
    }
}
