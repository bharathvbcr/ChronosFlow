package com.ChronosFlow.VBCR.core.data.repository

import androidx.room.withTransaction
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.data.dao.HabitEventDao
import com.ChronosFlow.VBCR.core.data.dao.HabitDao
import com.ChronosFlow.VBCR.core.data.dao.HabitScheduleDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitAnalytics
import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.HabitSchedule
import com.ChronosFlow.VBCR.core.domain.model.buildLegacyHabitSchedule
import com.ChronosFlow.VBCR.core.domain.model.deriveHabitAnalytics
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class HabitRepositoryImpl @Inject constructor(
    private val db: ChronosDatabase,
    private val habitDao: HabitDao,
    private val habitScheduleDao: HabitScheduleDao,
    private val habitEventDao: HabitEventDao
) : HabitRepository {
    override fun observeHabits(): Flow<List<Habit>> = combine(
        habitDao.observeHabits(),
        habitScheduleDao.observeAllSchedules()
    ) { habits, schedules -> habits to schedules }
        .flatMapLatest { (habits, schedules) ->
            if (habits.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val schedulesByHabit = schedules.associateBy { it.habitId }
            // Build one limited-event Flow per habit (10 rows max) and combine them all.
            val perHabitEventFlows = habits.map { habitEntity ->
                habitEventDao.getRecentEventsForHabit(habitId = habitEntity.id, limit = 10)
            }
            combine(perHabitEventFlows) { eventsArrays ->
                habits.mapIndexed { index, habitEntity ->
                    val base = habitEntity.toDomain()
                    val habitEvents = eventsArrays[index].map { it.toDomain() }
                    val analytics = if (habitEvents.isEmpty()) {
                        HabitAnalytics(currentStreak = base.streakCount)
                    } else {
                        deriveHabitAnalytics(habitEvents)
                    }
                    base.copy(
                        schedule = schedulesByHabit[habitEntity.id]?.toDomain()
                            ?: buildLegacyHabitSchedule(
                                habitId = habitEntity.id,
                                cadence = habitEntity.cadence,
                                windowStartMinute = habitEntity.windowStartMinute,
                                windowEndMinute = habitEntity.windowEndMinute,
                                plannerVisible = habitEntity.isBundled
                            ),
                        recentEvents = habitEvents,
                        analytics = analytics
                    )
                }
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
        db.withTransaction {
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
