package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.DayPlanDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.DayPlan
import com.ChronosFlow.VBCR.core.domain.repository.DayPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class DayPlanRepositoryImpl @Inject constructor(
    private val dayPlanDao: DayPlanDao,
    private val clock: Clock = Clock.systemUTC()
) : DayPlanRepository {
    override fun getDayPlanByDate(date: LocalDate): Flow<DayPlan?> = 
        dayPlanDao.getDayPlanByDate(date).map { it?.toDomain() }

    override suspend fun saveDayPlan(dayPlan: DayPlan) = 
        dayPlanDao.insertDayPlan(dayPlan.toEntity(updatedAt = clock.instant()))

    override suspend fun deleteDayPlan(date: LocalDate) = 
        dayPlanDao.deleteDayPlan(date)
}
