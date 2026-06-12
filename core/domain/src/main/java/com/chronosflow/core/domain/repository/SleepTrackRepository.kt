package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.SleepTrack
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SleepTrackRepository {
    fun observeForDate(date: LocalDate): Flow<SleepTrack?>
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<SleepTrack>
    suspend fun upsert(track: SleepTrack)
    suspend fun delete(id: String)
}
