package com.chronosflow.core.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class HabitSchedule(
    val id: String,
    val habitId: String,
    val recurrence: PlannerRecurrence = PlannerRecurrence(),
    val recurrenceRule: HabitRecurrenceRule? = null,
    val targetStartMinute: Int,
    val targetEndMinute: Int,
    val plannerVisible: Boolean = false,
    val pausedUntil: LocalDate? = null,
    val skipDate: LocalDate? = null,
    val deferUntilMinuteOfDay: Int? = null
) {
    init {
        when (val rule = recurrenceRule) {
            is HabitRecurrenceRule.Scheduled -> require(rule.recurrence == recurrence) {
                "HabitSchedule recurrence must match a scheduled recurrence rule."
            }
            is HabitRecurrenceRule.Quota -> require(recurrence == PlannerRecurrence()) {
                "HabitSchedule recurrence must stay at the default legacy value for quota rules."
            }
            null -> Unit
        }
    }

    val resolvedRecurrenceRule: HabitRecurrenceRule
        get() = recurrenceRule ?: HabitRecurrenceRule.Scheduled(recurrence)
}

enum class HabitEventType {
    COMPLETED,
    SKIPPED,
    PAUSED,
    RESUMED,
    MISSED,
    DEFERRED
}

data class HabitEvent(
    val id: String,
    val habitId: String,
    val type: HabitEventType,
    val eventDate: LocalDate,
    val recordedAt: Instant,
    val reason: String?,
    val startMinuteOfDay: Int?,
    val endMinuteOfDay: Int?
)

data class HabitAnalytics(
    val adherenceRate: Float = 0f,
    val completedCountLast7Days: Int = 0,
    val missedCountLast14Days: Int = 0,
    val skippedCountLast14Days: Int = 0,
    val currentStreak: Int = 0,
    val bestCompletionMinuteOfDay: Int? = null
)

/** Per-day completion/miss counts for a habit, oldest first, one entry per day in the window. */
data class HabitDailyCompletion(
    val date: LocalDate,
    val completedCount: Int,
    val missedCount: Int
)

fun deriveHabitCompletionTrend(
    events: List<HabitEvent>,
    windowDays: Int = 14,
    today: LocalDate = LocalDate.now()
): List<HabitDailyCompletion> {
    if (windowDays <= 0) return emptyList()
    val start = today.minusDays((windowDays - 1).toLong())
    val byDate = events
        .filter { it.eventDate in start..today }
        .groupBy { it.eventDate }
    return (0 until windowDays).map { offset ->
        val date = start.plusDays(offset.toLong())
        val dayEvents = byDate[date].orEmpty()
        HabitDailyCompletion(
            date = date,
            completedCount = dayEvents.count { it.type == HabitEventType.COMPLETED },
            missedCount = dayEvents.count { it.type == HabitEventType.MISSED }
        )
    }
}

fun buildLegacyHabitSchedule(
    habitId: String,
    cadence: String,
    windowStartMinute: Int,
    windowEndMinute: Int,
    plannerVisible: Boolean
): HabitSchedule {
    val parsedCadence = parseLegacyHabitCadence(cadence)
    return HabitSchedule(
        id = "schedule-$habitId",
        habitId = habitId,
        recurrence = parsedCadence.recurrence,
        recurrenceRule = parsedCadence.recurrenceRule,
        targetStartMinute = windowStartMinute,
        targetEndMinute = windowEndMinute,
        plannerVisible = plannerVisible
    )
}

fun deriveHabitAnalytics(
    events: List<HabitEvent>,
    today: LocalDate = LocalDate.now()
): HabitAnalytics {
    val recent14 = events.filter { it.eventDate >= today.minusDays(13) }
    val completed14 = recent14.filter { it.type == HabitEventType.COMPLETED }
    val missed14 = recent14.count { it.type == HabitEventType.MISSED }
    val skipped14 = recent14.count { it.type == HabitEventType.SKIPPED }
    val adherenceDenominator = completed14.size + missed14 + skipped14
    val adherenceRate = if (adherenceDenominator == 0) {
        0f
    } else {
        completed14.size.toFloat() / adherenceDenominator.toFloat()
    }
    val completed7 = completed14.count { it.eventDate >= today.minusDays(6) }
    val completionMinutes = completed14.mapNotNull(HabitEvent::startMinuteOfDay)
    val bestMinute = completionMinutes
        .takeIf { it.isNotEmpty() }
        ?.groupingBy { it }
        ?.eachCount()
        ?.maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        ?.key
    val completionDates = completed14.map(HabitEvent::eventDate).toSet()
    var streak = 0
    var cursor = today
    while (cursor in completionDates) {
        streak += 1
        cursor = cursor.minusDays(1)
    }
    return HabitAnalytics(
        adherenceRate = adherenceRate,
        completedCountLast7Days = completed7,
        missedCountLast14Days = missed14,
        skippedCountLast14Days = skipped14,
        currentStreak = streak,
        bestCompletionMinuteOfDay = bestMinute
    )
}

