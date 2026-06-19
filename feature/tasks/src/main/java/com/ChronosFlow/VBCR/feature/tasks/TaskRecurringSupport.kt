package com.ChronosFlow.VBCR.feature.tasks

import com.ChronosFlow.VBCR.core.domain.model.TaskRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.TaskReminderRule
import com.ChronosFlow.VBCR.core.domain.model.TaskReminderTrigger
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import com.ChronosFlow.VBCR.core.domain.usecase.ResolveNextTaskOccurrenceUseCase
import com.ChronosFlow.VBCR.core.ui.components.formatDisplayMinute
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class TaskRecurringCadence {
    DAILY,
    WEEKLY,
    MONTHLY_DAY_OF_MONTH,
    MONTHLY_ORDINAL_WEEKDAY
}

data class TaskReminderDraft(
    val id: String = UUID.randomUUID().toString(),
    val trigger: TaskReminderTrigger = TaskReminderTrigger.AT_TIME,
    val minuteOfDay: Int? = null,
    val offsetMinutesBefore: Int? = null
)

data class TaskRecurringConfig(
    val enabled: Boolean = false,
    val cadence: TaskRecurringCadence = TaskRecurringCadence.DAILY,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int = 1,
    val ordinal: Int = 1,
    val ordinalWeekday: DayOfWeek = DayOfWeek.MONDAY,
    val startsOn: LocalDate = LocalDate.now(),
    val endsOn: LocalDate? = null,
    val maxOccurrences: Int? = null,
    val reminderDrafts: List<TaskReminderDraft> = emptyList()
)

internal fun buildTaskScheduleFromConfig(
    taskId: String,
    existingSchedule: TaskSchedule?,
    config: TaskRecurringConfig,
    occurrenceMinuteOfDay: Int?,
    resolver: ResolveNextTaskOccurrenceUseCase,
    now: Instant = Instant.now()
): TaskSchedule? {
    if (!config.enabled) return null

    val recurrenceRule = when (config.cadence) {
        TaskRecurringCadence.DAILY -> TaskRecurrenceRule.Daily(
            intervalDays = config.interval,
            startsOn = config.startsOn,
            endsOn = config.endsOn,
            maxOccurrences = config.maxOccurrences
        )
        TaskRecurringCadence.WEEKLY -> TaskRecurrenceRule.Weekly(
            intervalWeeks = config.interval,
            weekdays = config.weekdays,
            startsOn = config.startsOn,
            endsOn = config.endsOn,
            maxOccurrences = config.maxOccurrences
        )
        TaskRecurringCadence.MONTHLY_DAY_OF_MONTH -> TaskRecurrenceRule.MonthlyByDayOfMonth(
            intervalMonths = config.interval,
            dayOfMonth = config.dayOfMonth,
            startsOn = config.startsOn,
            endsOn = config.endsOn,
            maxOccurrences = config.maxOccurrences
        )
        TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY -> TaskRecurrenceRule.MonthlyByOrdinalWeekday(
            intervalMonths = config.interval,
            ordinal = config.ordinal,
            weekday = config.ordinalWeekday,
            startsOn = config.startsOn,
            endsOn = config.endsOn,
            maxOccurrences = config.maxOccurrences
        )
    }

    val scheduleId = existingSchedule?.id ?: "schedule-$taskId"
    val reminderRules = config.reminderDrafts.map { draft ->
        TaskReminderRule(
            id = draft.id,
            taskScheduleId = scheduleId,
            trigger = draft.trigger,
            minuteOfDay = draft.minuteOfDay,
            offsetMinutesBefore = draft.offsetMinutesBefore
        )
    }
    val provisional = TaskSchedule(
        id = scheduleId,
        taskId = taskId,
        recurrenceRule = recurrenceRule,
        occurrenceMinuteOfDay = occurrenceMinuteOfDay,
        nextOccurrenceDate = null,
        lastCompletedOccurrenceDate = existingSchedule?.lastCompletedOccurrenceDate,
        generatedThroughDate = existingSchedule?.generatedThroughDate,
        isPaused = existingSchedule?.isPaused ?: false,
        reminderRules = reminderRules,
        createdAt = existingSchedule?.createdAt ?: now,
        updatedAt = now
    )
    return provisional.copy(
        nextOccurrenceDate = resolver(
            schedule = provisional,
            afterDate = recurrenceRule.startsOn.minusDays(1)
        )
    )
}

