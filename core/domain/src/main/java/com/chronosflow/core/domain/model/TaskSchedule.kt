package com.chronosflow.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class TaskSchedule(
    val id: String,
    val taskId: String,
    val recurrenceRule: TaskRecurrenceRule,
    val occurrenceMinuteOfDay: Int? = null,
    val nextOccurrenceDate: LocalDate? = null,
    val lastCompletedOccurrenceDate: LocalDate? = null,
    val generatedThroughDate: LocalDate? = null,
    val isPaused: Boolean = false,
    val reminderRules: List<TaskReminderRule> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)
