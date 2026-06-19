package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.SleepReadiness
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.model.deriveSleepReadiness
import com.ChronosFlow.VBCR.core.domain.planner.FreeTimeCalculator
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.domain.repository.MoodEnergyRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepTrackRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
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
    private val sleepScheduleRepository: SleepScheduleRepository,
    private val sleepTrackRepository: SleepTrackRepository,
    private val moodEnergyRepository: MoodEnergyRepository
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
        val energyLevel = energyForPriority(task.priority)
        // Last night only bears on today's plan: scheduling a future date ignores it.
        val readiness = if (scheduledDate == currentDate) {
            deriveSleepReadiness(mostRecentNight(currentDate))
        } else {
            SleepReadiness.UNKNOWN
        }
        // Demanding work is placed near the user's measured energy peak; lighter work keeps first-fit.
        // An explicit preferred start always wins, so we only seek a peak when none was set. After a
        // depleted night the historical morning peak is unreliable, so we skip it and defer instead.
        val peakEnergyHour =
            if (energyLevel == EnergyIntensity.HIGH &&
                task.preferredStartMinuteOfDay == null &&
                readiness != SleepReadiness.DEPLETED
            ) {
                peakEnergyHour(scheduledDate)
            } else {
                null
            }
        val startMinute = findStartMinute(
            blocks = blocks,
            date = scheduledDate,
            duration = duration,
            preferredStartMinuteOfDay = task.preferredStartMinuteOfDay,
            nowMinuteOfDay = nowMinuteOfDay,
            sleepSchedule = sleepSchedule,
            currentDate = currentDate,
            currentTime = currentTime,
            peakEnergyHour = peakEnergyHour,
            energyLevel = energyLevel,
            readiness = readiness
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
                energyLevel = energyLevel,
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
        currentTime: LocalTime,
        peakEnergyHour: Int?,
        energyLevel: EnergyIntensity,
        readiness: SleepReadiness
    ): Int? {
        val earliest = if (date == currentDate) {
            val minute = nowMinuteOfDay ?: currentTime.let { it.hour * 60 + it.minute }
            maxOf(snapUpToQuarterHour(minute), preferredStartMinuteOfDay ?: 0)
        } else {
            preferredStartMinuteOfDay ?: 8 * 60
        }
        val fittingGaps = freeTimeCalculator.calculate(blocks)
            .map { segment -> maxOf(segment.startMinute, earliest) to segment.endMinute }
            .filter { (start, end) ->
                start in 0..1439 &&
                    end <= 1440 &&
                    start + duration <= end &&
                    !sleepSchedule.intersects(start, duration)
            }
        if (fittingGaps.isEmpty()) return null

        // After a poor night, slide demanding work past the grogginess buffer. Soft preference: if no
        // gap reaches past the buffer we fall through to first-fit rather than fail to schedule.
        if (readiness == SleepReadiness.DEPLETED &&
            energyLevel == EnergyIntensity.HIGH &&
            preferredStartMinuteOfDay == null
        ) {
            val deferredEarliest = maxOf(earliest, DEPLETED_DEMANDING_FLOOR_MINUTE)
            fittingGaps.firstNotNullOfOrNull { (start, end) ->
                val placed = maxOf(start, deferredEarliest)
                if (placed + duration <= end && !sleepSchedule.intersects(placed, duration)) placed else null
            }?.let { return it }
        }

        if (peakEnergyHour != null) {
            val peakStart = peakEnergyHour * 60
            val peakEnd = peakStart + 60
            // Within each gap, slide as close to the peak as the gap allows, then keep the placement
            // that overlaps the peak window the most. Fall back to first-fit when none can reach it.
            val biased = fittingGaps
                .mapNotNull { (start, end) ->
                    val placed = peakStart.coerceIn(start, end - duration)
                    val overlap = minOf(placed + duration, peakEnd) - maxOf(placed, peakStart)
                    if (overlap > 0 && !sleepSchedule.intersects(placed, duration)) placed to overlap else null
                }
                .maxByOrNull { it.second }
                ?.first
            if (biased != null) return biased
        }
        return fittingGaps.first().first
    }

    /** The most recently logged night up to [currentDate] — dated either the wake day or the bed day. */
    private suspend fun mostRecentNight(currentDate: LocalDate) =
        sleepTrackRepository.getForDateRange(currentDate.minusDays(1), currentDate)
            .maxByOrNull { it.date }

    private suspend fun peakEnergyHour(date: LocalDate): Int? {
        val checkIns = moodEnergyRepository.getForDateRange(date.minusDays(ENERGY_HISTORY_DAYS), date)
        if (checkIns.size < MIN_ENERGY_SAMPLES) return null
        return checkIns
            .groupBy { it.recordedAt.hour }
            .maxByOrNull { (_, entries) -> entries.map { it.energyScore }.average() }
            ?.key
    }

    private fun snapUpToQuarterHour(minute: Int): Int {
        val snapped = ((minute + 14) / 15) * 15
        return snapped.coerceAtMost(1439)
    }

    private companion object {
        const val ENERGY_HISTORY_DAYS = 13L
        const val MIN_ENERGY_SAMPLES = 3
        // Demanding work avoids the early-morning grogginess window after a depleted night.
        const val DEPLETED_DEMANDING_FLOOR_MINUTE = 11 * 60
    }
}
