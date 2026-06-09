package com.chronosflow.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class DomainValueObjectsTest {
    private val today = LocalDate.of(2026, 5, 31)

    @Test
    fun `goal copy preserves immutable structure`() {
        val goal = Goal(
            id = "goal-1",
            title = "Read books",
            description = "Read one chapter",
            category = "Personal",
            targetValue = 10,
            startDate = today,
            targetDate = today.plusDays(30),
            progressValue = 3,
            isCompleted = false
        )
        val updated = goal.copy(progressValue = 8, isCompleted = true)

        assertEquals("goal-1", goal.id)
        assertEquals(3, goal.progressValue)
        assertEquals(8, updated.progressValue)
        assertEquals(true, updated.isCompleted)
        assertNotEquals(goal, updated)
    }

    @Test
    fun `calendar event stores all fields`() {
        val start = Instant.parse("2026-05-31T08:00:00Z")
        val end = Instant.parse("2026-05-31T09:00:00Z")
        val event = CalendarEvent(
            id = 7,
            title = "Team standup",
            description = "Daily sync",
            startAt = start,
            endAt = end,
            timezone = "UTC",
            location = null,
            externalId = "ext-1",
            isAllDay = false
        )

        assertEquals(7, event.id)
        assertEquals("Team standup", event.title)
        assertEquals(start, event.startAt)
        assertEquals(end, event.endAt)
    }

    @Test
    fun `routine keeps identity and activity flag`() {
        val routine = Routine(
            id = "r1",
            title = "Work sprint",
            blockIds = listOf("b1", "b2"),
            isActive = true,
            lastCompletedDate = today
        )

        val restarted = routine.copy(isActive = false, lastCompletedDate = null)
        assertEquals("r1", routine.id)
        assertEquals(true, routine.isActive)
        assertEquals(false, restarted.isActive)
        assertEquals(null, restarted.lastCompletedDate)
    }

    @Test
    fun `focus session stores time and completion state`() {
        val session = FocusSession(
            id = "s1",
            blockId = "b1",
            date = today,
            plannedDurationMinutes = 1200,
            actualDurationMinutes = 1080,
            interruptions = 2,
            startedAt = Instant.parse("2026-05-31T10:00:00Z"),
            completedAt = Instant.parse("2026-05-31T10:18:00Z"),
            notes = "Deep focus",
            isCompleted = true
        )

        assertEquals("b1", session.blockId)
        assertEquals(1080, session.actualDurationMinutes)
        assertEquals(2, session.interruptions)
        assertEquals(true, session.isCompleted)
    }

    @Test
    fun `mood energy check in retains ratings and timestamps`() {
        val checkIn = MoodEnergyCheckIn(
            id = "checkin-1",
            blockId = "b2",
            moodScore = 4,
            stressScore = 2,
            energyScore = 5,
            focusScore = 4,
            notes = "steady",
            recordedAt = LocalDateTime.parse("2026-05-31T11:00:00"),
            checkInDate = today
        )

        assertEquals(4, checkIn.moodScore)
        assertEquals(2, checkIn.stressScore)
        assertEquals(5, checkIn.energyScore)
        assertEquals("steady", checkIn.notes)
    }

    @Test
    fun `sleep track captures planned and actual minutes`() {
        val track = SleepTrack(
            id = "sleep-1",
            date = today,
            plannedStartMinute = 1380,
            plannedEndMinute = 420,
            actualStartMinute = 1390,
            actualEndMinute = 410,
            sleepQuality = 4,
            windDownNotes = "no caffeine",
            interruptedCount = 1
        )

        assertEquals(4, track.sleepQuality)
        assertEquals(1380, track.plannedStartMinute)
        assertEquals(420, track.plannedEndMinute)
        assertEquals(1, track.interruptedCount)
    }
}
