package com.chronosflow.feature.habits

import com.chronosflow.core.domain.model.HabitRecurrencePeriodUnit
import com.chronosflow.core.domain.model.HabitRecurrenceRule
import com.chronosflow.core.domain.model.HabitSchedule
import com.chronosflow.core.domain.model.PlannerRecurrence
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import java.time.DayOfWeek

internal enum class HabitRecurrenceEditorKind {
    SCHEDULED,
    QUOTA
}

internal enum class HabitRecurrenceScheduleMode {
    DAILY,
    WEEKDAYS,
    WEEKENDS,
    SELECTED_WEEKDAYS,
    EVERY_N_DAYS,
    EVERY_N_WEEKS
}

internal enum class HabitRecurrenceCustomPattern(val label: String) {
    SELECTED_WEEKDAYS("Selected weekdays"),
    EVERY_N_DAYS("Every N days"),
    EVERY_N_WEEKS("Every N weeks"),
    QUOTA("Quota")
}

internal data class HabitRecurrenceEditorState(
    val kind: HabitRecurrenceEditorKind = HabitRecurrenceEditorKind.SCHEDULED,
    val scheduleMode: HabitRecurrenceScheduleMode = HabitRecurrenceScheduleMode.DAILY,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val quotaCompletions: Int = 2,
    val quotaPeriodUnit: HabitRecurrencePeriodUnit = HabitRecurrencePeriodUnit.WEEK,
    val quotaInterval: Int = 1
) {
    fun summary(): String = when (kind) {
        HabitRecurrenceEditorKind.QUOTA -> buildQuotaSummary(
            count = quotaCompletions,
            unit = quotaPeriodUnit,
            interval = quotaInterval
        )
        HabitRecurrenceEditorKind.SCHEDULED -> buildScheduledSummary(
            mode = scheduleMode,
            interval = interval,
            weekdays = weekdays
        )
    }

    fun cadenceLabel(): String = summary()

    fun customPattern(): HabitRecurrenceCustomPattern = when (kind) {
        HabitRecurrenceEditorKind.QUOTA -> HabitRecurrenceCustomPattern.QUOTA
        HabitRecurrenceEditorKind.SCHEDULED -> when (scheduleMode) {
            HabitRecurrenceScheduleMode.EVERY_N_DAYS -> HabitRecurrenceCustomPattern.EVERY_N_DAYS
            HabitRecurrenceScheduleMode.EVERY_N_WEEKS -> HabitRecurrenceCustomPattern.EVERY_N_WEEKS
            HabitRecurrenceScheduleMode.DAILY,
            HabitRecurrenceScheduleMode.WEEKDAYS,
            HabitRecurrenceScheduleMode.WEEKENDS,
            HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS -> HabitRecurrenceCustomPattern.SELECTED_WEEKDAYS
        }
    }

    fun toHabitSchedule(
        existingSchedule: HabitSchedule?,
        habitId: String,
        targetStartMinute: Int,
        targetEndMinute: Int,
        plannerVisible: Boolean
    ): HabitSchedule {
        val rule = when (kind) {
            HabitRecurrenceEditorKind.QUOTA -> HabitRecurrenceRule.Quota(
                targetCompletions = quotaCompletions.coerceAtLeast(1),
                periodUnit = quotaPeriodUnit,
                interval = quotaInterval.coerceAtLeast(1)
            )
            HabitRecurrenceEditorKind.SCHEDULED -> {
                val recurrence = when (scheduleMode) {
                    HabitRecurrenceScheduleMode.DAILY -> PlannerRecurrence(
                        type = PlannerRecurrenceType.DAILY
                    )
                    HabitRecurrenceScheduleMode.WEEKDAYS -> PlannerRecurrence(
                        type = PlannerRecurrenceType.WEEKDAYS,
                        weekdays = WEEKDAY_SET
                    )
                    HabitRecurrenceScheduleMode.WEEKENDS -> PlannerRecurrence(
                        type = PlannerRecurrenceType.WEEKENDS,
                        weekdays = WEEKEND_SET
                    )
                    HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS -> PlannerRecurrence(
                        type = PlannerRecurrenceType.SELECTED_WEEKDAYS,
                        weekdays = weekdays.normalizedWeekdays()
                    )
                    HabitRecurrenceScheduleMode.EVERY_N_DAYS -> PlannerRecurrence(
                        type = PlannerRecurrenceType.EVERY_N_DAYS,
                        interval = interval.coerceAtLeast(1)
                    )
                    HabitRecurrenceScheduleMode.EVERY_N_WEEKS -> PlannerRecurrence(
                        type = PlannerRecurrenceType.WEEKLY_INTERVAL,
                        interval = interval.coerceAtLeast(1),
                        weekdays = weekdays.normalizedWeekdays(allowEmpty = true)
                    )
                }
                HabitRecurrenceRule.Scheduled(recurrence)
            }
        }
        val recurrence = when (rule) {
            is HabitRecurrenceRule.Scheduled -> rule.recurrence
            is HabitRecurrenceRule.Quota -> PlannerRecurrence()
        }
        return HabitSchedule(
            id = existingSchedule?.id.orEmpty(),
            habitId = habitId,
            recurrence = recurrence,
            recurrenceRule = rule,
            targetStartMinute = targetStartMinute,
            targetEndMinute = targetEndMinute,
            plannerVisible = plannerVisible,
            pausedUntil = existingSchedule?.pausedUntil,
            skipDate = existingSchedule?.skipDate,
            deferUntilMinuteOfDay = existingSchedule?.deferUntilMinuteOfDay
        )
    }
}

