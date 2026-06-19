package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.Routine
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RoutineRepository {
    fun observeRoutines(): Flow<List<Routine>>
    suspend fun getRoutineById(id: String): Routine?

    /** Upserts the routine and replaces its steps. */
    suspend fun saveRoutine(routine: Routine)
    suspend fun deleteRoutine(routine: Routine)
    suspend fun markCompleted(routineId: String, date: LocalDate)
}
