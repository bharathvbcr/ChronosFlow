package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import kotlinx.coroutines.flow.Flow

interface FocusSessionRepository {
    fun observeRecoverableSession(): Flow<FocusSessionState?>
    suspend fun getFocusSession(id: String): FocusSessionState?
    suspend fun saveFocusSession(state: FocusSessionState)
}
