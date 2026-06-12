package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.MedicationDoseEvent
import com.chronosflow.core.domain.model.MedicationSafetyProfile
import com.chronosflow.core.domain.model.MedicationSchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MedicationRepository {
    fun observeMedicationPlans(): Flow<List<MedicationPlan>>
    suspend fun getMedicationPlanById(id: String): MedicationPlan?
    suspend fun saveMedicationPlan(plan: MedicationPlan)
    suspend fun saveMedicationSchedule(schedule: MedicationSchedule)
    suspend fun saveMedicationSafetyProfile(profile: MedicationSafetyProfile)
    suspend fun addMedicationDoseEvent(event: MedicationDoseEvent)
    suspend fun getDoseEventsBetween(start: LocalDate, end: LocalDate): List<MedicationDoseEvent>
    suspend fun deleteMedicationPlan(plan: MedicationPlan)
}
