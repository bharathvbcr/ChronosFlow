package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.GoalDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.Goal
import com.ChronosFlow.VBCR.core.domain.model.GoalDerivedProgress
import com.ChronosFlow.VBCR.core.domain.model.GoalLinkedHabit
import com.ChronosFlow.VBCR.core.domain.model.GoalLinkedTask
import com.ChronosFlow.VBCR.core.domain.model.GoalLinkedWork
import com.ChronosFlow.VBCR.core.domain.repository.GoalRepository
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

    override fun observeLinkedWork(goalId: String): Flow<GoalLinkedWork> =
        combine(
            goalDao.observeTasksForGoal(goalId),
            goalDao.observeHabitsForGoal(goalId)
        ) { tasks, habits ->
            GoalLinkedWork(
                tasks = tasks.map { task ->
                    GoalLinkedTask(
                        id = task.id,
                        title = task.title,
                        isCompleted = task.isCompleted,
                        priority = task.priority,
                        targetDate = task.targetDate
                    )
                },
                habits = habits.map { habit ->
                    GoalLinkedHabit(
                        id = habit.id,
                        title = habit.title,
                        cadence = habit.cadence,
                        streakCount = habit.streakCount,
                        isActive = habit.isActive
                    )
                }
            )
        }
}
