package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.buildLegacyHabitSchedule
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.notifications.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialReminderDelegateTest {
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private val alarmRequestRepository: AlarmRequestRepository = mockk(relaxed = true)
    private val habitRepository: HabitRepository = mockk(relaxed = true)
    private val date = LocalDate.of(2026, 5, 8)

    @Test
    fun `sleep window skips overnight block start reminders`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(
            listOf(
                timeBlock(
                    id = "sleep-block",
                    date = date,
                    startMinute = 22 * 60,
                    durationMinutes = 45
                )
            )
        )
        every { alarmScheduler.scheduleInexactAlarm(any(), any(), any(), any()) } answers {
            throw AssertionError("sleep-window reminders should not be scheduled")
        }
        every { alarmScheduler.scheduleExactAlarm(any(), any(), any(), any(), any()) } answers {
            throw AssertionError("sleep-window reminders should not be scheduled")
        }
        coEvery { alarmRequestRepository.getAlarmRequest(any()) } returns null
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = true,
            breakReminders = false,
            missedAlerts = false,
            endDayReviewReminder = false,
            sleepScheduleEnabled = true,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60
        )

        assertEquals("Skipped 1 reminders during sleep hours", delegate.reminderScheduleStatus.value)
        coVerify {
            alarmRequestRepository.saveAlarmRequest(
                match {
                    it.blockId == "sleep-block" &&
                        it.failureReason == "Reminder falls inside the sleep schedule"
                }
            )
        }
    }

    @Test
    fun `enabled reminder settings without blocks report no upcoming reminders`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = true,
            breakReminders = false,
            missedAlerts = true,
            endDayReviewReminder = false,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60
        )

        assertEquals("No upcoming reminders for this day", delegate.reminderScheduleStatus.value)
    }

    @Test
    fun `daily review reminder is scheduled when journal feature enabled`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        coEvery { alarmRequestRepository.getAlarmRequest(any()) } returns null
        every { alarmScheduler.scheduleInexactAlarm("daydial:$date:day:review", any(), any(), any()) } returns
            com.chronosflow.core.notifications.AlarmScheduleResult.Scheduled("daydial:$date:day:review", exact = false)
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = false,
            breakReminders = false,
            missedAlerts = false,
            endDayReviewReminder = true,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60,
            journalRemindersEnabled = true
        )

        coVerify {
            alarmScheduler.scheduleInexactAlarm("daydial:$date:day:review", any(), "Daily review", any())
        }
    }

    @Test
    fun `daily review reminder is suppressed when journal feature disabled`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = false,
            breakReminders = false,
            missedAlerts = false,
            endDayReviewReminder = true,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60,
            journalRemindersEnabled = false
        )

        coVerify(exactly = 0) {
            alarmScheduler.scheduleInexactAlarm("daydial:$date:day:review", any(), any(), any())
        }
    }

    @Test
    fun `sleep journal log reminder is scheduled when toggled on`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        coEvery { alarmRequestRepository.getAlarmRequest(any()) } returns null
        every { alarmScheduler.scheduleInexactAlarm("daydial:$date:day:logsleep", any(), any(), any()) } returns
            com.chronosflow.core.notifications.AlarmScheduleResult.Scheduled("daydial:$date:day:logsleep", exact = false)
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = false,
            breakReminders = false,
            missedAlerts = false,
            endDayReviewReminder = false,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60,
            sleepJournalLogReminder = true
        )

        coVerify {
            alarmScheduler.scheduleInexactAlarm("daydial:$date:day:logsleep", any(), "Log sleep & journal", any())
        }
    }

    @Test
    fun `sleep journal log reminder is suppressed when both capture surfaces disabled`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = false,
            breakReminders = false,
            missedAlerts = false,
            endDayReviewReminder = false,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60,
            sleepJournalLogReminder = true,
            sleepJournalRemindersEnabled = false
        )

        coVerify(exactly = 0) {
            alarmScheduler.scheduleInexactAlarm("daydial:$date:day:logsleep", any(), any(), any())
        }
    }

    @Test
    fun `sleep journal log reminder is not scheduled when toggled off`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = false,
            breakReminders = false,
            missedAlerts = false,
            endDayReviewReminder = false,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60,
            sleepJournalLogReminder = false
        )

        coVerify(exactly = 0) {
            alarmScheduler.scheduleInexactAlarm("daydial:$date:day:logsleep", any(), any(), any())
        }
    }

    @Test
    fun `all day calendar imports do not schedule block reminders`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(
            listOf(
                allDayCalendarImport(
                    id = "birthday",
                    date = date
                )
            )
        )
        every { alarmScheduler.scheduleInexactAlarm(any(), any(), any(), any()) } answers {
            throw AssertionError("all-day calendar imports should not schedule focus reminders")
        }
        every { alarmScheduler.scheduleExactAlarm(any(), any(), any(), any(), any()) } answers {
            throw AssertionError("all-day calendar imports should not schedule focus reminders")
        }
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = true,
            breakReminders = true,
            missedAlerts = true,
            endDayReviewReminder = false,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60
        )

        assertEquals("No upcoming reminders for this day", delegate.reminderScheduleStatus.value)
        coVerify(exactly = 0) {
            alarmRequestRepository.saveAlarmRequest(any())
        }
    }

    @Test
    fun `habit start reminder is scheduled for due habit`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(
            listOf(
                habit(
                    id = "journal",
                    title = "Journal",
                    startMinute = 20 * 60
                )
            )
        )
        coEvery { alarmRequestRepository.getAlarmRequest(any()) } returns null
        every {
            alarmScheduler.scheduleExactAlarm("daydial:$date:habit-journal:start", any(), "Journal", "Journal starts now", any())
        } returns com.chronosflow.core.notifications.AlarmScheduleResult.Scheduled(
            "daydial:$date:habit-journal:start",
            exact = true
        )
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = true,
            breakReminders = false,
            missedAlerts = false,
            endDayReviewReminder = false,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60
        )

        assertEquals("Scheduled 1 upcoming reminders", delegate.reminderScheduleStatus.value)
        coVerify {
            alarmScheduler.scheduleExactAlarm(
                "daydial:$date:habit-journal:start",
                any(),
                "Journal",
                "Journal starts now",
                any()
            )
        }
    }

    @Test
    fun `completed habit does not schedule habit reminder`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(
            listOf(
                Habit(
                    id = "journal",
                    title = "Journal",
                    cadence = "Daily",
                    windowStartMinute = 20 * 60,
                    windowEndMinute = 20 * 60 + 5,
                    difficulty = 1,
                    isBundled = true,
                    streakCount = 0,
                    lastCompletedDate = date,
                    isActive = true,
                    schedule = buildLegacyHabitSchedule(
                        habitId = "journal",
                        cadence = "Daily",
                        windowStartMinute = 20 * 60,
                        windowEndMinute = 20 * 60 + 5,
                        plannerVisible = true
                    )
                )
            )
        )
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = true,
            breakReminders = false,
            missedAlerts = false,
            endDayReviewReminder = false,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60
        )

        assertEquals("No upcoming reminders for this day", delegate.reminderScheduleStatus.value)
        coVerify(exactly = 0) {
            alarmScheduler.scheduleInexactAlarm(any(), any(), any(), any())
        }
    }

    @Test
    fun `skipped habit does not schedule habit reminder`() = runTest(UnconfinedTestDispatcher()) {
        val schedule = buildLegacyHabitSchedule(
            habitId = "journal",
            cadence = "Daily",
            windowStartMinute = 20 * 60,
            windowEndMinute = 20 * 60 + 5,
            plannerVisible = true
        ).copy(skipDate = date)
        val repository = FakeTimeBlockRepository(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(
            listOf(
                habit(
                    id = "journal",
                    title = "Journal",
                    startMinute = 20 * 60
                ).copy(schedule = schedule)
            )
        )
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = true,
            breakReminders = false,
            missedAlerts = false,
            endDayReviewReminder = false,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60
        )

        assertEquals("No upcoming reminders for this day", delegate.reminderScheduleStatus.value)
        coVerify(exactly = 0) {
            alarmScheduler.scheduleInexactAlarm(any(), any(), any(), any())
        }
    }

    @Test
    fun `completed block does not schedule block reminders`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeTimeBlockRepository(
            listOf(
                timeBlock(
                    id = "done-block",
                    date = date,
                    startMinute = 9 * 60,
                    durationMinutes = 45
                ).copy(
                    actualStartMinuteOfDay = 9 * 60,
                    actualEndMinuteOfDay = 9 * 60 + 45
                )
            )
        )
        every { alarmScheduler.scheduleInexactAlarm(any(), any(), any(), any()) } answers {
            throw AssertionError("completed blocks should not schedule reminders")
        }
        every { alarmScheduler.scheduleExactAlarm(any(), any(), any(), any(), any()) } answers {
            throw AssertionError("completed blocks should not schedule reminders")
        }
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        val delegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository)

        delegate.refreshReminderSchedule(
            scope = this,
            date = date,
            blockStartReminders = true,
            breakReminders = true,
            missedAlerts = true,
            endDayReviewReminder = false,
            sleepScheduleEnabled = false,
            sleepScheduleStartMinute = 21 * 60,
            sleepScheduleEndMinute = 7 * 60
        )

        assertEquals("No upcoming reminders for this day", delegate.reminderScheduleStatus.value)
    }

    private fun timeBlock(
        id: String,
        date: LocalDate,
        startMinute: Int,
        durationMinutes: Int
    ): TimeBlock {
        val now = Instant.parse("2026-05-08T12:00:00Z")
        return TimeBlock(
            id = id,
            date = date,
            title = "Late focus",
            category = "WORK",
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            timezone = "UTC",
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

    private fun allDayCalendarImport(
        id: String,
        date: LocalDate
    ): TimeBlock {
        return timeBlock(
            id = id,
            date = date,
            startMinute = 0,
            durationMinutes = 15
        ).copy(
            title = "Birthday",
            category = ALL_DAY_CALENDAR_EVENT_CATEGORY,
            provenance = BlockProvenance.CALENDAR_IMPORTED,
            calendarEventId = 42L,
            isLocked = false,
            isProtected = false
        )
    }

    private fun habit(
        id: String,
        title: String,
        startMinute: Int
    ): Habit = Habit(
        id = id,
        title = title,
        cadence = "Daily",
        windowStartMinute = startMinute,
        windowEndMinute = startMinute + 5,
        difficulty = 1,
        isBundled = true,
        streakCount = 0,
        lastCompletedDate = null,
        isActive = true
    )

    private class FakeTimeBlockRepository(
        private val blocks: List<TimeBlock>
    ) : TimeBlockRepository {
        override fun getTimeBlocksByDate(date: LocalDate): Flow<List<TimeBlock>> = flowOf(
            blocks.filter { it.date == date }
        )

        override suspend fun getTimeBlockById(id: String): TimeBlock? = blocks.firstOrNull { it.id == id }

        override suspend fun saveTimeBlock(timeBlock: TimeBlock) = Unit

        override suspend fun deleteTimeBlock(timeBlock: TimeBlock) = Unit

        override suspend fun clearDay(date: LocalDate) = Unit
    }
}
