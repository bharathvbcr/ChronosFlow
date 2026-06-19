package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.repository.RoutineRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Instantiates each step of a routine as a [TimeBlock] on [date], starting at [startMinuteOfDay]
 * plus the step's offset. Created blocks carry [TimeBlock.routineId] so the routine's completion can
 * later be derived from them.
 *
 * @return the number of blocks created.
 */
class ApplyRoutineToDateUseCase @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val timeBlockRepository: TimeBlockRepository
) {
    suspend operator fun invoke(
        routineId: String,
        date: LocalDate,
        startMinuteOfDay: Int
    ): Int {
        val routine = routineRepository.getRoutineById(routineId) ?: return 0
        val timezone = ZoneId.systemDefault().id
        var created = 0
        routine.steps.forEach { step ->
            val now = Instant.now()
            // A step whose absolute minute exceeds the day rolls onto the following date rather than
            // folding back to the start of the same day. floorDiv/floorMod stay correct for any anchor.
            val total = startMinuteOfDay + step.offsetMinute
            val dayShift = Math.floorDiv(total, 1440)
            val start = Math.floorMod(total, 1440)
            timeBlockRepository.saveTimeBlock(
                TimeBlock(
                    id = UUID.randomUUID().toString(),
                    date = date.plusDays(dayShift.toLong()),
                    title = step.title,
                    category = step.category,
                    startMinuteOfDay = start,
                    durationMinutes = step.durationMinutes.coerceIn(1, 1440),
                    timezone = timezone,
                    provenance = BlockProvenance.USER_CREATED,
                    flexibility = BlockFlexibility.MOVABLE,
                    energyLevel = EnergyIntensity.fromLevel(step.energyLevel),
                    source = "routine",
                    taskId = null,
                    calendarEventId = null,
                    medicationPlanId = null,
                    habitId = null,
                    isLocked = false,
                    isProtected = false,
                    recurrenceRuleId = null,
                    actualStartMinuteOfDay = null,
                    actualEndMinuteOfDay = null,
                    createdAt = now,
                    updatedAt = now,
                    routineId = routineId
                )
            )
            created += 1
        }
        return created
    }
}
