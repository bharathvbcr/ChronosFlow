package com.ChronosFlow.VBCR.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val isCompleted: Boolean,
    val priority: Int,
    val dueDate: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val preferredDurationMinutes: Int? = null,
    val preferredStartMinuteOfDay: Int? = null,
    val targetDate: LocalDate? = null,
    val checklist: List<TaskChecklistItem> = emptyList(),
    val linkedContact: TaskContactSnapshot? = null,
    val actions: List<TaskAction> = emptyList(),
    val attachments: List<TaskAttachment> = emptyList(),
    val goalId: String? = null,
    val origin: String? = null,
    val externalId: String? = null
)
