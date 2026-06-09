package com.chronosflow.core.domain.model

import java.time.Instant
import java.time.ZoneId

data class SleepSchedule(
    val enabled: Boolean,
    val startMinute: Int = DEFAULT_START_MINUTE,
    val endMinute: Int = DEFAULT_END_MINUTE
) {
    private val normalizedStartMinute = normalizeMinute(startMinute)
    private val normalizedEndMinute = normalizeMinute(endMinute)

    val isActive: Boolean
        get() = enabled && normalizedStartMinute != normalizedEndMinute

    val isOvernight: Boolean
        get() = isActive && normalizedStartMinute > normalizedEndMinute

    fun contains(minuteOfDay: Int): Boolean {
        if (!isActive) return false
        val normalizedMinute = normalizeMinute(minuteOfDay)
        return if (isOvernight) {
            normalizedMinute >= normalizedStartMinute || normalizedMinute < normalizedEndMinute
        } else {
            normalizedMinute in normalizedStartMinute until normalizedEndMinute
        }
    }

    fun intersects(startMinute: Int, durationMinutes: Int): Boolean {
        if (!isActive) return false
        if (durationMinutes <= 0) return false
        if (durationMinutes >= MINUTES_PER_DAY) return true

        return eventSegments(startMinute, durationMinutes).any { event ->
            sleepSegments().any { sleep ->
                event.start < sleep.end && sleep.start < event.end
            }
        }
    }

    fun deferInstant(instant: Instant, zoneId: ZoneId): Instant {
        if (!isActive) return instant

        val localDateTime = instant.atZone(zoneId)
        val minuteOfDay = localDateTime.hour * 60 + localDateTime.minute
        if (!contains(minuteOfDay)) return instant

        val wakeDate = if (isOvernight && minuteOfDay >= normalizedStartMinute) {
            localDateTime.toLocalDate().plusDays(1)
        } else {
            localDateTime.toLocalDate()
        }

        return wakeDate
            .atStartOfDay(zoneId)
            .plusMinutes(normalizedEndMinute.toLong())
            .toInstant()
    }

    private fun sleepSegments(): List<MinuteSegment> {
        return if (isOvernight) {
            listOf(
                MinuteSegment(normalizedStartMinute, MINUTES_PER_DAY),
                MinuteSegment(0, normalizedEndMinute)
            )
        } else {
            listOf(MinuteSegment(normalizedStartMinute, normalizedEndMinute))
        }
    }

    private fun eventSegments(startMinute: Int, durationMinutes: Int): List<MinuteSegment> {
        val normalizedStart = normalizeMinute(startMinute)
        val endMinute = normalizedStart + durationMinutes
        return if (endMinute <= MINUTES_PER_DAY) {
            listOf(MinuteSegment(normalizedStart, endMinute))
        } else {
            listOf(
                MinuteSegment(normalizedStart, MINUTES_PER_DAY),
                MinuteSegment(0, endMinute - MINUTES_PER_DAY)
            )
        }
    }

    private data class MinuteSegment(
        val start: Int,
        val end: Int
    )

    companion object {
        const val PREFERENCES_NAME = "daydial_ui_settings"
        const val KEY_ENABLED = "sleep_schedule_enabled"
        const val KEY_START_MINUTE = "sleep_schedule_start_minute"
        const val KEY_END_MINUTE = "sleep_schedule_end_minute"
        const val DEFAULT_START_MINUTE = 21 * 60
        const val DEFAULT_END_MINUTE = 7 * 60
        private const val MINUTES_PER_DAY = 24 * 60

        fun default(): SleepSchedule = SleepSchedule(
            enabled = false,
            startMinute = DEFAULT_START_MINUTE,
            endMinute = DEFAULT_END_MINUTE
        )

        private fun normalizeMinute(minute: Int): Int = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }
}
