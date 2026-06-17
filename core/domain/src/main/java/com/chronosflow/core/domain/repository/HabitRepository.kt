package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.HabitEvent
import com.chronosflow.core.domain.model.HabitSchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HabitRepository {
    fun observeHabits(): Flow<List<Habit>>
    suspend fun getHabitById(id: String): Habit?
    suspend fun saveHabit(habit: Habit)
    suspend fun saveHabitSchedule(schedule: HabitSchedule)
    suspend fun addHabitEvent(event: HabitEvent)
    fun observeHabitEventsBetween(start: LocalDate, end: LocalDate): Flow<List<HabitEvent>>
    suspend fun deleteHabit(habit: Habit)
}
