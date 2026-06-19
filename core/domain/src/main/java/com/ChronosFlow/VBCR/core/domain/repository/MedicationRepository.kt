package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationSafetyProfile
import com.ChronosFlow.VBCR.core.domain.model.MedicationSchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MedicationRepository {
    fun observeMedicationPlans(): Flow<List<MedicationPlan>>
    suspend fun getMedicationPlanById(id: String): MedicationPlan?
    suspend fun saveMedicationPlan(plan: MedicationPlan)
    suspend fun saveMedicationSchedule(schedule: MedicationSchedule)
    suspend fun saveMedicationSafetyProfile(profile: MedicationSafetyProfile)
    suspend fun addMedicationDoseEvent(event: MedicationDoseEvent)
    fun observeDoseEventsBetween(start: LocalDate, end: LocalDate): Flow<List<MedicationDoseEvent>>
    suspend fun deleteMedicationPlan(plan: MedicationPlan)
}
