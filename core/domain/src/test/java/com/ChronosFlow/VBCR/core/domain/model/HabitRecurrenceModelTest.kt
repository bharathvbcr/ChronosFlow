package com.ChronosFlow.VBCR.core.domain.model

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HabitRecurrenceModelTest {

    @Test
    fun `habit schedules resolve legacy planner recurrence as scheduled rules`() {
        val plannerRecurrence = PlannerRecurrence(
            type = PlannerRecurrenceType.SELECTED_WEEKDAYS,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )

        val schedule = HabitSchedule(
            id = "schedule-1",
            habitId = "habit-1",
            recurrence = plannerRecurrence,
            targetStartMinute = 8 * 60,
            targetEndMinute = 10 * 60
        )

        assertEquals(
            HabitRecurrenceRule.Scheduled(plannerRecurrence),
            schedule.resolvedRecurrenceRule
        )
    }

    @Test
    fun `habit schedules preserve explicit quota recurrence rules`() {
        val quotaRule = HabitRecurrenceRule.Quota(
            targetCompletions = 2,
            periodUnit = HabitRecurrencePeriodUnit.WEEK
        )

        val schedule = HabitSchedule(
            id = "schedule-1",
            habitId = "habit-1",
            recurrenceRule = quotaRule,
            targetStartMinute = 8 * 60,
            targetEndMinute = 10 * 60
        )

        assertEquals(quotaRule, schedule.resolvedRecurrenceRule)
    }

    @Test
    fun `habit schedules reject mismatched scheduled recurrence sources`() {
        assertThrows(IllegalArgumentException::class.java) {
            HabitSchedule(
                id = "schedule-1",
                habitId = "habit-1",
                recurrence = PlannerRecurrence(type = PlannerRecurrenceType.DAILY),
                recurrenceRule = HabitRecurrenceRule.Scheduled(
                    PlannerRecurrence(type = PlannerRecurrenceType.WEEKLY_INTERVAL)
                ),
                targetStartMinute = 8 * 60,
                targetEndMinute = 10 * 60
            )
        }
    }

    @Test
    fun `quota recurrence rules reject non-positive target completions`() {
        assertThrows(IllegalArgumentException::class.java) {
            HabitRecurrenceRule.Quota(
                targetCompletions = 0,
                periodUnit = HabitRecurrencePeriodUnit.WEEK
            )
        }
    }

    @Test
    fun `quota recurrence rules reject non-positive intervals`() {
        assertThrows(IllegalArgumentException::class.java) {
            HabitRecurrenceRule.Quota(
                targetCompletions = 2,
                periodUnit = HabitRecurrencePeriodUnit.WEEK,
                interval = 0
            )
        }
    }

    @Test
    fun `habit schedules reject non-default legacy recurrence with quota rules`() {
        assertThrows(IllegalArgumentException::class.java) {
            HabitSchedule(
                id = "schedule-1",
                habitId = "habit-1",
                recurrence = PlannerRecurrence(type = PlannerRecurrenceType.WEEKLY_INTERVAL),
                recurrenceRule = HabitRecurrenceRule.Quota(
                    targetCompletions = 2,
                    periodUnit = HabitRecurrencePeriodUnit.WEEK
                ),
                targetStartMinute = 8 * 60,
                targetEndMinute = 10 * 60
            )
        }
    }
}
