package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepWorkWindowDetectorTest {
    private val detector = DeepWorkWindowDetector()

    @Test
    fun detectsAndRanksMorningDeepWorkWindow() {
        val blocks = listOf(
            block("Planning", "planning", start = 7 * 60, duration = 45),
            block("Lunch", "break", start = 12 * 60, duration = 45),
            block("Admin", "admin", start = 15 * 60, duration = 30)
        )

        val windows = detector.detect(blocks)

        assertTrue(windows.isNotEmpty())
        assertEquals(7 * 60 + 45, windows.first().startMinuteOfDay)
        assertTrue(windows.first().durationMinutes >= 90)
        assertTrue(windows.first().reason.contains("morning energy"))
    }

    @Test
    fun filtersShortGapsAndMergesOverlappingBlocks() {
        val blocks = listOf(
            block("A", "work", start = 8 * 60, duration = 80),
            block("B", "work", start = 9 * 60, duration = 80),
            block("C", "work", start = 12 * 60, duration = 45),
            block("D", "work", start = 14 * 60, duration = 40)
        )

        val windows = detector.detect(blocks, dayStartMinute = 8 * 60, dayEndMinute = 15 * 60)

        assertTrue(windows.none { it.durationMinutes < 90 })
        assertTrue(windows.any { it.startMinuteOfDay == 9 * 60 + 80 && it.endMinuteOfDay == 12 * 60 })
    }

    private fun block(
        title: String,
        category: String,
        start: Int,
        duration: Int,
        flexibility: BlockFlexibility = BlockFlexibility.MOVABLE
    ) = TimeBlock(
        id = title,
        date = LocalDate.of(2026, 5, 8),
        title = title,
        category = category,
        startMinuteOfDay = start,
        durationMinutes = duration,
        timezone = "America/Chicago",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = flexibility,
        energyLevel = EnergyIntensity.MODERATE,
        source = "test",
        taskId = null,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
