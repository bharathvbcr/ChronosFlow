package com.chronosflow.core.domain.model

import java.time.LocalDate
import java.time.ZoneId

/** Average check-in scores for one calendar day. */
data class MoodEnergyDailyAverage(
    val date: LocalDate,
    val avgMood: Float,
    val avgStress: Float,
    val avgEnergy: Float,
    val avgFocus: Float,
    val sampleCount: Int
)

/** Average check-in scores bucketed by local hour-of-day across the whole window. */
data class MoodEnergyHourAverage(
    val hourOfDay: Int,
    val avgMood: Float,
    val avgStress: Float,
    val avgEnergy: Float,
    val avgFocus: Float,
    val sampleCount: Int
)

data class MoodEnergyTrends(
    val dailyAverages: List<MoodEnergyDailyAverage> = emptyList(),
    val hourOfDayAverages: List<MoodEnergyHourAverage> = emptyList()
) {
    val isEmpty: Boolean get() = dailyAverages.isEmpty() && hourOfDayAverages.isEmpty()

    /** Hour with the highest average energy, if any check-ins exist. */
    val peakEnergyHour: Int?
        get() = hourOfDayAverages.maxByOrNull { it.avgEnergy }?.hourOfDay
}

/**
 * Aggregates raw check-ins into daily and per-hour averages.
 *
 * [recordedAt] is already a local timestamp (the entity converts the stored epoch millis using the
 * device zone), so hour bucketing reads its local hour directly. [zoneId] is accepted for callers
 * that aggregate from other sources and to keep the signature stable.
 */
fun deriveMoodEnergyTrends(
    checkIns: List<MoodEnergyCheckIn>,
    @Suppress("UNUSED_PARAMETER") zoneId: ZoneId = ZoneId.systemDefault()
): MoodEnergyTrends {
    if (checkIns.isEmpty()) return MoodEnergyTrends()

    val dailyAverages = checkIns
        .groupBy { it.checkInDate }
        .map { (date, entries) -> entries.averageInto { count, mood, stress, energy, focus ->
            MoodEnergyDailyAverage(date, mood, stress, energy, focus, count)
        } }
        .sortedBy { it.date }

    val hourOfDayAverages = checkIns
        .groupBy { it.recordedAt.hour }
        .map { (hour, entries) -> entries.averageInto { count, mood, stress, energy, focus ->
            MoodEnergyHourAverage(hour, mood, stress, energy, focus, count)
        } }
        .sortedBy { it.hourOfDay }

    return MoodEnergyTrends(dailyAverages, hourOfDayAverages)
}

private inline fun <T> List<MoodEnergyCheckIn>.averageInto(
    build: (count: Int, mood: Float, stress: Float, energy: Float, focus: Float) -> T
): T {
    val count = size
    return build(
        count,
        sumOf { it.moodScore }.toFloat() / count,
        sumOf { it.stressScore }.toFloat() / count,
        sumOf { it.energyScore }.toFloat() / count,
        sumOf { it.focusScore }.toFloat() / count
    )
}
