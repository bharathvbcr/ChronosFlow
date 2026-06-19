package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class CompleteTaskOccurrenceUseCase @Inject constructor(
    private val taskScheduleRepository: TaskScheduleRepository,
    private val resolveNextTaskOccurrenceUseCase: ResolveNextTaskOccurrenceUseCase
) {
    suspend operator fun invoke(
        taskId: String,
        completedOccurrenceDate: LocalDate,
        updatedAt: Instant = Instant.now()
    ): TaskSchedule? {
        val schedule = taskScheduleRepository.getTaskSchedule(taskId) ?: return null
        val nextOccurrenceDate = resolveNextTaskOccurrenceUseCase(
            schedule = schedule,
            afterDate = completedOccurrenceDate
        )
        return schedule.copy(
            lastCompletedOccurrenceDate = completedOccurrenceDate,
            nextOccurrenceDate = nextOccurrenceDate,
            updatedAt = updatedAt
        ).also { updatedSchedule ->
            taskScheduleRepository.saveTaskSchedule(updatedSchedule)
        }
    }
}
