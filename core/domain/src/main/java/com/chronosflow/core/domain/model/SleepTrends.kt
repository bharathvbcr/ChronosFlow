package com.chronosflow.core.domain.model

import java.time.LocalDate
import kotlin.math.roundToInt

/** One night positioned on a calendar day within the sleep-trend window. */
data class SleepTrendNight(
    val date: LocalDate,
    val quality: Int?,
    val durationMinutes: Int?
) {
    val isLogged: Boolean get() = quality != null || durationMinutes != null
}

/** A continuous run of nightly sleep summaries, one per day across the window. */
data class SleepTrends(
    val nights: List<SleepTrendNight> = emptyList()
) {
    val loggedNights: List<SleepTrendNight> get() = nights.filter { it.isLogged }

    val isEmpty: Boolean get() = loggedNights.isEmpty()

    /** Average sleep quality across logged nights, or null when nothing is logged. */
    val averageQuality: Float?
        get() = loggedNights.mapNotNull { it.quality }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()

    /** Average sleep duration in minutes across nights with a known window, or null. */
    val averageDurationMinutes: Int?
        get() = loggedNights.mapNotNull { it.durationMinutes }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt()
}

/**
 * Buckets [tracks] into one [SleepTrendNight] per day across the trailing [windowDays] ending on
 * [today]. Days without a logged night appear with null quality/duration so the series stays aligned.
 */
fun deriveSleepTrends(
    tracks: List<SleepTrack>,
    windowDays: Int = 14,
    today: LocalDate = LocalDate.now()
): SleepTrends {
    if (windowDays <= 0) return SleepTrends()
    val start = today.minusDays((windowDays - 1).toLong())
    val byDate = tracks.filter { it.date in start..today }.associateBy { it.date }
    val nights = (0 until windowDays).map { offset ->
        val date = start.plusDays(offset.toLong())
        val track = byDate[date]
        SleepTrendNight(
            date = date,
            quality = track?.sleepQuality?.takeIf { it > 0 },
            durationMinutes = track?.let(::sleepDurationMinutes)
        )
    }
    return SleepTrends(nights)
}

/**
 * Minutes asleep between the actual sleep and wake minute-of-day, wrapping past midnight. Returns
 * null when either endpoint is unset.
 */
fun sleepDurationMinutes(track: SleepTrack): Int? {
    val start = track.actualStartMinute ?: return null
    val end = track.actualEndMinute ?: return null
    val raw = end - start
    return if (raw >= 0) raw else raw + 24 * 60
}
