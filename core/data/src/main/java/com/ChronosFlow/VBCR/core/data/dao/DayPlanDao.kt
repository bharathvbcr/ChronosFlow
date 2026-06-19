package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.DayPlanEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DayPlanDao {
    @Query("SELECT * FROM day_plans WHERE date = :date")
    fun getDayPlanByDate(date: LocalDate): Flow<DayPlanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayPlan(dayPlan: DayPlanEntity)

    @Query("DELETE FROM day_plans WHERE date = :date")
    suspend fun deleteDayPlan(date: LocalDate)
}
