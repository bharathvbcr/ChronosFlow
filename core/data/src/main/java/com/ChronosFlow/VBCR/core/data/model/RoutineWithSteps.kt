package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class RoutineWithSteps(
    @Embedded val routine: RoutineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val steps: List<RoutineStepEntity>
)
