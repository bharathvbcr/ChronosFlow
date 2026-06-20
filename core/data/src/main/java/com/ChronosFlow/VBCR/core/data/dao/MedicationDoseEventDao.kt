package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ChronosFlow.VBCR.core.data.model.MedicationDoseEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDoseEventDao {
    @Query("SELECT * FROM medication_dose_events WHERE recordedAt >= :cutoff ORDER BY recordedAt DESC")
    fun observeAllEvents(cutoff: Long): Flow<List<MedicationDoseEventEntity>>

    @Query("SELECT * FROM medication_dose_events WHERE medicationPlanId = :medicationPlanId ORDER BY recordedAt DESC")
    suspend fun getEventsForPlan(medicationPlanId: String): List<MedicationDoseEventEntity>

    @Query("SELECT * FROM medication_dose_events WHERE eventDate BETWEEN :start AND :end ORDER BY eventDate ASC")
    fun observeEventsBetween(start: java.time.LocalDate, end: java.time.LocalDate): Flow<List<MedicationDoseEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: MedicationDoseEventEntity)
}
