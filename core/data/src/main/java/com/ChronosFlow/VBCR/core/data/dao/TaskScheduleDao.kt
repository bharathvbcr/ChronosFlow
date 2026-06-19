package com.ChronosFlow.VBCR.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ChronosFlow.VBCR.core.data.model.TaskReminderRuleEntity
import com.ChronosFlow.VBCR.core.data.model.TaskScheduleEntity
import com.ChronosFlow.VBCR.core.data.model.TaskScheduleWithReminderRules
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskScheduleDao {
    @Transaction
    @Query("SELECT * FROM task_schedules WHERE taskId = :taskId")
    fun observeScheduleForTask(taskId: String): Flow<TaskScheduleWithReminderRules?>

    @Transaction
    @Query("SELECT * FROM task_schedules WHERE taskId = :taskId")
    suspend fun getScheduleForTask(taskId: String): TaskScheduleWithReminderRules?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: TaskScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminderRules(rules: List<TaskReminderRuleEntity>)

    @Query("DELETE FROM task_reminder_rules WHERE taskScheduleId = :taskScheduleId")
    suspend fun deleteReminderRulesForSchedule(taskScheduleId: String)

    @Query("DELETE FROM task_schedules WHERE taskId = :taskId")
    suspend fun deleteScheduleForTask(taskId: String)

    @Transaction
    suspend fun replaceReminderRules(
        taskScheduleId: String,
        rules: List<TaskReminderRuleEntity>
    ) {
        deleteReminderRulesForSchedule(taskScheduleId)
        if (rules.isNotEmpty()) {
            insertReminderRules(rules)
        }
    }
}
