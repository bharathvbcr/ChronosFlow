package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.RoutineDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.Routine
import com.ChronosFlow.VBCR.core.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class RoutineRepositoryImpl @Inject constructor(
    private val routineDao: RoutineDao
) : RoutineRepository {
    override fun observeRoutines(): Flow<List<Routine>> =
        routineDao.observeRoutinesWithSteps().map { list -> list.map { it.toDomain() } }

    override suspend fun getRoutineById(id: String): Routine? =
        routineDao.getRoutineWithSteps(id)?.toDomain()

    override suspend fun saveRoutine(routine: Routine) {
        val now = Instant.now()
        val existing = routineDao.getRoutineWithSteps(routine.id)
        val createdAt = existing?.routine?.createdAt ?: now
        val stepEntities = routine.steps.mapIndexed { index, step -> step.toEntity(routine.id, index) }
        routineDao.upsertRoutineWithSteps(
            routine = routine.toEntity(createdAt = createdAt, updatedAt = now),
            steps = stepEntities
        )
    }

    override suspend fun deleteRoutine(routine: Routine) {
        val now = Instant.now()
        routineDao.deleteRoutine(routine.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun markCompleted(routineId: String, date: LocalDate) {
        routineDao.markCompleted(routineId, date, Instant.now())
    }
}
