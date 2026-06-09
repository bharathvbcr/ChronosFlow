package com.chronosflow.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chronosflow.core.data.model.MedicationSafetyProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationSafetyProfileDao {
    @Query("SELECT * FROM medication_safety_profiles")
    fun observeAllProfiles(): Flow<List<MedicationSafetyProfileEntity>>

    @Query("SELECT * FROM medication_safety_profiles WHERE medicationPlanId = :medicationPlanId")
    suspend fun getProfileForPlan(medicationPlanId: String): MedicationSafetyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: MedicationSafetyProfileEntity)
}
