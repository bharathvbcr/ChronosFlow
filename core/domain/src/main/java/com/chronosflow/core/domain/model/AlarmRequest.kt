package com.chronosflow.core.domain.model

import java.time.Instant

data class AlarmRequest(
    val id: String,
    val type: AlarmRequestType,
    val scheduledFor: Instant,
    val title: String,
    val message: String,
    val medicationPlanId: String?,
    val blockId: String?,
    val reliability: AlarmReliability,
    val deliveryState: AlarmDeliveryState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deliveredAt: Instant? = null,
    val failureReason: String? = null
)

enum class AlarmRequestType {
    MEDICATION,
    FOCUS_BLOCK,
    BLOCK_START,
    DAILY_REVIEW,
    URGENT_TASK
}

enum class AlarmDeliveryState {
    PENDING,
    SCHEDULED,
    DELIVERED,
    DEGRADED,
    FAILED,
    CANCELLED
}
