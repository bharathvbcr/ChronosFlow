package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTasksUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    operator fun invoke(): Flow<List<Task>> = repository.getAllTasks()
}
