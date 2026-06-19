package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskChecklistItem
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(
        title: String,
        description: String? = null,
        priority: Int = 0,
        dueDate: Instant? = null,
        preferredDurationMinutes: Int? = null,
        preferredStartMinuteOfDay: Int? = null,
        targetDate: LocalDate? = null,
        checklist: List<TaskChecklistItem> = emptyList(),
        linkedContact: TaskContactSnapshot? = null,
        actions: List<TaskAction> = emptyList(),
        attachments: List<TaskAttachment> = emptyList()
    ): Task {
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            isCompleted = false,
            priority = priority,
            dueDate = dueDate,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            preferredDurationMinutes = preferredDurationMinutes,
            preferredStartMinuteOfDay = preferredStartMinuteOfDay,
            targetDate = targetDate,
            checklist = checklist,
            linkedContact = linkedContact,
            actions = actions,
            attachments = attachments
        )
        repository.saveTask(task)
        return task
    }
}
