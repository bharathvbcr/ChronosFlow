package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.TimeBlockDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.data.sync.NoOpSyncMutationNotifier
import com.ChronosFlow.VBCR.core.data.sync.SyncMutationNotifier
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class TimeBlockRepositoryImpl @Inject constructor(
    private val timeBlockDao: TimeBlockDao,
    private val syncMutationNotifier: SyncMutationNotifier = NoOpSyncMutationNotifier
) : TimeBlockRepository {
    override fun getTimeBlocksByDate(date: LocalDate): Flow<List<TimeBlock>> = 
        timeBlockDao.getTimeBlocksByDate(date).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getTimeBlockById(id: String): TimeBlock? =
        timeBlockDao.getById(id)?.toDomain()

    override suspend fun saveTimeBlock(timeBlock: TimeBlock) {
        timeBlockDao.insertTimeBlock(timeBlock.toEntity())
        syncMutationNotifier.notifyLocalMutation()
    }

    override suspend fun deleteTimeBlock(timeBlock: TimeBlock) {
        timeBlockDao.deleteTimeBlock(timeBlock.toEntity())
        syncMutationNotifier.notifyLocalMutation()
    }

    override suspend fun clearDay(date: LocalDate) {
        timeBlockDao.clearDay(date)
        syncMutationNotifier.notifyLocalMutation()
    }
}