internal fun buildHabitRecurrenceEditorState(
    cadence: String,
    schedule: HabitSchedule?
): HabitRecurrenceEditorState {
    val rule = schedule?.resolvedRecurrenceRule
    return when (rule) {
        is HabitRecurrenceRule.Quota -> HabitRecurrenceEditorState(
            kind = HabitRecurrenceEditorKind.QUOTA,
            quotaCompletions = rule.targetCompletions,
            quotaPeriodUnit = rule.periodUnit,
            quotaInterval = rule.interval
        )
        is HabitRecurrenceRule.Scheduled -> buildScheduledEditorState(rule.recurrence, cadence)
        null -> buildLegacyCadenceEditorState(cadence)
    }
}

internal fun habitRecurrenceSummary(
    cadence: String,
    schedule: HabitSchedule?
): String = buildHabitRecurrenceEditorState(cadence = cadence, schedule = schedule).summary()

internal fun HabitRecurrenceCustomPattern.applyTo(
    state: HabitRecurrenceEditorState
): HabitRecurrenceEditorState = when (this) {
    HabitRecurrenceCustomPattern.SELECTED_WEEKDAYS -> state.copy(
        kind = HabitRecurrenceEditorKind.SCHEDULED,
        scheduleMode = HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS,
        weekdays = state.weekdays.normalizedWeekdays()
    )
    HabitRecurrenceCustomPattern.EVERY_N_DAYS -> state.copy(
        kind = HabitRecurrenceEditorKind.SCHEDULED,
        scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_DAYS,
        interval = state.interval.coerceAtLeast(2)
    )
    HabitRecurrenceCustomPattern.EVERY_N_WEEKS -> state.copy(
        kind = HabitRecurrenceEditorKind.SCHEDULED,
        scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_WEEKS,
        interval = state.interval.coerceAtLeast(1),
        weekdays = state.weekdays.normalizedWeekdays(allowEmpty = true)
    )
    HabitRecurrenceCustomPattern.QUOTA -> state.copy(
        kind = HabitRecurrenceEditorKind.QUOTA,
        quotaCompletions = state.quotaCompletions.coerceAtLeast(1),
        quotaPeriodUnit = state.quotaPeriodUnit,
        quotaInterval = state.quotaInterval.coerceAtLeast(1)
    )
}

