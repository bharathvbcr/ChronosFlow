package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.MedicationPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medication_plans ORDER BY isActive DESC, reminderMinuteOfDay ASC, name ASC")
    fun observeMedicationPlans(): Flow<List<MedicationPlanEntity>>

    @Query("SELECT * FROM medication_plans WHERE id = :id")
    suspend fun getMedicationPlanById(id: String): MedicationPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicationPlan(plan: MedicationPlanEntity)

    @Delete
    suspend fun deleteMedicationPlan(plan: MedicationPlanEntity)
}
