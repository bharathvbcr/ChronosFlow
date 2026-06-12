package com.chronosflow.core.data.mapper

import com.chronosflow.core.data.model.RoutineEntity
import com.chronosflow.core.data.model.RoutineStepEntity
import com.chronosflow.core.data.model.RoutineWithSteps
import com.chronosflow.core.domain.model.Routine
import com.chronosflow.core.domain.model.RoutineStep
import java.time.Instant

fun RoutineWithSteps.toDomain(): Routine = Routine(
    id = routine.id,
    title = routine.title,
    isActive = routine.isActive,
    lastCompletedDate = routine.lastCompletedDate,
    steps = steps
        .sortedBy { it.sortOrder }
        .map { it.toDomain() }
)

fun RoutineStepEntity.toDomain(): RoutineStep = RoutineStep(
    id = id,
    title = title,
    category = category,
    offsetMinute = offsetMinute,
    durationMinutes = durationMinutes,
    energyLevel = energyLevel
)

fun Routine.toEntity(createdAt: Instant, updatedAt: Instant): RoutineEntity = RoutineEntity(
    id = id,
    title = title,
    isActive = isActive,
    lastCompletedDate = lastCompletedDate,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RoutineStep.toEntity(routineId: String, sortOrder: Int): RoutineStepEntity = RoutineStepEntity(
    id = id,
    routineId = routineId,
    title = title,
    category = category,
    offsetMinute = offsetMinute,
    durationMinutes = durationMinutes,
    energyLevel = energyLevel,
    sortOrder = sortOrder
)
