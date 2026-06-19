package com.ChronosFlow.VBCR.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class TimeBlock(
    val id: String,
    val date: LocalDate,
    val title: String,
    val category: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val timezone: String,
    val provenance: BlockProvenance,
    val flexibility: BlockFlexibility,
    val energyLevel: EnergyIntensity,
    val source: String,
    val taskId: String?,
    val calendarEventId: Long?,
    val medicationPlanId: String?,
    val habitId: String?,
    val isLocked: Boolean,
    val isProtected: Boolean,
    val recurrenceRuleId: String?,
    val actualStartMinuteOfDay: Int?,
    val actualEndMinuteOfDay: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val taskOccurrenceDate: LocalDate? = null,
    val goalId: String? = null,
    val routineId: String? = null
) {
    init {
        require(startMinuteOfDay in 0..1439) { "startMinuteOfDay must be 0..1439" }
        require(durationMinutes in 1..1440) { "durationMinutes must be 1..1440" }
    }

    val plannedEndMinuteOfDay: Int
        get() = (startMinuteOfDay + durationMinutes) % 1440

    fun withUpdatedStart(startMinute: Int): TimeBlock = copy(
        startMinuteOfDay = ((startMinute % 1440) + 1440) % 1440,
        updatedAt = Instant.now()
    )

    fun withUpdatedDuration(durationMinutes: Int): TimeBlock = copy(
        durationMinutes = durationMinutes.coerceIn(1, 1440),
        updatedAt = Instant.now()
    )
}

