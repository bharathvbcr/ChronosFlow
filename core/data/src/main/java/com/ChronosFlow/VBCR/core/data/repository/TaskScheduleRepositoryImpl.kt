package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.TaskScheduleDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskScheduleRepositoryImpl @Inject constructor(
    private val taskScheduleDao: TaskScheduleDao
) : TaskScheduleRepository {
    override fun observeTaskSchedule(taskId: String): Flow<TaskSchedule?> =
        taskScheduleDao.observeScheduleForTask(taskId).map { it?.toDomain() }

    override suspend fun getTaskSchedule(taskId: String): TaskSchedule? =
        taskScheduleDao.getScheduleForTask(taskId)?.toDomain()

    override suspend fun saveTaskSchedule(schedule: TaskSchedule) {
        taskScheduleDao.upsertSchedule(schedule.toEntity())
        taskScheduleDao.replaceReminderRules(
            schedule.id,
            schedule.reminderRules.mapIndexed { index, reminderRule ->
                reminderRule.toEntity(sortOrder = index)
            }
        )
    }

    override suspend fun deleteTaskSchedule(taskId: String) {
        taskScheduleDao.deleteScheduleForTask(taskId)
    }
}
