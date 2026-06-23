package com.ChronosFlow.VBCR.core.domain.model

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
    // Evening nudge to log the night's sleep and capture the day's journal. Kept distinct from
    // DAILY_REVIEW so its call-to-action copy isn't replaced by the cached review digest.
    LOG_REMINDER,
    URGENT_TASK,
    // "Remind me to read later" nudge for a saved reading-list item. Taps open the reading list.
    READING_REMINDER
}

enum class AlarmDeliveryState {
    PENDING,
    SCHEDULED,
    DELIVERED,
    DEGRADED,
    FAILED,
    CANCELLED
}
