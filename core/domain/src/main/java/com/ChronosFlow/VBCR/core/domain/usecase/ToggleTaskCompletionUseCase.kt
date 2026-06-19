package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class ToggleTaskCompletionUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val taskScheduleRepository: TaskScheduleRepository,
    private val completeTaskOccurrenceUseCase: CompleteTaskOccurrenceUseCase
) {
    suspend operator fun invoke(taskId: String) {
        val task = repository.getTaskById(taskId) ?: return
        val now = Instant.now()
        val schedule = taskScheduleRepository.getTaskSchedule(taskId)
        if (schedule != null) {
            if (task.isCompleted) {
                repository.saveTask(task.copy(isCompleted = false, updatedAt = now))
                return
            }
            val occurrenceDate = schedule.nextOccurrenceDate ?: task.targetDate ?: LocalDate.now()
            val updatedSchedule = completeTaskOccurrenceUseCase(
                taskId = taskId,
                completedOccurrenceDate = occurrenceDate,
                updatedAt = now
            )
            repository.saveTask(
                task.copy(
                    isCompleted = updatedSchedule?.nextOccurrenceDate == null,
                    updatedAt = now
                )
            )
            return
        }
        val updatedTask = task.copy(
            isCompleted = !task.isCompleted,
            updatedAt = now
        )
        repository.saveTask(updatedTask)
    }
}
