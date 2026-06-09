package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.MoodEnergyCheckIn
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MoodEnergyRepository {
    fun observeForDate(date: LocalDate): Flow<List<MoodEnergyCheckIn>>
    suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<MoodEnergyCheckIn>
    suspend fun getLatest(): MoodEnergyCheckIn?
    suspend fun getForBlock(blockId: String): List<MoodEnergyCheckIn>
    suspend fun save(checkIn: MoodEnergyCheckIn)
    suspend fun delete(id: String)
}
