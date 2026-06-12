package com.chronosflow.core.domain.model

import java.time.LocalDate

/** A reusable bundle of block definitions that can be applied to any date. */
data class Routine(
    val id: String,
    val title: String,
    val blockIds: List<String> = emptyList(),
    val isActive: Boolean,
    val lastCompletedDate: LocalDate?,
    val steps: List<RoutineStep> = emptyList()
)

/** One step of a routine, instantiated as a TimeBlock at [offsetMinute] past the chosen start. */
data class RoutineStep(
    val id: String,
    val title: String,
    val category: String = DEFAULT_CATEGORY,
    val offsetMinute: Int,
    val durationMinutes: Int,
    val energyLevel: Int = DEFAULT_ENERGY_LEVEL
) {
    companion object {
        const val DEFAULT_CATEGORY = "ROUTINE"
        const val DEFAULT_ENERGY_LEVEL = 2
    }
}
