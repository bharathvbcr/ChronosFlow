package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.GoalEntity
import com.ChronosFlow.VBCR.core.data.model.HabitEntity
import com.ChronosFlow.VBCR.core.data.model.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY isCompleted ASC, targetDate IS NULL, targetDate ASC, title ASC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalById(id: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Delete
    suspend fun deleteGoal(goal: GoalEntity)

    @Query("SELECT COUNT(*) FROM tasks WHERE goalId = :goalId AND isCompleted = 1")
    fun observeCompletedTaskCount(goalId: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM habit_events WHERE eventType = 'COMPLETED' " +
            "AND habitId IN (SELECT id FROM habits WHERE goalId = :goalId)"
    )
    fun observeHabitCompletionCount(goalId: String): Flow<Int>

    @Query(
        "SELECT * FROM tasks WHERE goalId = :goalId " +
            "ORDER BY isCompleted ASC, priority DESC, targetDate IS NULL, targetDate ASC, title ASC"
    )
    fun observeTasksForGoal(goalId: String): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM habits WHERE goalId = :goalId " +
            "ORDER BY isActive DESC, streakCount DESC, title ASC"
    )
    fun observeHabitsForGoal(goalId: String): Flow<List<HabitEntity>>
}
