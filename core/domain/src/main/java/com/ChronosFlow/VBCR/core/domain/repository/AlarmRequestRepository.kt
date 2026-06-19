package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface AlarmRequestRepository {
    fun observePendingRequests(now: Instant): Flow<List<AlarmRequest>>
    fun observeRequestsByType(type: AlarmRequestType): Flow<List<AlarmRequest>>
    suspend fun saveAlarmRequest(request: AlarmRequest)
    suspend fun getAlarmRequest(id: String): AlarmRequest?
    suspend fun pruneExpiredAlarmRequests(threshold: Instant)
}
