package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ResolveNextTaskOccurrenceUseCaseTest {

    private lateinit var useCase: ResolveNextTaskOccurrenceUseCase
    private val now = Instant.parse("2026-05-25T15:00:00Z")

    @Before
    fun setup() {
        useCase = ResolveNextTaskOccurrenceUseCase()
    }

    @Test
    fun `daily recurrence advances by interval days`() {
        val schedule = schedule(
            TaskRecurrenceRule.Daily(
                intervalDays = 2,
                startsOn = LocalDate.of(2026, 5, 25)
            )
        )

        val next = useCase(schedule, afterDate = LocalDate.of(2026, 5, 25))

        assertEquals(LocalDate.of(2026, 5, 27), next)
    }

    @Test
    fun `weekly recurrence returns next selected weekday in active interval`() {
        val schedule = schedule(
            TaskRecurrenceRule.Weekly(
                intervalWeeks = 1,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                startsOn = LocalDate.of(2026, 5, 25)
            )
        )

        val next = useCase(schedule, afterDate = LocalDate.of(2026, 5, 28))

        assertEquals(LocalDate.of(2026, 6, 1), next)
    }

    @Test
    fun `monthly day of month clamps into shorter months`() {
        val schedule = schedule(
            TaskRecurrenceRule.MonthlyByDayOfMonth(
                intervalMonths = 1,
                dayOfMonth = 31,
                startsOn = LocalDate.of(2026, 1, 31)
            )
        )

        val next = useCase(schedule, afterDate = LocalDate.of(2026, 1, 31))

        assertEquals(LocalDate.of(2026, 2, 28), next)
    }

    @Test
    fun `monthly ordinal weekday resolves last friday`() {
        val schedule = schedule(
            TaskRecurrenceRule.MonthlyByOrdinalWeekday(
                intervalMonths = 1,
                ordinal = -1,
                weekday = DayOfWeek.FRIDAY,
                startsOn = LocalDate.of(2026, 5, 29)
            )
        )

        val next = useCase(schedule, afterDate = LocalDate.of(2026, 5, 29))

        assertEquals(LocalDate.of(2026, 6, 26), next)
    }

    @Test
    fun `returns null after max occurrences are exhausted`() {
        val schedule = schedule(
            TaskRecurrenceRule.Daily(
                intervalDays = 1,
                startsOn = LocalDate.of(2026, 5, 25),
                maxOccurrences = 2
            )
        )

        val next = useCase(schedule, afterDate = LocalDate.of(2026, 5, 26))

        assertNull(next)
    }

    @Test
    fun `returns null when end date has passed`() {
        val schedule = schedule(
            TaskRecurrenceRule.Daily(
                intervalDays = 1,
                startsOn = LocalDate.of(2026, 5, 25),
                endsOn = LocalDate.of(2026, 5, 27)
            )
        )

        val next = useCase(schedule, afterDate = LocalDate.of(2026, 5, 27))

        assertNull(next)
    }

    private fun schedule(recurrenceRule: TaskRecurrenceRule): TaskSchedule = TaskSchedule(
        id = "schedule-1",
        taskId = "task-1",
        recurrenceRule = recurrenceRule,
        createdAt = now,
        updatedAt = now
    )
}
