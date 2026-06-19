package com.ChronosFlow.VBCR.feature.tasks

import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule

data class TaskAssistantSummary(
    val headline: String,
    val nextStep: String
)

fun buildTaskAssistantSummary(
    tasks: List<Task>,
    taskSchedulesByTaskId: Map<String, TaskSchedule> = emptyMap()
): TaskAssistantSummary {
    val openTasks = tasks.filterNot { it.isCompleted }
    val urgentTasks = openTasks.filter { it.priority >= 2 }
    val unscheduledUrgentTasks = urgentTasks.filter {
        !taskHasProtectedTime(it, taskSchedulesByTaskId[it.id])
    }
    val headline = when {
        unscheduledUrgentTasks.isNotEmpty() ->
            "${unscheduledUrgentTasks.size} urgent task(s) still need protected time."
        urgentTasks.isNotEmpty() ->
            "Urgent tasks are captured and ready for DayDial placement."
        openTasks.isEmpty() ->
            "Your task inbox is clear."
        else ->
            "${openTasks.size} open task(s) are waiting for prioritization."
    }
    val nextStep = when {
        unscheduledUrgentTasks.isNotEmpty() ->
            "Schedule the top urgent task into DayDial before adding new commitments."
        urgentTasks.isNotEmpty() ->
            "Review the protected urgent work before pulling in lower-priority tasks."
        openTasks.size >= 5 ->
            "Batch the backlog into one planning pass and schedule only the next few concrete tasks."
        else ->
            "Keep the next task concrete, then schedule it once the rest of the day is stable."
    }
    return TaskAssistantSummary(headline = headline, nextStep = nextStep)
}

private fun taskHasProtectedTime(task: Task, schedule: TaskSchedule?): Boolean {
    return schedule != null || (task.targetDate != null && task.preferredDurationMinutes != null)
}
