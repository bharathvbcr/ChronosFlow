package com.ChronosFlow.VBCR.feature.habits

import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitRecurrencePeriodUnit
import com.ChronosFlow.VBCR.core.domain.model.HabitRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.HabitSchedule
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrence
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitRecurrenceEditorTest {

    @Test
    fun buildHabitRecurrenceEditorStateReadsQuotaRuleFromSchedule() {
        val schedule = HabitSchedule(
            id = "schedule-1",
            habitId = "habit-1",
            recurrenceRule = HabitRecurrenceRule.Quota(
                targetCompletions = 2,
                periodUnit = HabitRecurrencePeriodUnit.WEEK
            ),
            targetStartMinute = 8 * 60,
            targetEndMinute = 9 * 60
        )

        val state = buildHabitRecurrenceEditorState(
            cadence = "Daily",
            schedule = schedule
        )

        assertEquals(HabitRecurrenceEditorKind.QUOTA, state.kind)
        assertEquals(2, state.quotaCompletions)
        assertEquals(HabitRecurrencePeriodUnit.WEEK, state.quotaPeriodUnit)
        assertEquals("2 times per week", state.summary())
        assertEquals("2 times per week", state.cadenceLabel())
    }

    @Test
    fun buildHabitRecurrenceEditorStateParsesSingularQuotaPerSummary() {
        val state = buildHabitRecurrenceEditorState(
            cadence = "1 time per week",
            schedule = null
        )

        assertEquals(HabitRecurrenceEditorKind.QUOTA, state.kind)
        assertEquals(1, state.quotaCompletions)
        assertEquals(HabitRecurrencePeriodUnit.WEEK, state.quotaPeriodUnit)
        assertEquals(1, state.quotaInterval)
        assertEquals("1 time per week", state.summary())
    }

    @Test
    fun buildHabitRecurrenceEditorStateParsesSingularQuotaIntervalSummary() {
        val state = buildHabitRecurrenceEditorState(
            cadence = "1 time every 2 weeks",
            schedule = null
        )

        assertEquals(HabitRecurrenceEditorKind.QUOTA, state.kind)
        assertEquals(1, state.quotaCompletions)
        assertEquals(HabitRecurrencePeriodUnit.WEEK, state.quotaPeriodUnit)
        assertEquals(2, state.quotaInterval)
        assertEquals("1 time every 2 weeks", state.summary())
    }

    @Test
    fun buildHabitRecurrenceEditorStateParsesMonthlyQuotaSummary() {
        val state = buildHabitRecurrenceEditorState(
            cadence = "1 time per month",
            schedule = null
        )

        assertEquals(HabitRecurrenceEditorKind.QUOTA, state.kind)
        assertEquals(1, state.quotaCompletions)
        assertEquals(HabitRecurrencePeriodUnit.MONTH, state.quotaPeriodUnit)
        assertEquals(1, state.quotaInterval)
        assertEquals("1 time per month", state.summary())
    }

    @Test
    fun toHabitScheduleBuildsWeeklyIntervalWithSelectedWeekdays() {
        val state = HabitRecurrenceEditorState(
            kind = HabitRecurrenceEditorKind.SCHEDULED,
            scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_WEEKS,
            interval = 2,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        )

        val schedule = state.toHabitSchedule(
            existingSchedule = null,
            habitId = "habit-1",
            targetStartMinute = 7 * 60,
            targetEndMinute = 8 * 60,
            plannerVisible = true
        )

        assertEquals(
            HabitRecurrenceRule.Scheduled(
                PlannerRecurrence(
                    type = PlannerRecurrenceType.WEEKLY_INTERVAL,
                    interval = 2,
                    weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
                )
            ),
            schedule.recurrenceRule
        )
        assertEquals("Every 2 weeks on Mon + Thu", state.summary())
        assertEquals("Every 2 weeks on Mon + Thu", state.cadenceLabel())
    }

    @Test
    fun buildHabitRecurrenceEditorStateParsesEveryNdDaysAndWeeksWithoutWeekdays() {
        val everyDays = buildHabitRecurrenceEditorState(
            cadence = "every 4 days",
            schedule = null
        )
        assertEquals(HabitRecurrenceEditorKind.SCHEDULED, everyDays.kind)
        assertEquals(HabitRecurrenceScheduleMode.EVERY_N_DAYS, everyDays.scheduleMode)
        assertEquals(4, everyDays.interval)

        val everyWeeks = buildHabitRecurrenceEditorState(
            cadence = "every 2 weeks",
            schedule = null
        )
        assertEquals(HabitRecurrenceEditorKind.SCHEDULED, everyWeeks.kind)
        assertEquals(HabitRecurrenceScheduleMode.EVERY_N_WEEKS, everyWeeks.scheduleMode)
        assertEquals(2, everyWeeks.interval)
    }

    @Test
    fun customPatternForEveryNdaysForcesMinimumInterval() {
        val state = HabitRecurrenceEditorState(
            kind = HabitRecurrenceEditorKind.SCHEDULED,
            scheduleMode = HabitRecurrenceScheduleMode.WEEKDAYS,
            interval = 1
        )

        val everyNDays = HabitRecurrenceCustomPattern.EVERY_N_DAYS.applyTo(state)

        assertEquals(HabitRecurrenceEditorKind.SCHEDULED, everyNDays.kind)
        assertEquals(HabitRecurrenceScheduleMode.EVERY_N_DAYS, everyNDays.scheduleMode)
        assertEquals(2, everyNDays.interval)
    }

    @Test
    fun buildHabitHistoryTemplatesUsesHumanReadableRecurrenceSummary() {
        val templates = buildHabitHistoryTemplates(
            habits = listOf(
                habit(
                    id = "habit-1",
                    title = "Read",
                    cadence = "Weekly",
                    schedule = HabitSchedule(
                        id = "schedule-1",
                        habitId = "habit-1",
                        recurrence = PlannerRecurrence(
                            type = PlannerRecurrenceType.WEEKLY_INTERVAL,
                            interval = 2,
                            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
                        ),
                        recurrenceRule = HabitRecurrenceRule.Scheduled(
                            PlannerRecurrence(
                                type = PlannerRecurrenceType.WEEKLY_INTERVAL,
                                interval = 2,
                                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
                            )
                        ),
                        targetStartMinute = 19 * 60,
                        targetEndMinute = 20 * 60
                    )
                )
            )
        )

        assertEquals("Every 2 weeks on Mon + Thu", templates.single().recurrenceSummary)
        assertEquals(
            "Read · Every 2 weeks on Mon + Thu · Saved",
            templates.single().displayLabel
        )
    }

    @Test
    fun buildHabitRecurrenceEditorStateParsesEveryNWeeksWithWeekdays() {
        val state = buildHabitRecurrenceEditorState(
            cadence = "every 2 weeks on mon/fri",
            schedule = null
        )

        assertEquals(HabitRecurrenceEditorKind.SCHEDULED, state.kind)
        assertEquals(HabitRecurrenceScheduleMode.EVERY_N_WEEKS, state.scheduleMode)
        assertEquals(2, state.interval)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), state.weekdays)
        assertEquals("Every 2 weeks on Mon + Fri", state.summary())
    }

    @Test
    fun buildHabitRecurrenceEditorStateParsesWeeklyOnPreset() {
        val state = buildHabitRecurrenceEditorState(
            cadence = "weekly on monday, wednesday, friday",
            schedule = null
        )

        assertEquals(HabitRecurrenceEditorKind.SCHEDULED, state.kind)
        assertEquals(HabitRecurrenceScheduleMode.EVERY_N_WEEKS, state.scheduleMode)
        assertEquals(1, state.interval)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            state.weekdays
        )
        assertEquals("Weekly on Mon + Wed + Fri", state.summary())
    }

    @Test
    fun buildHabitRecurrenceEditorStateFallsBackToDailyForUnknownInput() {
        val state = buildHabitRecurrenceEditorState(
            cadence = "every purple thing",
            schedule = null
        )

        assertEquals(HabitRecurrenceEditorKind.SCHEDULED, state.kind)
        assertEquals(HabitRecurrenceScheduleMode.DAILY, state.scheduleMode)
        assertEquals("Daily", state.summary())
    }

    @Test
    fun customPatternKeepsOnlyOnePrimaryScheduleField() {
        val state = HabitRecurrenceEditorState(
            kind = HabitRecurrenceEditorKind.SCHEDULED,
            scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_DAYS,
            interval = 1,
            weekdays = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
            quotaCompletions = 5
        )
        val everyWeeks = HabitRecurrenceCustomPattern.EVERY_N_WEEKS.applyTo(state)
        assertEquals(HabitRecurrenceEditorKind.SCHEDULED, everyWeeks.kind)
        assertEquals(HabitRecurrenceScheduleMode.EVERY_N_WEEKS, everyWeeks.scheduleMode)
        assertEquals(1, everyWeeks.interval)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), everyWeeks.weekdays)
    }

    @Test
    fun customPatternForQuotaClampsInvalidValues() {
        val state = HabitRecurrenceEditorState(
            kind = HabitRecurrenceEditorKind.SCHEDULED,
            quotaCompletions = 0,
            quotaInterval = 0
        )
        val quota = HabitRecurrenceCustomPattern.QUOTA.applyTo(state)
        assertEquals(HabitRecurrenceEditorKind.QUOTA, quota.kind)
        assertEquals(1, quota.quotaCompletions)
        assertEquals(1, quota.quotaInterval)
    }

    @Test
    fun toHabitScheduleConvertsScheduledModesToPlannerRecurrence() {
        val weekdaysState = HabitRecurrenceEditorState(
            kind = HabitRecurrenceEditorKind.SCHEDULED,
            scheduleMode = HabitRecurrenceScheduleMode.WEEKDAYS
        )
        val stateSchedule = weekdaysState.toHabitSchedule(
            existingSchedule = null,
            habitId = "habit-1",
            targetStartMinute = 8 * 60,
            targetEndMinute = 9 * 60,
            plannerVisible = false
        )

        assertEquals(
            HabitSchedule(
                id = "",
                habitId = "habit-1",
                recurrence = PlannerRecurrence(
                    type = PlannerRecurrenceType.WEEKDAYS,
                    weekdays = setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                    )
                ),
                recurrenceRule = HabitRecurrenceRule.Scheduled(
                    PlannerRecurrence(
                        type = PlannerRecurrenceType.WEEKDAYS,
                        weekdays = setOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY
                        )
                    )
                ),
                targetStartMinute = 8 * 60,
                targetEndMinute = 9 * 60,
                plannerVisible = false
            ),
            stateSchedule
        )
    }

    private fun habit(
        id: String,
        title: String,
        cadence: String,
        schedule: HabitSchedule? = null
    ): Habit = Habit(
        id = id,
        title = title,
        cadence = cadence,
        windowStartMinute = 8 * 60,
        windowEndMinute = 9 * 60,
        difficulty = 2,
        isBundled = false,
        streakCount = 0,
        lastCompletedDate = LocalDate.of(2026, 5, 24),
        isActive = true,
        schedule = schedule
    )
}
