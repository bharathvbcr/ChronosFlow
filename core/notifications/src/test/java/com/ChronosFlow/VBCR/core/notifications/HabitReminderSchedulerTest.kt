package com.ChronosFlow.VBCR.core.notifications

import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitReminderSchedulerTest {
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private val alarmRequestRepository: AlarmRequestRepository = mockk(relaxed = true)
    private val scheduler = HabitReminderScheduler(alarmScheduler, alarmRequestRepository)

    @Test
    fun `due habit schedules next reminder with deterministic daydial id`() = runTest {
        val now = Instant.parse("2026-05-24T19:00:00Z")
        val habit = habit(id = "journal", title = "Journal", startMinute = 20 * 60)
        every { alarmRequestRepository.observeRequestsByType(AlarmRequestType.BLOCK_START) } returns flowOf(emptyList())
        every {
            alarmScheduler.scheduleInexactAlarm(
                "daydial:2026-05-24:habit-journal:start",
                Instant.parse("2026-05-24T20:00:00Z"),
                "Journal",
                "Journal starts now"
            )
        } returns AlarmScheduleResult.Scheduled("daydial:2026-05-24:habit-journal:start", exact = false)
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit

        val result = scheduler.syncUpcomingHabitReminder(habit, now, ZoneOffset.UTC)

        assertTrue(result is AlarmScheduleResult.Scheduled)
        coVerify {
            alarmRequestRepository.saveAlarmRequest(
                match {
                    it.id == "daydial:2026-05-24:habit-journal:start" &&
                        it.blockId == "habit-journal" &&
                        it.type == AlarmRequestType.BLOCK_START &&
                        it.reliability == AlarmReliability.INEXACT &&
                        it.deliveryState == AlarmDeliveryState.SCHEDULED
                }
            )
        }
    }

    @Test
    fun `completed habit schedules the next due day instead of today`() = runTest {
        val now = Instant.parse("2026-05-24T19:00:00Z")
        val habit = habit(id = "journal", title = "Journal", startMinute = 20 * 60)
            .copy(lastCompletedDate = LocalDate.of(2026, 5, 24))
        every { alarmRequestRepository.observeRequestsByType(AlarmRequestType.BLOCK_START) } returns flowOf(emptyList())
        every {
            alarmScheduler.scheduleInexactAlarm(
                "daydial:2026-05-25:habit-journal:start",
                Instant.parse("2026-05-25T20:00:00Z"),
                "Journal",
                "Journal starts now"
            )
        } returns AlarmScheduleResult.Scheduled("daydial:2026-05-25:habit-journal:start", exact = false)
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit

        val result = scheduler.syncUpcomingHabitReminder(habit, now, ZoneOffset.UTC)

        assertTrue(result is AlarmScheduleResult.Scheduled)
        verify {
            alarmScheduler.scheduleInexactAlarm(
                "daydial:2026-05-25:habit-journal:start",
                Instant.parse("2026-05-25T20:00:00Z"),
                "Journal",
                "Journal starts now"
            )
        }
    }

    @Test
    fun `cancel upcoming habit reminders cancels persisted habit block alarms`() = runTest {
        val now = Instant.parse("2026-05-24T19:00:00Z")
        val existing = alarmRequest(
            id = "daydial:2026-05-24:habit-journal:start",
            blockId = "habit-journal",
            now = now
        )
        every { alarmRequestRepository.observeRequestsByType(AlarmRequestType.BLOCK_START) } returns flowOf(listOf(existing))
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit

        scheduler.cancelUpcomingHabitReminders("journal", now)

        verify { alarmScheduler.cancelAlarm("daydial:2026-05-24:habit-journal:start") }
        coVerify {
            alarmRequestRepository.saveAlarmRequest(
                match {
                    it.id == existing.id &&
                        it.deliveryState == AlarmDeliveryState.CANCELLED &&
                        it.failureReason == "Cancelled by habit reminder resync"
                }
            )
        }
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
        windowEndMinute = startMinute + 15,
        difficulty = 1,
        isBundled = true,
        streakCount = 0,
        lastCompletedDate = null,
        isActive = true
    )

    private fun alarmRequest(
        id: String,
        blockId: String,
        now: Instant
    ): AlarmRequest = AlarmRequest(
        id = id,
        type = AlarmRequestType.BLOCK_START,
        scheduledFor = now.plusSeconds(3600),
        title = "Journal",
        message = "Journal starts now",
        medicationPlanId = null,
        blockId = blockId,
        reliability = AlarmReliability.INEXACT,
        deliveryState = AlarmDeliveryState.SCHEDULED,
        createdAt = now.minusSeconds(60),
        updatedAt = now.minusSeconds(60)
    )
}
