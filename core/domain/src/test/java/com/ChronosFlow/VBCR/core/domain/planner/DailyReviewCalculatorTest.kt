package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSource
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyReviewCalculatorTest {
    private val calculator = DailyReviewCalculator()
    private val date = LocalDate.of(2026, 5, 8)
    private val zone = ZoneId.of("America/Chicago")

    @Test
    fun `actual segment with block id completes matching block`() {
        val block = timeBlock("block-1", startMinute = 9 * 60, durationMinutes = 60)
        val segment = actualSegment(
            id = "segment-1",
            blockId = "block-1",
            startMinute = 9 * 60,
            endMinute = 10 * 60
        )

        val review = calculator.calculate(date, listOf(block), listOf(segment), zone)

        assertEquals(1, review.completedBlockCount)
        assertEquals(0, review.missedBlockCount)
        assertEquals(0, review.missedMinutes)
    }

    @Test
    fun `actual segment without block id completes overlapping block`() {
        val block = timeBlock("block-1", startMinute = 9 * 60, durationMinutes = 60)
        val segment = actualSegment(
            id = "segment-1",
            blockId = null,
            startMinute = 9 * 60 + 15,
            endMinute = 9 * 60 + 45
        )

        val review = calculator.calculate(date, listOf(block), listOf(segment), zone)

        assertEquals(1, review.completedBlockCount)
        assertEquals(0, review.missedBlockCount)
        assertEquals(0, review.missedMinutes)
    }

    @Test
    fun `actual segment without overlap leaves block missed`() {
        val block = timeBlock("block-1", startMinute = 9 * 60, durationMinutes = 60)
        val segment = actualSegment(
            id = "segment-1",
            blockId = null,
            startMinute = 11 * 60,
            endMinute = 12 * 60
        )

        val review = calculator.calculate(date, listOf(block), listOf(segment), zone)

        assertEquals(0, review.completedBlockCount)
        assertEquals(1, review.missedBlockCount)
        assertEquals(60, review.missedMinutes)
    }

    @Test
    fun `open segment is closed against the injected now so repeated calls agree`() {
        val block = timeBlock("block-1", startMinute = 9 * 60, durationMinutes = 60)
        val openSegment = actualSegment(
            id = "segment-1",
            blockId = "block-1",
            startMinute = 9 * 60,
            endMinute = null
        )
        val fixedNow = date.atStartOfDay(zone).plusMinutes(10 * 60).toInstant()

        val first = calculator.calculate(date, listOf(block), listOf(openSegment), zone, now = fixedNow)
        val second = calculator.calculate(date, listOf(block), listOf(openSegment), zone, now = fixedNow)

        // Idempotent for identical inputs — the review must not depend on wall-clock time of invocation.
        assertEquals(first, second)
        assertEquals(60, first.actualMinutes)
    }

    private fun actualSegment(
        id: String,
        blockId: String?,
        startMinute: Int,
        endMinute: Int?
    ): ActualTimeSegment {
        val start = date.atStartOfDay(zone).plusMinutes(startMinute.toLong()).toInstant()
        val end = endMinute?.let { date.atStartOfDay(zone).plusMinutes(it.toLong()).toInstant() }
        return ActualTimeSegment(
            id = id,
            blockId = blockId,
            date = date,
            startInstant = start,
            endInstant = end,
            source = ActualTimeSource.SYSTEM_INFERENCE,
            confidence = 0.8f
        )
    }

    private fun timeBlock(
        id: String,
        startMinute: Int,
        durationMinutes: Int
    ): TimeBlock {
        val now = Instant.now()
        return TimeBlock(
            id = id,
            date = date,
            title = "Deep work",
            category = "FOCUS",
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            timezone = zone.id,
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "TEST",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = now,
            updatedAt = now
        )
    }
}
