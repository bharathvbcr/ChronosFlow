package com.chronosflow.core.ai

import com.chronosflow.core.domain.model.HabitDailyCompletion
import com.chronosflow.core.domain.model.MedicationDailyAdherence
import com.chronosflow.core.domain.model.MoodEnergyTrends

/**
 * Recent companion-data trends fed into recommendation prompts so suggestions
 * reflect multi-day patterns instead of only today's numbers. All fields
 * default to empty: callers that cannot supply trends lose nothing.
 */
data class CompanionTrendContext(
    val moodEnergyTrends: MoodEnergyTrends = MoodEnergyTrends(),
    val habitCompletion: List<HabitDailyCompletion> = emptyList(),
    val medicationAdherence: List<MedicationDailyAdherence> = emptyList()
) {
    val isEmpty: Boolean
        get() = moodEnergyTrends.isEmpty && habitCompletion.isEmpty() && medicationAdherence.isEmpty()

    val peakEnergyHour: Int? get() = moodEnergyTrends.peakEnergyHour

    val habitCompletedLastWeek: Int get() = habitCompletion.takeLast(7).sumOf { it.completedCount }

    val habitCompletedPriorWeek: Int get() = habitCompletion.dropLast(7).sumOf { it.completedCount }

    /**
     * Last-week completions minus prior-week completions; null without a full
     * two-week window or a non-zero baseline to compare against.
     */
    val habitWeekOverWeekDelta: Int?
        get() = if (habitCompletion.size < 14 || habitCompletedPriorWeek == 0) {
            null
        } else {
            habitCompletedLastWeek - habitCompletedPriorWeek
        }

    val medicationTakenTotal: Int get() = medicationAdherence.sumOf { it.takenCount }

    val medicationMissedTotal: Int get() = medicationAdherence.sumOf { it.missedCount }
}
