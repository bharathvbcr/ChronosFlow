package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.MoodEnergyCheckInDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyCheckIn
import com.ChronosFlow.VBCR.core.domain.repository.MoodEnergyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class MoodEnergyRepositoryImpl @Inject constructor(
    private val dao: MoodEnergyCheckInDao
) : MoodEnergyRepository {
    override fun observeForDate(date: LocalDate): Flow<List<MoodEnergyCheckIn>> =
        dao.observeForDate(date).map { list -> list.map { it.toDomain(currentZoneId()) } }

    override fun observeForDateRange(start: LocalDate, end: LocalDate): Flow<List<MoodEnergyCheckIn>> =
        dao.observeForDateRange(start, end).map { list -> list.map { it.toDomain(currentZoneId()) } }

    override suspend fun getForDateRange(start: LocalDate, end: LocalDate): List<MoodEnergyCheckIn> =
        dao.getForDateRange(start, end).map { it.toDomain(currentZoneId()) }

    override suspend fun getLatest(): MoodEnergyCheckIn? = dao.getLatest()?.toDomain(currentZoneId())

    override suspend fun getForBlock(blockId: String): List<MoodEnergyCheckIn> =
        dao.getForBlock(blockId).map { it.toDomain(currentZoneId()) }

    override suspend fun save(checkIn: MoodEnergyCheckIn) = dao.insert(checkIn.toEntity(currentZoneId()))

    override suspend fun delete(id: String) = dao.deleteById(id)

    private fun currentZoneId(): ZoneId = ZoneId.systemDefault()
}
