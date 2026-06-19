package com.ChronosFlow.VBCR.core.domain.model

import java.time.Instant

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val startAt: Instant,
    val endAt: Instant,
    val timezone: String,
    val location: String?,
    val externalId: String?,
    val isAllDay: Boolean
)
