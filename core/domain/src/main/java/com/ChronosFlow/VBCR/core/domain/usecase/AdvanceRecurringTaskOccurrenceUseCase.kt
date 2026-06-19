package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import javax.inject.Inject

/**
 * Advances the recurring schedule for the task occurrence linked to [block], if any. Shared by the
 * in-app completion (DayDialReviewDelegate) and the agent-facing completeTimeBlock AppFunction so
 * both advance recurring tasks identically.
 *
 * Only the schedule advancement (data) lives here; re-syncing the task's alarms is left to the
 * caller because that lives at the notification layer. The operation is naturally idempotent: once
 * the occurrence is advanced, a repeat call for the same block returns null (the schedule's
 * next-occurrence no longer matches the block's occurrence date).
 *
 * @return the (task, updatedSchedule) whose alarms the caller should re-sync, or null when [block]
 * has no linked recurring occurrence that is currently due.
 */
class AdvanceRecurringTaskOccurrenceUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val taskScheduleRepository: TaskScheduleRepository,
    private val completeTaskOccurrenceUseCase: CompleteTaskOccurrenceUseCase
) {
    suspend operator fun invoke(block: TimeBlock): Pair<Task, TaskSchedule>? {
        val taskId = block.taskId ?: return null
        val occurrenceDate = block.taskOccurrenceDate ?: return null
        val task = taskRepository.getTaskById(taskId) ?: return null
        val existingSchedule = taskScheduleRepository.getTaskSchedule(taskId) ?: return null
        if (existingSchedule.nextOccurrenceDate != occurrenceDate) return null
        val updatedSchedule = completeTaskOccurrenceUseCase(taskId, occurrenceDate) ?: return null
        return task to updatedSchedule
    }
}
