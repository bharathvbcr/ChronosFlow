package com.chronosflow.core.data.repository

import com.chronosflow.core.data.dao.GoalDao
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.domain.model.Goal
import com.chronosflow.core.domain.model.GoalDerivedProgress
import com.chronosflow.core.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao
) : GoalRepository {
    override fun observeGoals(): Flow<List<Goal>> =
        goalDao.observeGoals().map { list -> list.map { it.toDomain() } }

    override suspend fun getGoalById(id: String): Goal? = goalDao.getGoalById(id)?.toDomain()

    override suspend fun saveGoal(goal: Goal) = goalDao.insertGoal(goal.toEntity())

    override suspend fun deleteGoal(goal: Goal) = goalDao.deleteGoal(goal.toEntity())

    override fun observeDerivedProgress(goalId: String): Flow<GoalDerivedProgress> =
        combine(
            goalDao.observeCompletedTaskCount(goalId),
            goalDao.observeHabitCompletionCount(goalId)
        ) { tasks, habits ->
            GoalDerivedProgress(completedTaskCount = tasks, habitCompletionCount = habits)
        }
}