private fun buildScheduledEditorState(
    recurrence: PlannerRecurrence,
    cadence: String
): HabitRecurrenceEditorState = when (recurrence.type) {
    PlannerRecurrenceType.DAILY -> {
        if (recurrence.interval > 1) {
            HabitRecurrenceEditorState(
                scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_DAYS,
                interval = recurrence.interval
            )
        } else {
            HabitRecurrenceEditorState(scheduleMode = HabitRecurrenceScheduleMode.DAILY)
        }
    }
    PlannerRecurrenceType.WEEKDAYS -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.WEEKDAYS,
        weekdays = WEEKDAY_SET
    )
    PlannerRecurrenceType.WEEKENDS -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.WEEKENDS,
        weekdays = WEEKEND_SET
    )
    PlannerRecurrenceType.SELECTED_WEEKDAYS -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS,
        weekdays = recurrence.weekdays.normalizedWeekdays()
    )
    PlannerRecurrenceType.EVERY_N_DAYS -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_DAYS,
        interval = recurrence.interval.coerceAtLeast(1)
    )
    PlannerRecurrenceType.WEEKLY_INTERVAL -> HabitRecurrenceEditorState(
        scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_WEEKS,
        interval = recurrence.interval.coerceAtLeast(1),
        weekdays = recurrence.weekdays.normalizedWeekdays(allowEmpty = true)
    )
    else -> buildLegacyCadenceEditorState(cadence)
}

