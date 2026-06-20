package com.ChronosFlow.VBCR.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Verifies that the habit-streak counter uses [LocalDate]-based event dates rather than
 * wall-clock timestamps, so a completion recorded at 23:59 still counts toward the streak
 * when the query runs at 00:01 the following day.
 *
 * Prior to the fix, implementations that compared epoch-millisecond timestamps with a
 * 24-hour sliding window would miss completions logged late at night.
 */
class HabitStreakWindowTest {

    /**
     * A completion written at 23:59 local time carries [LocalDate] = today.
     * When [deriveHabitAnalytics] is called with today + 1 day as the reference,
     * today's completion must appear as "yesterday" in the streak walk-back and
     * contribute streak = 1.
     *
     * The old 24-hour timestamp window would reject it because the epoch distance
     * between 23:59 tonight and 00:01 tomorrow is only 2 minutes, but many
     * implementations computed "24h ago" from now and thus excluded it when the
     * next-morning query ran more than 24h after the event's millisecond timestamp.
     *
     * The fix: always compare [LocalDate] values, never epoch offsets.
     */
    @Test
    fun `habitStreak completionAt2359 countsForNextDay`() {
        val today = LocalDate.of(2026, 6, 20)
        val tomorrow = today.plusDays(1)

        // Simulate a completion logged at 23:59 on 'today'.
        val lateNightCompletion = HabitEvent(
            id = "event-late-night",
            habitId = "habit-1",
            type = HabitEventType.COMPLETED,
            // eventDate is the LOCAL date of the completion — this is what the streak
            // algorithm must use, not a derived epoch offset.
            eventDate = today,
            recordedAt = Instant.parse("2026-06-20T21:59:00Z"), // 23:59 UTC+2 / 21:59 UTC
            reason = null,
            startMinuteOfDay = 23 * 60 + 59, // 23:59
            endMinuteOfDay = 23 * 60 + 59
        )

        // Query streak from tomorrow's perspective (00:01).
        // The streakCount should be 1 because yesterday (= today) has a completion.
        val analytics = deriveHabitAnalytics(
            events = listOf(lateNightCompletion),
            today = tomorrow
        )

        assertEquals(
            "A completion at 23:59 must contribute streak=1 when queried at 00:01 the next day",
            1,
            analytics.currentStreak
        )
    }

    /**
     * Verify the positive case: a completion on today itself (from today's perspective)
     * also yields streak = 1.
     */
    @Test
    fun `habitStreak completionOnSameDay streakIsOne`() {
        val today = LocalDate.of(2026, 6, 20)

        val event = HabitEvent(
            id = "event-today",
            habitId = "habit-1",
            type = HabitEventType.COMPLETED,
            eventDate = today,
            recordedAt = Instant.parse("2026-06-20T08:00:00Z"),
            reason = null,
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 8 * 60 + 30
        )

        val analytics = deriveHabitAnalytics(events = listOf(event), today = today)

        assertEquals(1, analytics.currentStreak)
    }

    /**
     * Two consecutive completions (today and yesterday) must yield streak = 2
     * regardless of what time of day they were recorded.
     */
    @Test
    fun `habitStreak twoConsecutiveDays streakIsTwo`() {
        val today = LocalDate.of(2026, 6, 20)
        val yesterday = today.minusDays(1)

        val events = listOf(
            HabitEvent(
                id = "event-today",
                habitId = "habit-1",
                type = HabitEventType.COMPLETED,
                eventDate = today,
                recordedAt = Instant.parse("2026-06-20T23:50:00Z"),
                reason = null,
                startMinuteOfDay = 23 * 60 + 50,
                endMinuteOfDay = 23 * 60 + 55
            ),
            HabitEvent(
                id = "event-yesterday",
                habitId = "habit-1",
                type = HabitEventType.COMPLETED,
                eventDate = yesterday,
                recordedAt = Instant.parse("2026-06-19T07:00:00Z"),
                reason = null,
                startMinuteOfDay = 7 * 60,
                endMinuteOfDay = 7 * 60 + 20
            )
        )

        val analytics = deriveHabitAnalytics(events = events, today = today)

        assertEquals(2, analytics.currentStreak)
    }

    /**
     * A gap in the completion history (today completed, the day before yesterday completed,
     * but yesterday missed) must break the streak at 1.
     */
    @Test
    fun `habitStreak gapBreaksStreak`() {
        val today = LocalDate.of(2026, 6, 20)

        val events = listOf(
            HabitEvent(
                id = "event-today",
                habitId = "habit-1",
                type = HabitEventType.COMPLETED,
                eventDate = today,
                recordedAt = Instant.parse("2026-06-20T09:00:00Z"),
                reason = null,
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 9 * 60 + 15
            ),
            // yesterday is intentionally missing — gap in streak
            HabitEvent(
                id = "event-two-days-ago",
                habitId = "habit-1",
                type = HabitEventType.COMPLETED,
                eventDate = today.minusDays(2),
                recordedAt = Instant.parse("2026-06-18T09:00:00Z"),
                reason = null,
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 9 * 60 + 15
            )
        )

        val analytics = deriveHabitAnalytics(events = events, today = today)

        assertEquals(
            "A gap in daily completions must reset the streak to 1 (only today counts)",
            1,
            analytics.currentStreak
        )
    }

    /**
     * No completions at all must yield streak = 0.
     */
    @Test
    fun `habitStreak noCompletions streakIsZero`() {
        val today = LocalDate.of(2026, 6, 20)

        val analytics = deriveHabitAnalytics(events = emptyList(), today = today)

        assertEquals(0, analytics.currentStreak)
    }
}
