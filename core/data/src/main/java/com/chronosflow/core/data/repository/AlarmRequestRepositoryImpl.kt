package com.chronosflow.core.data.repository

import com.chronosflow.core.data.dao.AlarmDao
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class AlarmRequestRepositoryImpl @Inject constructor(
    private val alarmDao: AlarmDao
) : AlarmRequestRepository {
    override fun observePendingRequests(now: Instant): Flow<List<AlarmRequest>> =
        alarmDao.observePendingRequests(now).map { requests -> requests.map { it.toDomain() } }

    override fun observeRequestsByType(type: AlarmRequestType): Flow<List<AlarmRequest>> =
        alarmDao.observeRequestsByType(type.name).map { requests -> requests.map { it.toDomain() } }

    override suspend fun saveAlarmRequest(request: AlarmRequest) =
        alarmDao.insertAlarmRequest(request.toEntity())

    override suspend fun getAlarmRequest(id: String): AlarmRequest? = alarmDao.getAlarmRequest(id)?.toDomain()

    override suspend fun pruneExpiredAlarmRequests(threshold: Instant) =
        alarmDao.deleteExpiredAlarmRequests(threshold)
}
