package com.chronosflow.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chronosflow.core.data.model.MedicationDoseEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDoseEventDao {
    @Query("SELECT * FROM medication_dose_events ORDER BY recordedAt DESC")
    fun observeAllEvents(): Flow<List<MedicationDoseEventEntity>>

    @Query("SELECT * FROM medication_dose_events WHERE medicationPlanId = :medicationPlanId ORDER BY recordedAt DESC")
    suspend fun getEventsForPlan(medicationPlanId: String): List<MedicationDoseEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: MedicationDoseEventEntity)
}
