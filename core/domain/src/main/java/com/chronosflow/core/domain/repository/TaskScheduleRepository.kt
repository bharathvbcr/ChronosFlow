package com.chronosflow.core.domain.repository

import com.chronosflow.core.domain.model.TaskSchedule
import kotlinx.coroutines.flow.Flow

interface TaskScheduleRepository {
    fun observeTaskSchedule(taskId: String): Flow<TaskSchedule?>
    suspend fun getTaskSchedule(taskId: String): TaskSchedule?
    suspend fun saveTaskSchedule(schedule: TaskSchedule)
    suspend fun deleteTaskSchedule(taskId: String)
}
