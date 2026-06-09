package com.chronosflow.feature.focus

import com.chronosflow.core.ai.findNextFocusBlock
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FocusNextBlockSuggestionTest {
    @Test
    fun `picks earliest upcoming block after current minute`() {
        val today = LocalDate.of(2026, 5, 25)
        val blocks = listOf(
            block("a", "Morning review", 540, today),
            block("b", "Deep work", 600, today),
            block("c", "Wrap-up", 900, today)
        )

        val next = findNextFocusBlock(blocks, currentMinute = 570)

        assertEquals("b", next?.id)
        assertEquals("Deep work", next?.title)
    }

    private fun block(
        id: String,
        title: String,
        startMinute: Int,
        date: LocalDate
    ): TimeBlock {
        val now = Instant.parse("2026-05-25T08:00:00Z")
        return TimeBlock(
            id = id,
            date = date,
            title = title,
            category = "WORK",
            startMinuteOfDay = startMinute,
            durationMinutes = 60,
            timezone = "UTC",
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.FIXED,
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
            createdAt = now,
            updatedAt = now
        )
    }
}
