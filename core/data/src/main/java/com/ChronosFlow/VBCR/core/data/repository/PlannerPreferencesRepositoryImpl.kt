package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import com.ChronosFlow.VBCR.core.domain.repository.PlannerPreferencesRepository
import javax.inject.Inject

class PlannerPreferencesRepositoryImpl @Inject constructor(
    private val preferencesDataSource: ChronosPreferencesDataSource
) : PlannerPreferencesRepository {

    override fun getRecentHabitTemplateIds(): List<String> {
        return parseIds(preferencesDataSource.getString(HABIT_RECENT_TEMPLATE_IDS_KEY))
    }

    override fun saveRecentHabitTemplateIds(ids: List<String>) {
        preferencesDataSource.putString(HABIT_RECENT_TEMPLATE_IDS_KEY, encodeIds(ids))
    }

    override fun getRecentMedicationTemplateIds(): List<String> {
        return parseIds(preferencesDataSource.getString(MEDICATION_RECENT_TEMPLATE_IDS_KEY))
    }

    override fun saveRecentMedicationTemplateIds(ids: List<String>) {
        preferencesDataSource.putString(MEDICATION_RECENT_TEMPLATE_IDS_KEY, encodeIds(ids))
    }

    private fun parseIds(raw: String): List<String> = raw
        .split('|')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .take(MAX_RECENT_IDS)

    private fun encodeIds(ids: List<String>): String = ids
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .take(MAX_RECENT_IDS)
        .joinToString("|")

    private companion object {
        const val HABIT_RECENT_TEMPLATE_IDS_KEY = "habit_recent_template_ids"
        const val MEDICATION_RECENT_TEMPLATE_IDS_KEY = "medication_recent_template_ids"
        const val MAX_RECENT_IDS = 5
    }
}
