package com.chronosflow.core.domain.planner

import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_MARKER_MINUTES
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CalendarImportSemanticsPlannerTest {
    private val date = LocalDate.of(2026, 5, 25)
    private val now = Instant.parse("2026-05-25T08:00:00Z")

    @Test
    fun `free time calculation ignores all day calendar markers`() {
        val freeTime = FreeTimeCalculator().calculate(
            listOf(
                block(
                    id = "holiday",
                    startMinute = 0,
                    durationMinutes = ALL_DAY_CALENDAR_MARKER_MINUTES,
                    category = ALL_DAY_CALENDAR_EVENT_CATEGORY,
                    provenance = BlockProvenance.CALENDAR_IMPORTED,
                    flexibility = BlockFlexibility.OPTIONAL,
                    calendarEventId = 77L
                )
            )
        )

        assertEquals(listOf(FreeTimeSegment(0, 1440)), freeTime)
    }

    @Test
    fun `conflict detection ignores legacy full day calendar imports`() {
        val conflicts = ConflictDetectionEngine().detect(
            listOf(
                block(
                    id = "legacy-birthday",
                    startMinute = 0,
                    durationMinutes = 1440,
                    category = "CALENDAR",
                    provenance = BlockProvenance.CALENDAR_IMPORTED,
                    flexibility = BlockFlexibility.FIXED,
                    calendarEventId = 88L,
                    isLocked = true,
                    isProtected = true
                ),
                block(id = "focus", startMinute = 9 * 60, durationMinutes = 60)
            )
        )

        assertTrue(conflicts.isEmpty())
    }

    private fun block(
        id: String,
        startMinute: Int,
        durationMinutes: Int,
        category: String = "WORK",
        provenance: BlockProvenance = BlockProvenance.USER_CREATED,
        flexibility: BlockFlexibility = BlockFlexibility.RESIZABLE,
        calendarEventId: Long? = null,
        isLocked: Boolean = false,
        isProtected: Boolean = false
    ): TimeBlock {
        return TimeBlock(
            id = id,
            date = date,
            title = id,
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
            isLocked = isLocked,
            isProtected = isProtected,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = now,
            updatedAt = now
        )
    }
}
