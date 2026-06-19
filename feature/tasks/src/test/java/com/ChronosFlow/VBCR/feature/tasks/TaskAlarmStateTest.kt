package com.ChronosFlow.VBCR.feature.tasks

import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TaskAlarmStateTest {
    @Test
    fun `toTaskAlarmUiState maps failed notification errors to notification permission needed`() {
        val request = AlarmRequest(
            id = "alarm-1",
            type = AlarmRequestType.URGENT_TASK,
            scheduledFor = Instant.parse("2026-05-25T09:00:00Z"),
            title = "Reminder",
            message = "Check this item",
            medicationPlanId = null,
            blockId = null,
            reliability = AlarmReliability.BLOCKED,
            deliveryState = AlarmDeliveryState.FAILED,
            createdAt = Instant.parse("2026-05-25T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-25T08:00:00Z"),
            failureReason = "Post Notification permission not enabled"
        )

        val state = request.toTaskAlarmUiState()

        assertEquals(TaskAlarmStatus.NOTIFICATION_PERMISSION_REQUIRED, state.status)
    }

    @Test
    fun `toTaskAlarmUiState maps failed schedule to exact permission required`() {
        val request = AlarmRequest(
            id = "alarm-2",
            type = AlarmRequestType.URGENT_TASK,
            scheduledFor = Instant.parse("2026-05-25T09:00:00Z"),
            title = "Reminder",
            message = "Check this item",
            medicationPlanId = null,
            blockId = null,
            reliability = AlarmReliability.INEXACT,
            deliveryState = AlarmDeliveryState.FAILED,
            createdAt = Instant.parse("2026-05-25T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-25T08:00:00Z"),
            failureReason = "Unable to reach alarm service"
        )

        val state = request.toTaskAlarmUiState()

        assertEquals(TaskAlarmStatus.EXACT_PERMISSION_REQUIRED, state.status)
    }

    @Test
    fun `toTaskAlarmUiState maps blocked reliability to exact permission required`() {
        val request = AlarmRequest(
            id = "alarm-3",
            type = AlarmRequestType.URGENT_TASK,
            scheduledFor = Instant.parse("2026-05-25T09:00:00Z"),
            title = "Reminder",
            message = "Check this item",
            medicationPlanId = null,
            blockId = null,
            reliability = AlarmReliability.BLOCKED,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = Instant.parse("2026-05-25T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-25T08:00:00Z")
        )

        val state = request.toTaskAlarmUiState()

        assertEquals(TaskAlarmStatus.EXACT_PERMISSION_REQUIRED, state.status)
    }

    @Test
    fun `toTaskAlarmUiState maps degraded states to degraded window`() {
        val request = AlarmRequest(
            id = "alarm-4",
            type = AlarmRequestType.URGENT_TASK,
            scheduledFor = Instant.parse("2026-05-25T09:00:00Z"),
            title = "Reminder",
            message = "Check this item",
            medicationPlanId = null,
            blockId = null,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.DEGRADED,
            createdAt = Instant.parse("2026-05-25T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-25T08:00:00Z")
        )

        val state = request.toTaskAlarmUiState()

        assertEquals(TaskAlarmStatus.DEGRADED_WINDOW, state.status)
    }

    @Test
    fun `toTaskAlarmUiState maps normal states to exact`() {
        val request = AlarmRequest(
            id = "alarm-5",
            type = AlarmRequestType.URGENT_TASK,
            scheduledFor = Instant.parse("2026-05-25T09:00:00Z"),
            title = "Reminder",
            message = "Check this item",
            medicationPlanId = null,
            blockId = null,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.SCHEDULED,
            createdAt = Instant.parse("2026-05-25T08:00:00Z"),
            updatedAt = Instant.parse("2026-05-25T08:00:00Z")
        )

        val state = request.toTaskAlarmUiState()

        assertEquals(TaskAlarmStatus.EXACT, state.status)
    }
}
