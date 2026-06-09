package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.FreeTimeCalculator
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TaskScheduleRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

class ScheduleTaskIntoDayUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val taskScheduleRepository: TaskScheduleRepository,
    private val plannerService: PlannerService,
    private val freeTimeCalculator: FreeTimeCalculator,
    private val sleepScheduleRepository: SleepScheduleRepository
) {
    suspend operator fun invoke(
        taskId: String,
        date: LocalDate? = null,
        preferredDurationMinutes: Int? = null,
        nowMinuteOfDay: Int? = null,
        currentDate: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): PlannerOperationResult {
        val task = taskRepository.getTaskById(taskId)
            ?: return PlannerOperationResult.Rejected("Task not found", taskId)
        if (task.isCompleted) {
            return PlannerOperationResult.Rejected("Completed tasks cannot be scheduled", taskId)
        }

        val taskSchedule = taskScheduleRepository.getTaskSchedule(taskId)
        val scheduledDate = date ?: taskSchedule?.nextOccurrenceDate ?: task.targetDate ?: currentDate
        val duration = (
            preferredDurationMinutes
                ?: task.preferredDurationMinutes
                ?: durationForPriority(task.priority)
            ).coerceIn(15, 180)
        val blocks = plannerService.getBlocksForDate(scheduledDate)
        val existingOccurrence = taskSchedule?.let { schedule ->
            blocks.firstOrNull { block ->
                block.taskId == task.id && block.taskOccurrenceDate == scheduledDate
            }
        }
        if (existingOccurrence != null) {
            return PlannerOperationResult.Applied(
                message = "Occurrence already scheduled for ${task.title}",
                blockId = existingOccurrence.id,
                snappedToMinute = existingOccurrence.startMinuteOfDay,
                affectedBlockIds = listOf(existingOccurrence.id)
            )
        }
        val sleepSchedule = sleepScheduleRepository.getSleepSchedule()
        val startMinute = findStartMinute(
            blocks = blocks,
            date = scheduledDate,
            duration = duration,
            preferredStartMinuteOfDay = task.preferredStartMinuteOfDay,
            nowMinuteOfDay = nowMinuteOfDay,
            sleepSchedule = sleepSchedule,
            currentDate = currentDate,
            currentTime = currentTime
        )
            ?: return PlannerOperationResult.Rejected(
                "No open $duration minute gap ${noGapDateLabel(scheduledDate, currentDate)}",
                taskId
            )

        val result = plannerService.createBlock(
            TimeBlock(
                id = UUID.randomUUID().toString(),
                date = scheduledDate,
                title = task.title,
                category = "TASK",
                startMinuteOfDay = startMinute,
                durationMinutes = duration,
                timezone = ZoneId.systemDefault().id,
                provenance = BlockProvenance.TASK_CONVERTED,
                flexibility = BlockFlexibility.RESIZABLE,
                energyLevel = energyForPriority(task.priority),
                source = "TASK",
                taskId = task.id,
                calendarEventId = null,
                medicationPlanId = null,
                habitId = null,
                isLocked = false,
                isProtected = task.priority >= 2,
                recurrenceRuleId = null,
                actualStartMinuteOfDay = null,
                actualEndMinuteOfDay = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                taskOccurrenceDate = taskSchedule?.let { scheduledDate }
            )
        )
        return if (result is PlannerOperationResult.Applied) {
            PlannerOperationResult.Applied(
                message = "Scheduled ${task.title} into DayDial",
                blockId = result.blockId ?: task.id,
                snappedToMinute = result.snappedToMinute,
                affectedBlockIds = result.affectedBlockIds
            )
        } else {
            result
        }
    }

    private fun durationForPriority(priority: Int): Int = when {
        priority >= 2 -> 60
        priority == 1 -> 45
        else -> 30
    }

    private fun energyForPriority(priority: Int): EnergyIntensity = when {
        priority >= 2 -> EnergyIntensity.HIGH
        priority == 1 -> EnergyIntensity.MODERATE
        else -> EnergyIntensity.LOW
    }

    private fun noGapDateLabel(scheduledDate: LocalDate, currentDate: LocalDate): String =
        if (scheduledDate == currentDate) "today" else "on $scheduledDate"

    private fun findStartMinute(
        blocks: List<TimeBlock>,
        date: LocalDate,
        duration: Int,
        preferredStartMinuteOfDay: Int?,
        nowMinuteOfDay: Int?,
        sleepSchedule: SleepSchedule,
        currentDate: LocalDate,
        currentTime: LocalTime
    ): Int? {
        val earliest = if (date == currentDate) {
            val minute = nowMinuteOfDay ?: currentTime.let { it.hour * 60 + it.minute }
            maxOf(snapUpToQuarterHour(minute), preferredStartMinuteOfDay ?: 0)
        } else {
            preferredStartMinuteOfDay ?: 8 * 60
        }
        return freeTimeCalculator.calculate(blocks)
            .asSequence()
            .map { segment -> maxOf(segment.startMinute, earliest) to segment.endMinute }
            .firstOrNull { (start, end) ->
                start in 0..1439 &&
                    end <= 1440 &&
                    start + duration <= end &&
                    !sleepSchedule.intersects(start, duration)
            }
            ?.first
    }

    private fun snapUpToQuarterHour(minute: Int): Int {
        val snapped = ((minute + 14) / 15) * 15
        return snapped.coerceAtMost(1439)
    }
}
