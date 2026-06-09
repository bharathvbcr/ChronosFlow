package com.chronosflow.core.domain.model

enum class TaskReminderTrigger {
    AT_TIME,
    BEFORE_OCCURRENCE
}

data class TaskReminderRule(
    val id: String,
    val taskScheduleId: String,
    val trigger: TaskReminderTrigger,
    val minuteOfDay: Int? = null,
    val offsetMinutesBefore: Int? = null
)
