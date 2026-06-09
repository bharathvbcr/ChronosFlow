package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.TimeBlock
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TimeBlockRepository {
    fun getTimeBlocksByDate(date: LocalDate): Flow<List<TimeBlock>>
    suspend fun getTimeBlockById(id: String): TimeBlock?
    suspend fun saveTimeBlock(timeBlock: TimeBlock)
    suspend fun deleteTimeBlock(timeBlock: TimeBlock)
    suspend fun clearDay(date: LocalDate)
}