private fun buildLegacyCadenceEditorState(cadence: String): HabitRecurrenceEditorState {
    val normalized = cadence.trim()
    val lowercase = normalized.lowercase()
    return when {
        lowercase == "daily" -> HabitRecurrenceEditorState(scheduleMode = HabitRecurrenceScheduleMode.DAILY)
        lowercase == "weekdays" -> HabitRecurrenceEditorState(
            scheduleMode = HabitRecurrenceScheduleMode.WEEKDAYS,
            weekdays = WEEKDAY_SET
        )
        lowercase == "weekends" -> HabitRecurrenceEditorState(
            scheduleMode = HabitRecurrenceScheduleMode.WEEKENDS,
            weekdays = WEEKEND_SET
        )
        lowercase == "weekly" -> HabitRecurrenceEditorState(
            scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_WEEKS,
            interval = 1
        )
        everyDaysRegex.matches(lowercase) -> HabitRecurrenceEditorState(
            scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_DAYS,
            interval = everyDaysRegex.matchEntire(lowercase)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        )
        everyWeeksRegex.matches(lowercase) -> {
            val match = everyWeeksRegex.matchEntire(lowercase)
            HabitRecurrenceEditorState(
                scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_WEEKS,
                interval = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                weekdays = match?.groupValues?.get(2).orEmpty().parseWeekdays()
            )
        }
        weeklyOnRegex.matches(lowercase) -> HabitRecurrenceEditorState(
            scheduleMode = HabitRecurrenceScheduleMode.EVERY_N_WEEKS,
            interval = 1,
            weekdays = weeklyOnRegex.matchEntire(lowercase)?.groupValues?.get(1).orEmpty().parseWeekdays()
        )
        quotaPerRegex.matches(lowercase) -> {
            val match = quotaPerRegex.matchEntire(lowercase)
            HabitRecurrenceEditorState(
                kind = HabitRecurrenceEditorKind.QUOTA,
                quotaCompletions = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                quotaPeriodUnit = match?.groupValues?.get(2).orEmpty().toPeriodUnit()
            )
        }
        quotaEveryRegex.matches(lowercase) -> {
            val match = quotaEveryRegex.matchEntire(lowercase)
            HabitRecurrenceEditorState(
                kind = HabitRecurrenceEditorKind.QUOTA,
                quotaCompletions = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                quotaInterval = match?.groupValues?.get(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                quotaPeriodUnit = match?.groupValues?.get(3).orEmpty().toPeriodUnit()
            )
        }
        normalized.parseWeekdays().isNotEmpty() -> HabitRecurrenceEditorState(
            scheduleMode = HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS,
            weekdays = normalized.parseWeekdays()
        )
        else -> HabitRecurrenceEditorState(scheduleMode = HabitRecurrenceScheduleMode.DAILY)
    }
}

private fun buildScheduledSummary(
    mode: HabitRecurrenceScheduleMode,
    interval: Int,
    weekdays: Set<DayOfWeek>
): String = when (mode) {
    HabitRecurrenceScheduleMode.DAILY -> "Daily"
    HabitRecurrenceScheduleMode.WEEKDAYS -> "Weekdays"
    HabitRecurrenceScheduleMode.WEEKENDS -> "Weekends"
    HabitRecurrenceScheduleMode.SELECTED_WEEKDAYS -> weekdays
        .normalizedWeekdays()
        .joinToString(" + ") { it.displayShortName() }
        .ifBlank { "Selected weekdays" }
    HabitRecurrenceScheduleMode.EVERY_N_DAYS ->
        if (interval <= 1) "Daily" else "Every $interval days"
    HabitRecurrenceScheduleMode.EVERY_N_WEEKS -> {
        val weekdaysSummary = weekdays
            .normalizedWeekdays(allowEmpty = true)
            .joinToString(" + ") { it.displayShortName() }
        when {
            interval <= 1 && weekdaysSummary.isBlank() -> "Weekly"
            interval <= 1 -> "Weekly on $weekdaysSummary"
            weekdaysSummary.isBlank() -> "Every $interval weeks"
            else -> "Every $interval weeks on $weekdaysSummary"
        }
    }
}

private fun buildQuotaSummary(
    count: Int,
    unit: HabitRecurrencePeriodUnit,
    interval: Int
): String {
    val safeCount = count.coerceAtLeast(1)
    val safeInterval = interval.coerceAtLeast(1)
    val unitLabel = unit.displayLabel(plural = safeInterval > 1)
    return if (safeInterval == 1) {
        "$safeCount time${if (safeCount == 1) "" else "s"} per ${unit.displayLabel(plural = false)}"
    } else {
        "$safeCount time${if (safeCount == 1) "" else "s"} every $safeInterval $unitLabel"
    }
}

private fun Set<DayOfWeek>.normalizedWeekdays(allowEmpty: Boolean = false): Set<DayOfWeek> {
    val normalized = toList().sortedBy { it.value }.toSet()
    return if (normalized.isEmpty() && !allowEmpty) {
        setOf(DayOfWeek.MONDAY)
    } else {
        normalized
    }
}

private fun String.parseWeekdays(): Set<DayOfWeek> = split('+', ',', '/')
    .map { token -> token.trim().lowercase() }
    .mapNotNull { token ->
        when (token) {
            "mon", "monday" -> DayOfWeek.MONDAY
            "tue", "tues", "tuesday" -> DayOfWeek.TUESDAY
            "wed", "wednesday" -> DayOfWeek.WEDNESDAY
            "thu", "thur", "thurs", "thursday" -> DayOfWeek.THURSDAY
            "fri", "friday" -> DayOfWeek.FRIDAY
            "sat", "saturday" -> DayOfWeek.SATURDAY
            "sun", "sunday" -> DayOfWeek.SUNDAY
            else -> null
        }
    }
    .toSet()

private fun String.toPeriodUnit(): HabitRecurrencePeriodUnit = when {
    contains("month") -> HabitRecurrencePeriodUnit.MONTH
    contains("day") -> HabitRecurrencePeriodUnit.DAY
    else -> HabitRecurrencePeriodUnit.WEEK
}

private fun HabitRecurrencePeriodUnit.displayLabel(plural: Boolean): String = when (this) {
    HabitRecurrencePeriodUnit.DAY -> if (plural) "days" else "day"
    HabitRecurrencePeriodUnit.WEEK -> if (plural) "weeks" else "week"
    HabitRecurrencePeriodUnit.MONTH -> if (plural) "months" else "month"
}

private fun DayOfWeek.displayShortName(): String = name.take(3).lowercase()
    .replaceFirstChar(Char::titlecase)

private val everyDaysRegex = Regex("^every (\\d+) days?$")
private val everyWeeksRegex = Regex("^every (\\d+) weeks?(?: on (.+))?$")
private val weeklyOnRegex = Regex("^weekly on (.+)$")
private val quotaPerRegex = Regex("^(\\d+) time(?:s)? per (day|week|month)$")
private val quotaEveryRegex = Regex("^(\\d+) time(?:s)? every (\\d+) (day|days|week|weeks|month|months)$")
private val WEEKDAY_SET = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)
private val WEEKEND_SET = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
