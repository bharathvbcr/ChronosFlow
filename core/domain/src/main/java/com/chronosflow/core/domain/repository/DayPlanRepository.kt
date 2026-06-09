package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.DayPlan
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface DayPlanRepository {
    fun getDayPlanByDate(date: LocalDate): Flow<DayPlan?>
    suspend fun saveDayPlan(dayPlan: DayPlan)
    suspend fun deleteDayPlan(date: LocalDate)
}
