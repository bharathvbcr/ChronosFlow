package com.ChronosFlow.VBCR.core.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitPlannerProjectionTest {

    @Test
    fun `buildLegacyHabitSchedule maps cadence into recurrence`() {
        val schedule = buildLegacyHabitSchedule(
            habitId = "habit-1",
            cadence = "Weekdays",
            windowStartMinute = 7 * 60,
            windowEndMinute = 9 * 60,
            plannerVisible = true
        )

        assertEquals(PlannerRecurrenceType.WEEKDAYS, schedule.recurrence.type)
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            ),
            schedule.recurrence.weekdays
        )
        assertEquals(7 * 60, schedule.targetStartMinute)
        assertEquals(9 * 60, schedule.targetEndMinute)
        assertTrue(schedule.plannerVisible)
    }

    @Test
    fun `buildLegacyHabitSchedule preserves every n day cadence strings`() {
        val schedule = buildLegacyHabitSchedule(
            habitId = "habit-1",
            cadence = "Every 3 days",
            windowStartMinute = 7 * 60,
            windowEndMinute = 9 * 60,
            plannerVisible = false
        )

        assertEquals(PlannerRecurrenceType.EVERY_N_DAYS, schedule.recurrence.type)
        assertEquals(3, schedule.recurrence.interval)
    }

    @Test
    fun `buildLegacyHabitSchedule preserves weekly interval weekdays from cadence strings`() {
        val schedule = buildLegacyHabitSchedule(
            habitId = "habit-1",
            cadence = "Every 2 weeks on Mon + Thu",
            windowStartMinute = 7 * 60,
            windowEndMinute = 9 * 60,
            plannerVisible = false
        )

        assertEquals(PlannerRecurrenceType.WEEKLY_INTERVAL, schedule.recurrence.type)
        assertEquals(2, schedule.recurrence.interval)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), schedule.recurrence.weekdays)
    }

    @Test
    fun `buildLegacyHabitSchedule preserves selected weekday cadence strings`() {
        val schedule = buildLegacyHabitSchedule(
            habitId = "habit-1",
            cadence = "Mon + Thu",
            windowStartMinute = 7 * 60,
            windowEndMinute = 9 * 60,
            plannerVisible = false
        )

        assertEquals(PlannerRecurrenceType.SELECTED_WEEKDAYS, schedule.recurrence.type)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), schedule.recurrence.weekdays)
    }

    @Test
    fun `buildLegacyHabitSchedule preserves quota cadence strings`() {
        val schedule = buildLegacyHabitSchedule(
            habitId = "habit-1",
            cadence = "1 time every 2 weeks",
            windowStartMinute = 7 * 60,
            windowEndMinute = 9 * 60,
            plannerVisible = false
        )

        assertEquals(PlannerRecurrence(), schedule.recurrence)
        assertEquals(
            HabitRecurrenceRule.Quota(
                targetCompletions = 1,
                periodUnit = HabitRecurrencePeriodUnit.WEEK,
                interval = 2
            ),
            schedule.recurrenceRule
        )
    }

    @Test
    fun `deriveHabitAnalytics uses completion history as source of truth`() {
        val today = LocalDate.of(2026, 5, 25)
        val now = Instant.parse("2026-05-25T14:00:00Z")
        val events = listOf(
            HabitEvent(
                id = "completed-1",
                habitId = "habit-1",
                type = HabitEventType.COMPLETED,
                eventDate = today,
                recordedAt = now,
                reason = null,
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 8 * 60 + 15
            ),
            HabitEvent(
                id = "completed-2",
                habitId = "habit-1",
                type = HabitEventType.COMPLETED,
                eventDate = today.minusDays(1),
                recordedAt = now.minusSeconds(24 * 60 * 60),
                reason = null,
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 8 * 60 + 20
            ),
            HabitEvent(
                id = "missed-1",
                habitId = "habit-1",
                type = HabitEventType.MISSED,
                eventDate = today.minusDays(2),
                recordedAt = now.minusSeconds(2 * 24 * 60 * 60),
                reason = "Window closed",
                startMinuteOfDay = null,
                endMinuteOfDay = null
            ),
            HabitEvent(
                id = "skipped-1",
                habitId = "habit-1",
                type = HabitEventType.SKIPPED,
                eventDate = today.minusDays(3),
                recordedAt = now.minusSeconds(3 * 24 * 60 * 60),
                reason = "Rest day",
                startMinuteOfDay = null,
                endMinuteOfDay = null
            )
        )

        val analytics = deriveHabitAnalytics(events = events, today = today)

        assertEquals(0.5f, analytics.adherenceRate, 0.0001f)
        assertEquals(2, analytics.completedCountLast7Days)
        assertEquals(1, analytics.missedCountLast14Days)
        assertEquals(1, analytics.skippedCountLast14Days)
        assertEquals(2, analytics.currentStreak)
        assertEquals(8 * 60, analytics.bestCompletionMinuteOfDay)
    }
}
