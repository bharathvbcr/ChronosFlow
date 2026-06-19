package com.ChronosFlow.VBCR.feature.habits

import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HabitWeekStripTest {

    private val today: LocalDate = LocalDate.parse("2026-06-13")

    @Test
    fun `collects completed event dates within the window`() {
        val habit = habit(
            events = listOf(
                event(HabitEventType.COMPLETED, today),
                event(HabitEventType.COMPLETED, today.minusDays(2)),
                event(HabitEventType.SKIPPED, today.minusDays(1))
            )
        )

        val days = habitCompletedDaysInWindow(habit, today, days = 7)

        assertEquals(setOf(today, today.minusDays(2)), days)
    }

    @Test
    fun `excludes completions older than the window`() {
        val habit = habit(
            events = listOf(event(HabitEventType.COMPLETED, today.minusDays(10)))
        )

        val days = habitCompletedDaysInWindow(habit, today, days = 7)

        assertTrue(days.isEmpty())
    }

    @Test
    fun `falls back to lastCompletedDate when in window and not in events`() {
        val habit = habit(events = emptyList(), lastCompletedDate = today.minusDays(1))

        val days = habitCompletedDaysInWindow(habit, today, days = 7)

        assertEquals(setOf(today.minusDays(1)), days)
    }

    @Test
    fun `empty window yields no days`() {
        val habit = habit(events = listOf(event(HabitEventType.COMPLETED, today)))

        assertTrue(habitCompletedDaysInWindow(habit, today, days = 0).isEmpty())
    }

    private fun habit(
        events: List<HabitEvent>,
        lastCompletedDate: LocalDate? = null
    ): Habit = Habit(
        id = "habit-1",
        title = "Stretch",
        cadence = "Daily",
        windowStartMinute = 8 * 60,
        windowEndMinute = 9 * 60,
        difficulty = 2,
        isBundled = false,
        streakCount = 0,
        lastCompletedDate = lastCompletedDate,
        isActive = true,
        recentEvents = events
    )

    private fun event(type: HabitEventType, date: LocalDate): HabitEvent = HabitEvent(
        id = "event-${type.name}-$date",
        habitId = "habit-1",
        type = type,
        eventDate = date,
        recordedAt = Instant.parse("2026-06-13T09:00:00Z"),
        reason = null,
        startMinuteOfDay = null,
        endMinuteOfDay = null
    )
}