internal fun recurringSummary(
    config: TaskRecurringConfig,
    occurrenceMinuteOfDay: Int?
): String? {
    if (!config.enabled) return null

    val cadenceSummary = when (config.cadence) {
        TaskRecurringCadence.DAILY -> if (config.interval == 1) {
            "Daily"
        } else {
            "Every ${config.interval} days"
        }
        TaskRecurringCadence.WEEKLY -> config.weekdays
            .sortedBy { it.value }
            .joinToString(" + ") { it.displayShortName() }
        TaskRecurringCadence.MONTHLY_DAY_OF_MONTH ->
            "Day ${config.dayOfMonth}"
        TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY ->
            "${config.ordinal.displayOrdinalLabel()} ${config.ordinalWeekday.displayFullName()}"
    }
    val repeatsSummary = when (config.cadence) {
        TaskRecurringCadence.DAILY -> null
        TaskRecurringCadence.WEEKLY -> if (config.interval == 1) {
            "repeats weekly"
        } else {
            "repeats every ${config.interval} weeks"
        }
        TaskRecurringCadence.MONTHLY_DAY_OF_MONTH,
        TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY -> if (config.interval == 1) {
            "repeats monthly"
        } else {
            "repeats every ${config.interval} months"
        }
    }
    val timeSummary = occurrenceMinuteOfDay?.let { "at ${formatDisplayMinute(it)}" }
    val reminderSummary = config.reminderDrafts
        .takeIf { it.isNotEmpty() }
        ?.let { "${it.size} reminder${if (it.size == 1) "" else "s"}" }
    val endSummary = config.endsOn?.let { "ends ${it}" }

    val leading = listOfNotNull(cadenceSummary, timeSummary).joinToString(" ")
    return listOfNotNull(leading.takeIf { it.isNotBlank() }, repeatsSummary, reminderSummary, endSummary).joinToString(", ")
}

internal fun recurringSummary(schedule: TaskSchedule?): String? {
    val taskConfig = schedule?.toRecurringConfig() ?: return null
    return recurringSummary(taskConfig, schedule.occurrenceMinuteOfDay)
}

internal fun TaskSchedule.toRecurringConfig(): TaskRecurringConfig {
    val rule = recurrenceRule
    return when (rule) {
        is TaskRecurrenceRule.Daily -> TaskRecurringConfig(
            enabled = true,
            cadence = TaskRecurringCadence.DAILY,
            interval = rule.intervalDays,
            startsOn = rule.startsOn,
            endsOn = rule.endsOn,
            maxOccurrences = rule.maxOccurrences,
            reminderDrafts = reminderRules.map { it.toDraft() }
        )
        is TaskRecurrenceRule.Weekly -> TaskRecurringConfig(
            enabled = true,
            cadence = TaskRecurringCadence.WEEKLY,
            interval = rule.intervalWeeks,
            weekdays = rule.weekdays,
            startsOn = rule.startsOn,
            endsOn = rule.endsOn,
            maxOccurrences = rule.maxOccurrences,
            reminderDrafts = reminderRules.map { it.toDraft() }
        )
        is TaskRecurrenceRule.MonthlyByDayOfMonth -> TaskRecurringConfig(
            enabled = true,
            cadence = TaskRecurringCadence.MONTHLY_DAY_OF_MONTH,
            interval = rule.intervalMonths,
            dayOfMonth = rule.dayOfMonth,
            startsOn = rule.startsOn,
            endsOn = rule.endsOn,
            maxOccurrences = rule.maxOccurrences,
            reminderDrafts = reminderRules.map { it.toDraft() }
        )
        is TaskRecurrenceRule.MonthlyByOrdinalWeekday -> TaskRecurringConfig(
            enabled = true,
            cadence = TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY,
            interval = rule.intervalMonths,
            ordinal = rule.ordinal,
            ordinalWeekday = rule.weekday,
            startsOn = rule.startsOn,
            endsOn = rule.endsOn,
            maxOccurrences = rule.maxOccurrences,
            reminderDrafts = reminderRules.map { it.toDraft() }
        )
    }
}

private fun TaskReminderRule.toDraft(): TaskReminderDraft = TaskReminderDraft(
    id = id,
    trigger = trigger,
    minuteOfDay = minuteOfDay,
    offsetMinutesBefore = offsetMinutesBefore
)

private fun DayOfWeek.displayShortName(): String = name.take(3).lowercase()
    .replaceFirstChar(Char::titlecase)

private fun DayOfWeek.displayFullName(): String = name.lowercase()
    .replaceFirstChar(Char::titlecase)

internal fun Int.displayOrdinalLabel(): String = when (this) {
    1 -> "First"
    2 -> "Second"
    3 -> "Third"
    4 -> "Fourth"
    -1 -> "Last"
    else -> "${this}th"
}