private data class LegacyHabitCadenceParse(
    val recurrence: PlannerRecurrence,
    val recurrenceRule: HabitRecurrenceRule? = null
)

private fun parseLegacyHabitCadence(cadence: String): LegacyHabitCadenceParse {
    val normalized = cadence.trim()
    val lowercase = normalized.lowercase()
    return when {
        lowercase == "daily" -> LegacyHabitCadenceParse(
            recurrence = PlannerRecurrence(type = PlannerRecurrenceType.DAILY)
        )
        lowercase == "weekdays" -> LegacyHabitCadenceParse(
            recurrence = PlannerRecurrence(
                type = PlannerRecurrenceType.WEEKDAYS,
                weekdays = LEGACY_WEEKDAY_SET
            )
        )
        lowercase == "weekends" -> LegacyHabitCadenceParse(
            recurrence = PlannerRecurrence(
                type = PlannerRecurrenceType.WEEKENDS,
                weekdays = LEGACY_WEEKEND_SET
            )
        )
        lowercase == "weekly" -> LegacyHabitCadenceParse(
            recurrence = PlannerRecurrence(type = PlannerRecurrenceType.WEEKLY_INTERVAL)
        )
        legacyEveryDaysRegex.matches(lowercase) -> LegacyHabitCadenceParse(
            recurrence = PlannerRecurrence(
                type = PlannerRecurrenceType.EVERY_N_DAYS,
                interval = legacyEveryDaysRegex.matchEntire(lowercase)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?.coerceAtLeast(1)
                    ?: 1
            )
        )
        legacyEveryWeeksRegex.matches(lowercase) -> {
            val match = legacyEveryWeeksRegex.matchEntire(lowercase)
            LegacyHabitCadenceParse(
                recurrence = PlannerRecurrence(
                    type = PlannerRecurrenceType.WEEKLY_INTERVAL,
                    interval = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    weekdays = match?.groupValues?.get(2).orEmpty().toLegacyWeekdaySet()
                )
            )
        }
        legacyWeeklyOnRegex.matches(lowercase) -> LegacyHabitCadenceParse(
            recurrence = PlannerRecurrence(
                type = PlannerRecurrenceType.WEEKLY_INTERVAL,
                interval = 1,
                weekdays = legacyWeeklyOnRegex.matchEntire(lowercase)?.groupValues?.get(1).orEmpty().toLegacyWeekdaySet()
            )
        )
        legacyQuotaPerRegex.matches(lowercase) -> {
            val match = legacyQuotaPerRegex.matchEntire(lowercase)
            val quotaRule = HabitRecurrenceRule.Quota(
                targetCompletions = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                periodUnit = match?.groupValues?.get(2).orEmpty().toLegacyPeriodUnit()
            )
            LegacyHabitCadenceParse(
                recurrence = PlannerRecurrence(),
                recurrenceRule = quotaRule
            )
        }
        legacyQuotaEveryRegex.matches(lowercase) -> {
            val match = legacyQuotaEveryRegex.matchEntire(lowercase)
            val quotaRule = HabitRecurrenceRule.Quota(
                targetCompletions = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                periodUnit = match?.groupValues?.get(3).orEmpty().toLegacyPeriodUnit(),
                interval = match?.groupValues?.get(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            )
            LegacyHabitCadenceParse(
                recurrence = PlannerRecurrence(),
                recurrenceRule = quotaRule
            )
        }
        normalized.toLegacyWeekdaySet().isNotEmpty() -> LegacyHabitCadenceParse(
            recurrence = PlannerRecurrence(
                type = PlannerRecurrenceType.SELECTED_WEEKDAYS,
                weekdays = normalized.toLegacyWeekdaySet()
            )
        )
        else -> LegacyHabitCadenceParse(
            recurrence = PlannerRecurrence(type = PlannerRecurrenceType.DAILY)
        )
    }
}

private fun String.toLegacyWeekdaySet(): Set<DayOfWeek> = split('+', ',', '/')
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

private fun String.toLegacyPeriodUnit(): HabitRecurrencePeriodUnit = when {
    contains("month") -> HabitRecurrencePeriodUnit.MONTH
    contains("day") -> HabitRecurrencePeriodUnit.DAY
    else -> HabitRecurrencePeriodUnit.WEEK
}

private val legacyEveryDaysRegex = Regex("^every (\\d+) days?$")
private val legacyEveryWeeksRegex = Regex("^every (\\d+) weeks?(?: on (.+))?$")
private val legacyWeeklyOnRegex = Regex("^weekly on (.+)$")
private val legacyQuotaPerRegex = Regex("^(\\d+) time(?:s)? per (day|week|month)$")
private val legacyQuotaEveryRegex = Regex("^(\\d+) time(?:s)? every (\\d+) (day|days|week|weeks|month|months)$")
private val LEGACY_WEEKDAY_SET = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)
private val LEGACY_WEEKEND_SET = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
