package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.SleepTrackDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.SleepTrack
import com.ChronosFlow.VBCR.core.domain.repository.SleepTrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class SleepTrackRepositoryImpl @Inject constructor(
    private val sleepTrackDao: SleepTrackDao
) : SleepTrackRepository {
    override fun observeForDate(date: LocalDate): Flow<SleepTrack?> =
        sleepTrackDao.observeForDate(date).map { it?.toDomain() }

    override fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<SleepTrack>> =
        sleepTrackDao.observeForDateRange(start, end).map { list -> list.map { it.toDomain() } }

    override suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<SleepTrack> =
        sleepTrackDao.getForDateRange(start, end).map { it.toDomain() }

    override suspend fun upsert(track: SleepTrack) = sleepTrackDao.upsert(track.toEntity())

    override suspend fun delete(id: String) = sleepTrackDao.deleteById(id)
}
