package com.chronosflow.core.data.repository

import app.cash.turbine.test
import com.chronosflow.core.data.dao.HabitDao
import com.chronosflow.core.data.dao.HabitEventDao
import com.chronosflow.core.data.dao.HabitScheduleDao
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.data.model.HabitEntity
import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.HabitEvent
import com.chronosflow.core.domain.model.HabitEventType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HabitRepositoryImplTest {
    private val habitDao: HabitDao = mockk()
    private val habitScheduleDao: HabitScheduleDao = mockk()
    private val habitEventDao: HabitEventDao = mockk()
    private val repository = HabitRepositoryImpl(habitDao, habitScheduleDao, habitEventDao)

    @Test
    fun `observe habits maps each domain habit with legacy default schedule`() = runTest {
        every { habitDao.observeHabits() } returns flowOf(listOf(habitEntity()))
        every { habitScheduleDao.observeAllSchedules() } returns flowOf(emptyList())
        every { habitEventDao.observeAllEvents() } returns flowOf(emptyList())

        repository.observeHabits().test {
            val habits = awaitItem()
            assertEquals(1, habits.size)
            assertEquals("habit-1", habits[0].id)
            assertEquals("schedule-habit-1", habits[0].schedule?.id)
            awaitComplete()
        }
    }

    @Test
    fun `get habit by id falls back to legacy schedule and analytics defaults`() = runTest {
        coEvery { habitDao.getHabitById("habit-1") } returns habitEntity(streak = 4)
        coEvery { habitEventDao.getEventsForHabit("habit-1") } returns emptyList()
        coEvery { habitScheduleDao.getScheduleForHabit("habit-1") } returns null

        val habit = repository.getHabitById("habit-1")
        assertNotNull(habit)
        assertEquals("schedule-habit-1", habit!!.schedule?.id)
        assertEquals(4, habit.analytics.currentStreak)
    }

    @Test
    fun `save and delete habit pass through dao layer`() = runTest {
        coEvery { habitDao.insertHabit(any()) } returns Unit
        coEvery { habitScheduleDao.upsertSchedule(any()) } returns Unit
        coEvery { habitDao.deleteHabit(any()) } returns Unit

        val habit = habitDomainEntity()
        repository.saveHabit(habit)
        repository.deleteHabit(habit)

        coVerify { habitDao.insertHabit(any()) }
        coVerify { habitScheduleDao.upsertSchedule(any()) }
        coVerify { habitDao.deleteHabit(any()) }
    }

    @Test
    fun `observe habit events between maps entities to domain`() = runTest {
        val start = LocalDate.parse("2026-01-01")
        val end = LocalDate.parse("2026-01-07")
        val event = HabitEvent(
            id = "evt-1",
            habitId = "habit-1",
            type = HabitEventType.COMPLETED,
            eventDate = LocalDate.parse("2026-01-03"),
            recordedAt = Instant.parse("2026-01-03T12:00:00Z"),
            reason = null,
            startMinuteOfDay = 480,
            endMinuteOfDay = 540
        )
        every { habitEventDao.observeEventsBetween(start, end) } returns flowOf(listOf(event.toEntity()))

        repository.observeHabitEventsBetween(start, end).test {
            val events = awaitItem()
            assertEquals(1, events.size)
            assertEquals("evt-1", events[0].id)
            assertEquals(HabitEventType.COMPLETED, events[0].type)
            awaitComplete()
        }
    }

    @Test
    fun `add habit event inserts event entity`() = runTest {
        coEvery { habitEventDao.insertEvent(any()) } returns Unit

        repository.addHabitEvent(
            HabitEvent(
                id = "event-1",
                habitId = "habit-1",
                type = HabitEventType.COMPLETED,
                eventDate = LocalDate.parse("2026-01-03"),
                recordedAt = Instant.parse("2026-01-03T12:00:00Z"),
                reason = null,
                startMinuteOfDay = 480,
                endMinuteOfDay = 540
            )
        )
        coVerify { habitEventDao.insertEvent(any()) }
    }

    private fun habitEntity(streak: Int = 0): HabitEntity = HabitEntity(
        id = "habit-1",
        title = "Stretch",
        cadence = "daily",
        windowStartMinute = 480,
        windowEndMinute = 540,
        difficulty = 1,
        isBundled = false,
        streakCount = streak,
        lastCompletedDate = null,
        isActive = true
    )

    private fun habitDomainEntity(): Habit = Habit(
        id = "habit-1",
        title = "Stretch",
        cadence = "daily",
        windowStartMinute = 480,
        windowEndMinute = 540,
        difficulty = 1,
        isBundled = false,
        streakCount = 2,
        lastCompletedDate = null,
        isActive = true
    )
}
