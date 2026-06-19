package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.FocusSessionDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class FocusSessionRepositoryImpl @Inject constructor(
    private val focusSessionDao: FocusSessionDao
) : FocusSessionRepository {
    override fun observeRecoverableSession(): Flow<FocusSessionState?> =
        focusSessionDao.observeRecoverableSession().map { it?.toDomain() }

    override suspend fun getFocusSession(id: String): FocusSessionState? =
        focusSessionDao.getFocusSession(id)?.toDomain()

    override suspend fun saveFocusSession(state: FocusSessionState) =
        focusSessionDao.insertFocusSession(state.toEntity(now = Instant.now()))
}
