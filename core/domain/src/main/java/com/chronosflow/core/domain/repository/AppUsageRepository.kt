package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.AppUsageDay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface AppUsageRepository {
    fun observeForDate(date: LocalDate): Flow<AppUsageDay?>
    fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<AppUsageDay>>
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<AppUsageDay>
    suspend fun upsert(day: AppUsageDay)
}
