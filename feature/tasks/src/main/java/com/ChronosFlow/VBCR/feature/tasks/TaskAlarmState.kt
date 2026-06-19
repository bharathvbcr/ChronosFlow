package com.ChronosFlow.VBCR.feature.tasks

import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import java.time.Instant

internal enum class TaskAlarmStatus {
    EXACT,
    DEGRADED_WINDOW,
    NOTIFICATION_PERMISSION_REQUIRED,
    EXACT_PERMISSION_REQUIRED
}

internal data class TaskAlarmUiState(
    val status: TaskAlarmStatus,
    val scheduledFor: Instant?
)

internal fun AlarmRequest.toTaskAlarmUiState(): TaskAlarmUiState {
    val status = when {
        deliveryState == AlarmDeliveryState.FAILED &&
            failureReason?.contains("Notification", ignoreCase = true) == true ->
            TaskAlarmStatus.NOTIFICATION_PERMISSION_REQUIRED
        deliveryState == AlarmDeliveryState.FAILED ||
            reliability == AlarmReliability.BLOCKED ->
            TaskAlarmStatus.EXACT_PERMISSION_REQUIRED
        deliveryState == AlarmDeliveryState.DEGRADED ||
            reliability == AlarmReliability.DEGRADED_WINDOW ->
            TaskAlarmStatus.DEGRADED_WINDOW
        else -> TaskAlarmStatus.EXACT
    }
    return TaskAlarmUiState(
        status = status,
        scheduledFor = scheduledFor
    )
}
