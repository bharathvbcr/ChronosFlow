package com.chronosflow.core.notifications

import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmReliability
import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import com.chronosflow.core.domain.repository.HabitRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PendingAlarmReconcilerTest {
    private val alarmScheduler: AlarmScheduler = mockk()
    private val alarmRequestRepository: AlarmRequestRepository = mockk(relaxed = true)
    private val habitRepository: HabitRepository = mockk()
    private val habitReminderScheduler: HabitReminderScheduler = mockk()
    private val reconciler = PendingAlarmReconciler(
        alarmScheduler,
        alarmRequestRepository,
        habitRepository,
        habitReminderScheduler
    )

    @Test
    fun `reconcile persists exact scheduled state`() = runTest {
        val now = Instant.parse("2026-05-25T12:00:00Z")
        val request = alarmRequest(now = now)
        every { alarmScheduler.pruneExpiredPersistedReminders() } returns 2
        every { alarmRequestRepository.observePendingRequests(now) } returns flowOf(listOf(request))
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        coEvery { alarmScheduler.scheduleAlarmRequest(request) } returns AlarmScheduleResult.Scheduled("task:1", exact = true)
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit
        coEvery { habitReminderScheduler.syncUpcomingHabitReminders(emptyList(), now) } returns 0

        val result = reconciler.reconcile(now)

        assertEquals(2, result.prunedLegacyCount)
        assertEquals(1, result.scheduledCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.failedCount)
        coVerify { alarmScheduler.scheduleAlarmRequest(request) }
        coVerify { habitReminderScheduler.syncUpcomingHabitReminders(emptyList(), now) }
        coVerify {
            alarmRequestRepository.saveAlarmRequest(match {
                it.id == request.id &&
                    it.reliability == AlarmReliability.EXACT &&
                    it.deliveryState == AlarmDeliveryState.SCHEDULED &&
                    it.updatedAt == now &&
                    it.failureReason == null
            })
        }
    }

    @Test
    fun `reconcile persists degraded state when fallback window is used`() = runTest {
        val now = Instant.parse("2026-05-25T12:00:00Z")
        val request = alarmRequest(now = now, id = "med:1", type = AlarmRequestType.MEDICATION)
        every { alarmScheduler.pruneExpiredPersistedReminders() } returns 0
        every { alarmRequestRepository.observePendingRequests(now) } returns flowOf(listOf(request))
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        coEvery { alarmScheduler.scheduleAlarmRequest(request) } returns AlarmScheduleResult.Scheduled("med:1", exact = false)
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit
        coEvery { habitReminderScheduler.syncUpcomingHabitReminders(emptyList(), now) } returns 0

        val result = reconciler.reconcile(now)

        assertEquals(0, result.prunedLegacyCount)
        assertEquals(1, result.scheduledCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.failedCount)
        coVerify {
            alarmRequestRepository.saveAlarmRequest(match {
                it.id == request.id &&
                    it.reliability == AlarmReliability.DEGRADED_WINDOW &&
                    it.deliveryState == AlarmDeliveryState.DEGRADED &&
                    it.updatedAt == now &&
                    it.failureReason == "Exact alarm permission unavailable; scheduled with fallback window"
            })
        }
    }

    @Test
    fun `reconcile persists failed state when permission blocks scheduling`() = runTest {
        val now = Instant.parse("2026-05-25T12:00:00Z")
        val request = alarmRequest(now = now)
        every { alarmScheduler.pruneExpiredPersistedReminders() } returns 0
        every { alarmRequestRepository.observePendingRequests(now) } returns flowOf(listOf(request))
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        coEvery { alarmScheduler.scheduleAlarmRequest(request) } returns AlarmScheduleResult.ExactDenied("task:1")
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit
        coEvery { habitReminderScheduler.syncUpcomingHabitReminders(emptyList(), now) } returns 0

        val result = reconciler.reconcile(now)

        assertEquals(0, result.prunedLegacyCount)
        assertEquals(0, result.scheduledCount)
        assertEquals(0, result.skippedCount)
        assertEquals(1, result.failedCount)
        coVerify {
            alarmRequestRepository.saveAlarmRequest(match {
                it.id == request.id &&
                    it.reliability == AlarmReliability.BLOCKED &&
                    it.deliveryState == AlarmDeliveryState.FAILED &&
                    it.updatedAt == now &&
                    it.failureReason == "Exact alarm permission denied"
            })
        }
    }

    @Test
    fun `reconcile persists failed state when general permission denied`() = runTest {
        val now = Instant.parse("2026-05-25T12:00:00Z")
        val request = alarmRequest(now = now)
        every { alarmScheduler.pruneExpiredPersistedReminders() } returns 0
        every { alarmRequestRepository.observePendingRequests(now) } returns flowOf(listOf(request))
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        coEvery { alarmScheduler.scheduleAlarmRequest(request) } returns AlarmScheduleResult.PermissionDenied("task:1")
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit
        coEvery { habitReminderScheduler.syncUpcomingHabitReminders(emptyList(), now) } returns 0

        val result = reconciler.reconcile(now)

        assertEquals(1, result.failedCount)
        coVerify {
            alarmRequestRepository.saveAlarmRequest(match {
                it.deliveryState == AlarmDeliveryState.FAILED && it.failureReason == "Notification permission denied"
            })
        }
    }

    @Test
    fun `reconcile persists failed state when request is skipped`() = runTest {
        val now = Instant.parse("2026-05-25T12:00:00Z")
        val request = alarmRequest(now = now)
        every { alarmScheduler.pruneExpiredPersistedReminders() } returns 0
        every { alarmRequestRepository.observePendingRequests(now) } returns flowOf(listOf(request))
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        coEvery { alarmScheduler.scheduleAlarmRequest(request) } returns AlarmScheduleResult.Skipped("task:1", "Old reminder")
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit
        coEvery { habitReminderScheduler.syncUpcomingHabitReminders(emptyList(), now) } returns 0

        val result = reconciler.reconcile(now)

        assertEquals(1, result.skippedCount)
        coVerify {
            alarmRequestRepository.saveAlarmRequest(match {
                it.deliveryState == AlarmDeliveryState.FAILED && it.failureReason == "Old reminder"
            })
        }
    }

    private fun alarmRequest(
        now: Instant,
        id: String = "task:1",
        type: AlarmRequestType = AlarmRequestType.URGENT_TASK
    ): AlarmRequest {
        return AlarmRequest(
            id = id,
            type = type,
            scheduledFor = now.plusSeconds(3600),
            title = "Task",
            message = "Due",
            medicationPlanId = if (type == AlarmRequestType.MEDICATION) id else null,
            blockId = if (type == AlarmRequestType.MEDICATION) null else "1",
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = now.minusSeconds(60),
            updatedAt = now.minusSeconds(60)
        )
    }
}
