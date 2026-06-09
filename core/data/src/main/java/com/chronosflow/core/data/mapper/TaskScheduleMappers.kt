package com.chronosflow.core.data.mapper

import com.chronosflow.core.data.model.TaskReminderRuleEntity
import com.chronosflow.core.data.model.TaskScheduleEntity
import com.chronosflow.core.data.model.TaskScheduleWithReminderRules
import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskRecurrenceType
import com.chronosflow.core.domain.model.TaskReminderRule
import com.chronosflow.core.domain.model.TaskReminderTrigger
import com.chronosflow.core.domain.model.TaskSchedule
import java.time.DayOfWeek

fun TaskScheduleWithReminderRules.toDomain(): TaskSchedule = TaskSchedule(
    id = schedule.id,
    taskId = schedule.taskId,
    recurrenceRule = schedule.toRecurrenceRule(),
    occurrenceMinuteOfDay = schedule.occurrenceMinuteOfDay,
    nextOccurrenceDate = schedule.nextOccurrenceDate,
    lastCompletedOccurrenceDate = schedule.lastCompletedOccurrenceDate,
    generatedThroughDate = schedule.generatedThroughDate,
    isPaused = schedule.isPaused,
    reminderRules = reminderRules
        .sortedBy(TaskReminderRuleEntity::sortOrder)
        .map(TaskReminderRuleEntity::toDomain),
    createdAt = schedule.createdAt,
    updatedAt = schedule.updatedAt
)

fun TaskSchedule.toEntity(): TaskScheduleEntity {
    val recurrenceType = recurrenceRule.type
    val intervalCount: Int
    val weekdaysCsv: String?
    val dayOfMonth: Int?
    val ordinalInMonth: Int?
    val weekdayInMonth: String?
    val startsOn: java.time.LocalDate
    val endsOn: java.time.LocalDate?
    val maxOccurrences: Int?

    when (val rule = recurrenceRule) {
        is TaskRecurrenceRule.Daily -> {
            intervalCount = rule.intervalDays
            weekdaysCsv = null
            dayOfMonth = null
            ordinalInMonth = null
            weekdayInMonth = null
            startsOn = rule.startsOn
            endsOn = rule.endsOn
            maxOccurrences = rule.maxOccurrences
        }
        is TaskRecurrenceRule.Weekly -> {
            intervalCount = rule.intervalWeeks
            weekdaysCsv = rule.weekdays.toCsv()
            dayOfMonth = null
            ordinalInMonth = null
            weekdayInMonth = null
            startsOn = rule.startsOn
            endsOn = rule.endsOn
            maxOccurrences = rule.maxOccurrences
        }
        is TaskRecurrenceRule.MonthlyByDayOfMonth -> {
            intervalCount = rule.intervalMonths
            weekdaysCsv = null
            dayOfMonth = rule.dayOfMonth
            ordinalInMonth = null
            weekdayInMonth = null
            startsOn = rule.startsOn
            endsOn = rule.endsOn
            maxOccurrences = rule.maxOccurrences
        }
        is TaskRecurrenceRule.MonthlyByOrdinalWeekday -> {
            intervalCount = rule.intervalMonths
            weekdaysCsv = null
            dayOfMonth = null
            ordinalInMonth = rule.ordinal
            weekdayInMonth = rule.weekday.name
            startsOn = rule.startsOn
            endsOn = rule.endsOn
            maxOccurrences = rule.maxOccurrences
        }
    }
    return TaskScheduleEntity(
        id = id,
        taskId = taskId,
        recurrenceType = recurrenceType.name,
        intervalCount = intervalCount,
        weekdaysCsv = weekdaysCsv,
        dayOfMonth = dayOfMonth,
        ordinalInMonth = ordinalInMonth,
        weekdayInMonth = weekdayInMonth,
        startsOn = startsOn,
        endsOn = endsOn,
        maxOccurrences = maxOccurrences,
        occurrenceMinuteOfDay = occurrenceMinuteOfDay,
        nextOccurrenceDate = nextOccurrenceDate,
        lastCompletedOccurrenceDate = lastCompletedOccurrenceDate,
        generatedThroughDate = generatedThroughDate,
        isPaused = isPaused,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun TaskReminderRuleEntity.toDomain(): TaskReminderRule = TaskReminderRule(
    id = id,
    taskScheduleId = taskScheduleId,
    trigger = runCatching { TaskReminderTrigger.valueOf(trigger) }
        .getOrElse { TaskReminderTrigger.AT_TIME },
    minuteOfDay = minuteOfDay,
    offsetMinutesBefore = offsetMinutesBefore
)

fun TaskReminderRule.toEntity(sortOrder: Int): TaskReminderRuleEntity = TaskReminderRuleEntity(
    id = id,
    taskScheduleId = taskScheduleId,
    trigger = trigger.name,
    minuteOfDay = minuteOfDay,
    offsetMinutesBefore = offsetMinutesBefore,
    sortOrder = sortOrder
)

private fun TaskScheduleEntity.toRecurrenceRule(): TaskRecurrenceRule {
    return when (runCatching { TaskRecurrenceType.valueOf(recurrenceType) }.getOrElse { TaskRecurrenceType.DAILY }) {
        TaskRecurrenceType.DAILY -> TaskRecurrenceRule.Daily(
            intervalDays = intervalCount,
            startsOn = startsOn,
            endsOn = endsOn,
            maxOccurrences = maxOccurrences
        )
        TaskRecurrenceType.WEEKLY -> TaskRecurrenceRule.Weekly(
            intervalWeeks = intervalCount,
            weekdays = weekdaysCsv.toDayOfWeekSet(),
            startsOn = startsOn,
            endsOn = endsOn,
            maxOccurrences = maxOccurrences
        )
        TaskRecurrenceType.MONTHLY_DAY_OF_MONTH -> TaskRecurrenceRule.MonthlyByDayOfMonth(
            intervalMonths = intervalCount,
            dayOfMonth = dayOfMonth ?: startsOn.dayOfMonth,
            startsOn = startsOn,
            endsOn = endsOn,
            maxOccurrences = maxOccurrences
        )
        TaskRecurrenceType.MONTHLY_ORDINAL_WEEKDAY -> TaskRecurrenceRule.MonthlyByOrdinalWeekday(
            intervalMonths = intervalCount,
            ordinal = ordinalInMonth ?: 1,
            weekday = weekdayInMonth
                ?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                ?: startsOn.dayOfWeek,
            startsOn = startsOn,
            endsOn = endsOn,
            maxOccurrences = maxOccurrences
        )
    }
}
