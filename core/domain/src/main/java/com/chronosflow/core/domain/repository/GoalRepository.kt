package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.Goal
import com.chronosflow.core.domain.model.GoalDerivedProgress
import com.chronosflow.core.domain.model.GoalLinkedWork
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeGoals(): Flow<List<Goal>>
    suspend fun getGoalById(id: String): Goal?
    suspend fun saveGoal(goal: Goal)
    suspend fun deleteGoal(goal: Goal)

    /** Live derived progress for a goal: counts of completed linked tasks and habit completions. */
    fun observeDerivedProgress(goalId: String): Flow<GoalDerivedProgress>

    /** Live list of the tasks and habits linked to a goal, for the goal detail view. */
    fun observeLinkedWork(goalId: String): Flow<GoalLinkedWork>
}
