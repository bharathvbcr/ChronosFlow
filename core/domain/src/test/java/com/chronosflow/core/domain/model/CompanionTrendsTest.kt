package com.chronosflow.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class CompanionTrendsTest {
    private val today = LocalDate.of(2026, 6, 11)

    @Test
    fun `deriveGoalProgress sums manual and derived counts capped at target`() {
        val goal = Goal(
            id = "g1",
            title = "Read 10 books",
            description = null,
            category = "Personal",
            targetValue = 10,
            startDate = today,
            targetDate = null,
            progressValue = 3,
            isCompleted = false
        )
        val derived = GoalDerivedProgress(completedTaskCount = 5, habitCompletionCount = 4)

        // 3 + 5 + 4 = 12, capped at target 10.
        assertEquals(10, deriveGoalProgress(goal, derived))
    }

    @Test
    fun `deriveGoalProgress is uncapped when target is non-positive`() {
        val goal = Goal(
            id = "g2",
            title = "Open-ended",
            description = null,
            category = "Personal",
            targetValue = 0,
            startDate = today,
            targetDate = null,
            progressValue = 2,
            isCompleted = false
        )
        assertEquals(5, deriveGoalProgress(goal, GoalDerivedProgress(completedTaskCount = 3)))
    }

    @Test
    fun `deriveMoodEnergyTrends buckets by day and local hour`() {
        val checkIns = listOf(
            checkIn(date = today, hour = 9, mood = 4, energy = 5),
            checkIn(date = today, hour = 9, mood = 2, energy = 3),
            checkIn(date = today.minusDays(1), hour = 21, mood = 3, energy = 1)
        )

        val trends = deriveMoodEnergyTrends(checkIns)

        assertEquals(2, trends.dailyAverages.size)
        val todayAvg = trends.dailyAverages.first { it.date == today }
        assertEquals(3f, todayAvg.avgMood, 0.001f)
        assertEquals(4f, todayAvg.avgEnergy, 0.001f)
        assertEquals(2, todayAvg.sampleCount)
        // Hour 9 has highest energy, hour 21 the lowest.
        assertEquals(9, trends.peakEnergyHour)
    }

    @Test
    fun `deriveHabitCompletionTrend emits one entry per window day`() {
        val events = listOf(
            habitEvent(date = today, type = HabitEventType.COMPLETED),
            habitEvent(date = today, type = HabitEventType.MISSED),
            habitEvent(date = today.minusDays(2), type = HabitEventType.COMPLETED)
        )

        val trend = deriveHabitCompletionTrend(events, windowDays = 3, today = today)

        assertEquals(3, trend.size)
        assertEquals(today.minusDays(2), trend.first().date)
        assertEquals(1, trend.first().completedCount)
        assertEquals(1, trend.last().completedCount)
        assertEquals(1, trend.last().missedCount)
    }

    @Test
    fun `deriveMedicationAdherenceTrend counts taken and missed per day`() {
        val events = listOf(
            doseEvent(date = today, type = MedicationDoseEventType.TAKEN),
            doseEvent(date = today, type = MedicationDoseEventType.MISSED),
            doseEvent(date = today.minusDays(1), type = MedicationDoseEventType.TAKEN)
        )

        val trend = deriveMedicationAdherenceTrend(events, windowDays = 2, today = today)

        assertEquals(2, trend.size)
        assertEquals(1, trend.first().takenCount)
        assertEquals(1, trend.last().takenCount)
        assertEquals(1, trend.last().missedCount)
    }

    @Test
    fun `deriveSleepTrends emits one night per window day with wrap-around duration`() {
        val tracks = listOf(
            // Slept 23:00 (1380) -> 07:00 (420), wraps midnight = 8h.
            sleepTrack(date = today, quality = 4, start = 1380, end = 420),
            // Two days back, quality only, no window.
            sleepTrack(date = today.minusDays(2), quality = 3, start = null, end = null)
        )

        val trends = deriveSleepTrends(tracks, windowDays = 3, today = today)

        assertEquals(3, trends.nights.size)
        assertEquals(today.minusDays(2), trends.nights.first().date)
        // Day -1 is unlogged.
        assertEquals(null, trends.nights[1].quality)
        assertEquals(2, trends.loggedNights.size)
        val lastNight = trends.nights.last()
        assertEquals(4, lastNight.quality)
        assertEquals(8 * 60, lastNight.durationMinutes)
        assertEquals(8 * 60, trends.averageDurationMinutes)
        assertEquals(3.5f, trends.averageQuality!!, 0.001f)
    }

    private fun sleepTrack(
        date: LocalDate,
        quality: Int,
        start: Int?,
        end: Int?
    ) = SleepTrack(
        id = "s-$date",
        date = date,
        plannedStartMinute = null,
        plannedEndMinute = null,
        actualStartMinute = start,
        actualEndMinute = end,
        sleepQuality = quality,
        windDownNotes = null,
        interruptedCount = 0
    )

    private fun checkIn(date: LocalDate, hour: Int, mood: Int, energy: Int) = MoodEnergyCheckIn(
        id = "c-$date-$hour-$mood",
        blockId = null,
        moodScore = mood,
        stressScore = 2,
        energyScore = energy,
        focusScore = 3,
        notes = null,
        recordedAt = LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)),
        checkInDate = date
    )

    private fun habitEvent(date: LocalDate, type: HabitEventType) = HabitEvent(
        id = "h-$date-$type",
        habitId = "habit-1",
        type = type,
        eventDate = date,
        recordedAt = Instant.parse("2026-06-11T00:00:00Z"),
        reason = null,
        startMinuteOfDay = null,
        endMinuteOfDay = null
    )

    private fun doseEvent(date: LocalDate, type: MedicationDoseEventType) = MedicationDoseEvent(
        id = "d-$date-$type",
        medicationPlanId = "plan-1",
        type = type,
        eventDate = date,
        recordedAt = Instant.parse("2026-06-11T00:00:00Z"),
        scheduledMinuteOfDay = null,
        reason = null,
        doseAmount = null
    )
}
