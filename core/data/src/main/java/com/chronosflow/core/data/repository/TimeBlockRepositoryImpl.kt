package com.chronosflow.core.data.repository

import com.chronosflow.core.data.dao.TimeBlockDao
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.data.sync.NoOpSyncMutationNotifier
import com.chronosflow.core.data.sync.SyncMutationNotifier
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.repository.TimeBlockRepository
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
