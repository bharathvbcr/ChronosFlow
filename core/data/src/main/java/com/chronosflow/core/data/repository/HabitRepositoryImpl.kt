package com.chronosflow.core.data.repository

import com.chronosflow.core.data.dao.HabitEventDao
import com.chronosflow.core.data.dao.HabitDao
import com.chronosflow.core.data.dao.HabitScheduleDao
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.HabitAnalytics
import com.chronosflow.core.domain.model.HabitEvent
import com.chronosflow.core.domain.model.HabitSchedule
import com.chronosflow.core.domain.model.buildLegacyHabitSchedule
import com.chronosflow.core.domain.model.deriveHabitAnalytics
import com.chronosflow.core.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class HabitRepositoryImpl @Inject constructor(
    private val habitDao: HabitDao,
    private val habitScheduleDao: HabitScheduleDao,
    private val habitEventDao: HabitEventDao
) : HabitRepository {
    override fun observeHabits(): Flow<List<Habit>> = combine(
        habitDao.observeHabits(),
        habitScheduleDao.observeAllSchedules(),
        habitEventDao.observeAllEvents()
    ) { habits, schedules, events ->
        val schedulesByHabit = schedules.associateBy { it.habitId }
        val eventsByHabit = events.groupBy { it.habitId }
        habits.map { habitEntity ->
            val base = habitEntity.toDomain()
            val habitEvents = eventsByHabit[habitEntity.id].orEmpty().map { it.toDomain() }
            val analytics = if (habitEvents.isEmpty()) {
                HabitAnalytics(currentStreak = base.streakCount)
            } else {
                deriveHabitAnalytics(habitEvents)
            }
            base.copy(
                schedule = schedulesByHabit[habitEntity.id]?.toDomain() ?: buildLegacyHabitSchedule(
                    habitId = habitEntity.id,
                    cadence = habitEntity.cadence,
                    windowStartMinute = habitEntity.windowStartMinute,
                    windowEndMinute = habitEntity.windowEndMinute,
                    plannerVisible = habitEntity.isBundled
                ),
                recentEvents = habitEvents.sortedByDescending(HabitEvent::recordedAt).take(10),
                analytics = analytics
            )
        }
    }

    override suspend fun getHabitById(id: String): Habit? {
        val habit = habitDao.getHabitById(id)?.toDomain() ?: return null
        val events = habitEventDao.getEventsForHabit(id).map { it.toDomain() }
        val schedule = habitScheduleDao.getScheduleForHabit(id)?.toDomain() ?: buildLegacyHabitSchedule(
            habitId = habit.id,
            cadence = habit.cadence,
            windowStartMinute = habit.windowStartMinute,
            windowEndMinute = habit.windowEndMinute,
            plannerVisible = habit.isBundled
        )
        return habit.copy(
            schedule = schedule,
            recentEvents = events.sortedByDescending(HabitEvent::recordedAt).take(10),
            analytics = if (events.isEmpty()) {
                HabitAnalytics(currentStreak = habit.streakCount)
            } else {
                deriveHabitAnalytics(events)
            }
        )
    }

    override suspend fun saveHabit(habit: Habit) {
        habitDao.insertHabit(habit.toEntity())
        saveHabitSchedule(
            habit.schedule ?: buildLegacyHabitSchedule(
                habitId = habit.id,
                cadence = habit.cadence,
                windowStartMinute = habit.windowStartMinute,
                windowEndMinute = habit.windowEndMinute,
                plannerVisible = habit.isBundled
            )
        )
    }

    override suspend fun saveHabitSchedule(schedule: HabitSchedule) {
        val now = Instant.now()
        habitScheduleDao.upsertSchedule(schedule.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun addHabitEvent(event: HabitEvent) {
        habitEventDao.insertEvent(event.toEntity())
    }

    override fun observeHabitEventsBetween(
        start: java.time.LocalDate,
        end: java.time.LocalDate
    ): Flow<List<HabitEvent>> =
        habitEventDao.observeEventsBetween(start, end).map { events -> events.map { it.toDomain() } }

    override suspend fun deleteHabit(habit: Habit) = habitDao.deleteHabit(habit.toEntity())
}
