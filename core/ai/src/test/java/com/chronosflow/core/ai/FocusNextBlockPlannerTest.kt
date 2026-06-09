package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.AssistTextGeneration
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusNextBlockPlannerTest {
    @Test
    fun `suggestNextBlock uses AI pick when available`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "b|Deep work is the next protected block",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = FocusNextBlockPlanner(coordinator)
        val blocks = sampleBlocks()

        val suggestion = planner.suggestNextBlock(blocks, currentMinute = 570)

        assertEquals("b", suggestion?.id)
        assertEquals(FocusAssistSource.GEMINI_NANO, suggestion?.source)
    }

    @Test
    fun `findNextFocusBlock picks earliest upcoming block`() {
        val blocks = sampleBlocks()
        val next = findNextFocusBlock(blocks, currentMinute = 570)
        assertEquals("b", next?.id)
    }

    @Test
    fun `findNextFocusBlock ignores all day calendar imports when wrapping to earliest block`() {
        val today = LocalDate.of(2026, 5, 25)
        val now = Instant.parse("2026-05-25T08:00:00Z")
        val blocks = listOf(
            block(
                id = "holiday",
                title = "City festival",
                startMinute = 0,
                durationMinutes = 15,
                date = today,
                now = now,
                category = ALL_DAY_CALENDAR_EVENT_CATEGORY,
                provenance = BlockProvenance.CALENDAR_IMPORTED,
                flexibility = BlockFlexibility.OPTIONAL,
                calendarEventId = 77L
            ),
            block("a", "Morning review", 540, today, now),
            block("b", "Deep work", 600, today, now)
        )

        val next = findNextFocusBlock(blocks, currentMinute = 23 * 60)

        assertEquals("a", next?.id)
    }

    @Test
    fun `suggestNextBlock ignores AI pick for all day calendar import`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "holiday|The festival is on your calendar",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = FocusNextBlockPlanner(coordinator)
        val today = LocalDate.of(2026, 5, 25)
        val now = Instant.parse("2026-05-25T08:00:00Z")
        val blocks = listOf(
            block(
                id = "holiday",
                title = "City festival",
                startMinute = 0,
                durationMinutes = 15,
                date = today,
                now = now,
                category = ALL_DAY_CALENDAR_EVENT_CATEGORY,
                provenance = BlockProvenance.CALENDAR_IMPORTED,
                flexibility = BlockFlexibility.OPTIONAL,
                calendarEventId = 77L
            ),
            block("b", "Deep work", 600, today, now)
        )

        val suggestion = planner.suggestNextBlock(blocks, currentMinute = 570)

        assertEquals("b", suggestion?.id)
        assertEquals(FocusAssistSource.LOCAL, suggestion?.source)
    }

    private fun sampleBlocks(): List<TimeBlock> {
        val today = LocalDate.of(2026, 5, 25)
        val now = Instant.parse("2026-05-25T08:00:00Z")
        return listOf(
            block("a", "Morning review", 540, today, now),
            block("b", "Deep work", 600, today, now),
            block("c", "Wrap-up", 900, today, now)
        )
    }

    private fun block(
        id: String,
        title: String,
        startMinute: Int,
        date: LocalDate,
        now: Instant,
        durationMinutes: Int = 60,
        category: String = "WORK",
        provenance: BlockProvenance = BlockProvenance.USER_CREATED,
        flexibility: BlockFlexibility = BlockFlexibility.FIXED,
        calendarEventId: Long? = null
    ) = TimeBlock(
        id = id,
        date = date,
        title = title,
        category = category,
        startMinuteOfDay = startMinute,
        durationMinutes = durationMinutes,
        timezone = "UTC",
        provenance = provenance,
        flexibility = flexibility,
        energyLevel = EnergyIntensity.MODERATE,
        source = "test",
        taskId = null,
        calendarEventId = calendarEventId,
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
