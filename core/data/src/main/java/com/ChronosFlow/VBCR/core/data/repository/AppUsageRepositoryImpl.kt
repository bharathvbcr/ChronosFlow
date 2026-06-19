package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.AppUsageDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.AppUsageDay
import com.ChronosFlow.VBCR.core.domain.repository.AppUsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class AppUsageRepositoryImpl @Inject constructor(
    private val appUsageDao: AppUsageDao
) : AppUsageRepository {
    override fun observeForDate(date: LocalDate): Flow<AppUsageDay?> =
        appUsageDao.observeForDate(date).map { it?.toDomain() }

    override fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<AppUsageDay>> =
        appUsageDao.observeForDateRange(start, end).map { list -> list.map { it.toDomain() } }

    override suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<AppUsageDay> =
        appUsageDao.getForDateRange(start, end).map { it.toDomain() }

    override suspend fun upsert(day: AppUsageDay) = appUsageDao.upsert(day.toEntity())
}
