package com.chronosflow.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.chronosflow.core.data.model.RoutineEntity
import com.chronosflow.core.data.model.RoutineStepEntity
import com.chronosflow.core.data.model.RoutineWithSteps
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY isActive DESC, title ASC")
    fun observeRoutinesWithSteps(): Flow<List<RoutineWithSteps>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutineWithSteps(id: String): RoutineWithSteps?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<RoutineStepEntity>)

    @Query("DELETE FROM routine_steps WHERE routineId = :routineId")
    suspend fun deleteStepsForRoutine(routineId: String)

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    @Query("UPDATE routines SET lastCompletedDate = :date, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markCompleted(id: String, date: LocalDate, updatedAt: Instant)

    @Transaction
    suspend fun upsertRoutineWithSteps(routine: RoutineEntity, steps: List<RoutineStepEntity>) {
        insertRoutine(routine)
        deleteStepsForRoutine(routine.id)
        if (steps.isNotEmpty()) insertSteps(steps)
    }
}
