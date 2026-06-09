package com.chronosflow.core.domain.repository

interface PlannerPreferencesRepository {
    fun getRecentHabitTemplateIds(): List<String>
    fun saveRecentHabitTemplateIds(ids: List<String>)
    fun getRecentMedicationTemplateIds(): List<String>
    fun saveRecentMedicationTemplateIds(ids: List<String>)
}
