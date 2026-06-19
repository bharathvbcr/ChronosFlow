package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.AppLaunchTarget
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDeliveryCoordinatorHabitLaunchTest {
    private val context: Context = mockk(relaxed = true)
    private val alarmRequestRepository: AlarmRequestRepository = mockk(relaxed = true)
    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val timeBlockRepository: TimeBlockRepository = mockk(relaxed = true)
    private val habitRepository: HabitRepository = mockk(relaxed = true)
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)

    private val currentBlockNotificationCoordinator: CurrentBlockNotificationCoordinator = mockk(relaxed = true)

    private val coordinator = AlarmDeliveryCoordinator(
        context = context,
        alarmRequestRepository = alarmRequestRepository,
        taskRepository = taskRepository,
        timeBlockRepository = timeBlockRepository,
        habitRepository = habitRepository,
        alarmScheduler = alarmScheduler,
        currentBlockNotificationCoordinator = currentBlockNotificationCoordinator
    )

    @Test
    fun `synthetic habit block id resolves launch target from request`() = runTest {
        val requestId = "daydial:2026-05-24:habit-journal:start"
        val habitTarget = AppLaunchTarget(label = "Journal", value = "package:com.example.journal")
        coEvery { alarmRequestRepository.getAlarmRequest(requestId) } returns alarmRequest(
            id = requestId,
            blockId = "habit-journal"
        )
        coEvery { habitRepository.getHabitById("journal") } returns habit(
            id = "journal",
            launchTarget = habitTarget
        )

        val target = invokeHabitLaunchTarget(coordinator, requestId)

        assertEquals(habitTarget, target)
    }

    @Test
    fun `legacy raw habit id resolves launch target from request block id`() = runTest {
        val requestId = "daydial:2026-05-24:journal:start"
        val habitTarget = AppLaunchTarget(label = "Journal", value = "package:com.example.journal")
        coEvery { alarmRequestRepository.getAlarmRequest(requestId) } returns alarmRequest(
            id = requestId,
            blockId = "journal"
        )
        coEvery { habitRepository.getHabitById("journal") } returns habit(
            id = "journal",
            launchTarget = habitTarget
        )

        val target = invokeHabitLaunchTarget(coordinator, requestId)

        assertEquals(habitTarget, target)
    }

    @Test
    fun `focus block id resolves launch target through block mapping`() = runTest {
        val requestId = "daydial:2026-05-24:focus-journal:start"
        val habitTarget = AppLaunchTarget(label = "Journal", value = "package:com.example.journal")
        coEvery { alarmRequestRepository.getAlarmRequest(requestId) } returns alarmRequest(
            id = requestId,
            blockId = "focus-journal"
        )
        coEvery { habitRepository.getHabitById("focus-journal") } returns null
        coEvery { timeBlockRepository.getTimeBlockById("focus-journal") } returns focusBlock(
            id = "focus-journal",
            date = LocalDate.of(2026, 5, 24),
            habitId = "journal"
        )
        coEvery { habitRepository.getHabitById("journal") } returns habit(
            id = "journal",
            launchTarget = habitTarget
        )

        val target = invokeHabitLaunchTarget(coordinator, requestId)

        assertEquals(habitTarget, target)
    }

    private suspend fun invokeHabitLaunchTarget(
        coordinator: AlarmDeliveryCoordinator,
        requestId: String
    ): AppLaunchTarget? {
        val method = AlarmDeliveryCoordinator::class.java.getDeclaredMethod(
            "loadHabitLaunchTargetForRequest",
            String::class.java,
            Continuation::class.java
        ).apply {
            isAccessible = true
        }

        return suspendCancellableCoroutine { cont ->
            val result = method.invoke(coordinator, requestId, cont)
            if (result != COROUTINE_SUSPENDED) {
                @Suppress("UNCHECKED_CAST")
                cont.resumeWith(kotlin.Result.success(result as AppLaunchTarget?))
            }
        }
    }

    private fun alarmRequest(
        id: String,
        blockId: String
    ): AlarmRequest = AlarmRequest(
        id = id,
        type = AlarmRequestType.BLOCK_START,
        scheduledFor = Instant.parse("2026-05-24T20:00:00Z"),
        title = "Journal starts now",
        message = "Journal starts now",
        medicationPlanId = null,
        blockId = blockId,
        reliability = AlarmReliability.INEXACT,
        deliveryState = AlarmDeliveryState.PENDING,
        createdAt = Instant.parse("2026-05-24T19:00:00Z"),
        updatedAt = Instant.parse("2026-05-24T19:00:00Z")
    )

    private fun habit(
        id: String,
        launchTarget: AppLaunchTarget
    ): Habit = Habit(
        id = id,
        title = "Journal",
        cadence = "Daily",
        windowStartMinute = 20 * 60,
        windowEndMinute = 20 * 60 + 5,
        difficulty = 1,
        isBundled = true,
        streakCount = 0,
        lastCompletedDate = null,
        isActive = true,
        launchTarget = launchTarget
    )

    private fun focusBlock(
        id: String,
        date: LocalDate,
        habitId: String
    ): TimeBlock = TimeBlock(
        id = id,
        date = date,
        title = "Journal focus block",
        category = "FOCUS",
        startMinuteOfDay = 20 * 60,
        durationMinutes = 15,
        timezone = ZoneId.systemDefault().id,
        source = "TEST",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.RESIZABLE,
        energyLevel = EnergyIntensity.MODERATE,
        taskId = null,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = habitId,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        taskOccurrenceDate = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.parse("2026-05-24T19:00:00Z"),
        updatedAt = Instant.parse("2026-05-24T19:00:00Z")
    )
}
