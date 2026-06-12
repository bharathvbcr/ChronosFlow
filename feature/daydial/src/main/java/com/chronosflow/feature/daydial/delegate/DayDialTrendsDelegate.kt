package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.domain.model.HabitDailyCompletion
import com.chronosflow.core.domain.model.MedicationDailyAdherence
import com.chronosflow.core.domain.model.MoodEnergyTrends
import com.chronosflow.core.domain.usecase.GetHabitCompletionTrendUseCase
import com.chronosflow.core.domain.usecase.GetMedicationAdherenceTrendUseCase
import com.chronosflow.core.domain.usecase.ObserveMoodEnergyTrendsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class CompanionTrendSections(
    val moodTrends: MoodEnergyTrends = MoodEnergyTrends(),
    val habitTrend: List<HabitDailyCompletion> = emptyList(),
    val medicationTrend: List<MedicationDailyAdherence> = emptyList()
) {
    val isEmpty: Boolean
        get() = moodTrends.isEmpty && habitTrend.isEmpty() && medicationTrend.isEmpty()
}

/** Loads the companion trend snapshot shown by the Insights tab's trend cards. */
class DayDialTrendsDelegate @Inject constructor(
    private val observeMoodEnergyTrendsUseCase: ObserveMoodEnergyTrendsUseCase,
    private val getHabitCompletionTrendUseCase: GetHabitCompletionTrendUseCase,
    private val getMedicationAdherenceTrendUseCase: GetMedicationAdherenceTrendUseCase
) {
    suspend fun loadTrends(windowDays: Int): CompanionTrendSections = CompanionTrendSections(
        moodTrends = observeMoodEnergyTrendsUseCase(windowDays).first(),
        habitTrend = getHabitCompletionTrendUseCase(windowDays),
        medicationTrend = getMedicationAdherenceTrendUseCase(windowDays)
    )
}
