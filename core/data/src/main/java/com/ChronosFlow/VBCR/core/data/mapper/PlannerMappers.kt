package com.ChronosFlow.VBCR.core.data.mapper

import com.ChronosFlow.VBCR.core.data.model.HabitEventEntity
import com.ChronosFlow.VBCR.core.data.model.HabitScheduleEntity
import com.ChronosFlow.VBCR.core.data.model.MedicationDoseEventEntity
import com.ChronosFlow.VBCR.core.data.model.MedicationSafetyProfileEntity
import com.ChronosFlow.VBCR.core.data.model.MedicationScheduleEntity
import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.model.HabitRecurrencePeriodUnit
import com.ChronosFlow.VBCR.core.domain.model.HabitRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.HabitSchedule
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.MedicationSafetyProfile
import com.ChronosFlow.VBCR.core.domain.model.MedicationSchedule
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrence
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrenceType
import java.time.DayOfWeek
import java.time.Instant

fun HabitScheduleEntity.toDomain(): HabitSchedule = HabitSchedule(
    id = id,
    habitId = habitId,
    recurrence = legacyRecurrenceOrDefault(),
    recurrenceRule = toHabitRecurrenceRule(),
    targetStartMinute = targetStartMinute,
    targetEndMinute = targetEndMinute,
    plannerVisible = plannerVisible,
    pausedUntil = pausedUntil,
    skipDate = skipDate,
    deferUntilMinuteOfDay = deferUntilMinuteOfDay
)

fun HabitSchedule.toEntity(createdAt: Instant, updatedAt: Instant): HabitScheduleEntity {
    val resolvedRule = resolvedRecurrenceRule
    val (recurrenceType, intervalCount, weekdaysCsv, recurrenceRuleKind, quotaTargetCompletions, quotaPeriodUnit) =
        when (resolvedRule) {
            is HabitRecurrenceRule.Scheduled -> SchedulePersistencePayload(
                recurrenceType = resolvedRule.recurrence.type.name,
                intervalCount = resolvedRule.recurrence.interval,
                weekdaysCsv = resolvedRule.recurrence.weekdays.toCsv(),
                recurrenceRuleKind = HABIT_RULE_KIND_SCHEDULED,
                quotaTargetCompletions = null,
                quotaPeriodUnit = null
            )
            is HabitRecurrenceRule.Quota -> SchedulePersistencePayload(
                recurrenceType = resolvedRule.periodUnit.name,
                intervalCount = resolvedRule.interval,
                weekdaysCsv = null,
                recurrenceRuleKind = HABIT_RULE_KIND_QUOTA,
                quotaTargetCompletions = resolvedRule.targetCompletions,
                quotaPeriodUnit = resolvedRule.periodUnit.name
            )
        }

    return HabitScheduleEntity(
        id = id,
        habitId = habitId,
        recurrenceType = recurrenceType,
        intervalCount = intervalCount,
        weekdaysCsv = weekdaysCsv,
        targetStartMinute = targetStartMinute,
        targetEndMinute = targetEndMinute,
        plannerVisible = plannerVisible,
        pausedUntil = pausedUntil,
        skipDate = skipDate,
        deferUntilMinuteOfDay = deferUntilMinuteOfDay,
        createdAt = createdAt,
        updatedAt = updatedAt,
        recurrenceRuleKind = recurrenceRuleKind,
        quotaTargetCompletions = quotaTargetCompletions,
        quotaPeriodUnit = quotaPeriodUnit
    )
}

fun HabitEventEntity.toDomain(): HabitEvent = HabitEvent(
    id = id,
    habitId = habitId,
    type = runCatching { HabitEventType.valueOf(eventType) }.getOrElse { HabitEventType.COMPLETED },
    eventDate = eventDate,
    recordedAt = recordedAt,
    reason = reason,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay
)

fun HabitEvent.toEntity(): HabitEventEntity = HabitEventEntity(
    id = id,
    habitId = habitId,
    eventType = type.name,
    eventDate = eventDate,
    recordedAt = recordedAt,
    reason = reason,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay
)

fun MedicationScheduleEntity.toDomain(): MedicationSchedule = MedicationSchedule(
    id = id,
    medicationPlanId = medicationPlanId,
    recurrence = PlannerRecurrence(
        type = runCatching { PlannerRecurrenceType.valueOf(recurrenceType) }
            .getOrElse { PlannerRecurrenceType.DAILY },
        interval = intervalCount,
        weekdays = weekdaysCsv.toDayOfWeekSet(),
        timesOfDayMinutes = doseTimesCsv.toMinuteList()
    ),
    plannerVisible = plannerVisible,
    pausedUntil = pausedUntil,
    windowMinutes = windowMinutes,
    isPrn = isPrn
)

fun MedicationSchedule.toEntity(createdAt: Instant, updatedAt: Instant): MedicationScheduleEntity = MedicationScheduleEntity(
    id = id,
    medicationPlanId = medicationPlanId,
    recurrenceType = recurrence.type.name,
    intervalCount = recurrence.interval,
    weekdaysCsv = recurrence.weekdays.toCsv(),
    doseTimesCsv = recurrence.normalizedTimesOfDayMinutes.toMinuteCsv(),
    plannerVisible = plannerVisible,
    pausedUntil = pausedUntil,
    windowMinutes = windowMinutes,
    isPrn = isPrn,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MedicationDoseEventEntity.toDomain(): MedicationDoseEvent = MedicationDoseEvent(
    id = id,
    medicationPlanId = medicationPlanId,
    type = runCatching { MedicationDoseEventType.valueOf(eventType) }
        .getOrElse { MedicationDoseEventType.TAKEN },
    eventDate = eventDate,
    recordedAt = recordedAt,
    scheduledMinuteOfDay = scheduledMinuteOfDay,
    reason = reason,
    doseAmount = doseAmount
)

fun MedicationDoseEvent.toEntity(): MedicationDoseEventEntity = MedicationDoseEventEntity(
    id = id,
    medicationPlanId = medicationPlanId,
    eventType = type.name,
    eventDate = eventDate,
    recordedAt = recordedAt,
    scheduledMinuteOfDay = scheduledMinuteOfDay,
    reason = reason,
    doseAmount = doseAmount
)

fun MedicationSafetyProfileEntity.toDomain(): MedicationSafetyProfile = MedicationSafetyProfile(
    medicationPlanId = medicationPlanId,
    form = form,
    route = route,
    strength = strength,
    instructions = instructions,
    mealTiming = mealTiming,
    supplyRemaining = supplyRemaining,
    refillThreshold = refillThreshold,
    pharmacyName = pharmacyName,
    prescriberName = prescriberName,
    cautions = cautionsCsv.toStringList()
)

fun MedicationSafetyProfile.toEntity(): MedicationSafetyProfileEntity = MedicationSafetyProfileEntity(
    medicationPlanId = medicationPlanId,
    form = form,
    route = route,
    strength = strength,
    instructions = instructions,
    mealTiming = mealTiming,
    supplyRemaining = supplyRemaining,
    refillThreshold = refillThreshold,
    pharmacyName = pharmacyName,
    prescriberName = prescriberName,
    cautionsCsv = cautions.toCsv()
)

internal fun String?.toDayOfWeekSet(): Set<DayOfWeek> = this
    .orEmpty()
    .split(',')
    .mapNotNull { value ->
        value.trim().takeIf(String::isNotBlank)?.let {
            runCatching { DayOfWeek.valueOf(it) }.getOrNull()
        }
    }
    .toSet()

internal fun Set<DayOfWeek>.toCsv(): String? = takeIf { it.isNotEmpty() }
    ?.joinToString(",") { it.name }

internal fun String?.toMinuteList(): List<Int> = this
    .orEmpty()
    .split(',')
    .mapNotNull { it.trim().toIntOrNull() }
    .distinct()
    .sorted()

internal fun List<Int>.toMinuteCsv(): String = distinct().sorted().joinToString(",")

internal fun String?.toStringList(): List<String> = this
    .orEmpty()
    .split('|')
    .map(String::trim)
    .filter(String::isNotBlank)

internal fun List<String>.toCsv(): String? = map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .takeIf { it.isNotEmpty() }
    ?.joinToString("|")

private fun HabitScheduleEntity.legacyPlannerRecurrence(): PlannerRecurrence = PlannerRecurrence(
    type = runCatching { PlannerRecurrenceType.valueOf(recurrenceType) }
        .getOrElse { PlannerRecurrenceType.DAILY },
    interval = intervalCount,
    weekdays = weekdaysCsv.toDayOfWeekSet()
)

private fun HabitScheduleEntity.legacyRecurrenceOrDefault(): PlannerRecurrence =
    if (recurrenceRuleKind.equals(HABIT_RULE_KIND_QUOTA, ignoreCase = true)) {
        PlannerRecurrence()
    } else {
        legacyPlannerRecurrence()
    }

private fun HabitScheduleEntity.toHabitRecurrenceRule(): HabitRecurrenceRule? {
    val legacyRecurrence = legacyPlannerRecurrence()
    return when {
        recurrenceRuleKind.equals(HABIT_RULE_KIND_QUOTA, ignoreCase = true) -> {
            val periodUnit = quotaPeriodUnit
                ?.let { rawValue -> runCatching { HabitRecurrencePeriodUnit.valueOf(rawValue) }.getOrNull() }
            val targetCompletions = quotaTargetCompletions
            if (periodUnit != null && targetCompletions != null) {
                runCatching {
                    HabitRecurrenceRule.Quota(
                        targetCompletions = targetCompletions,
                        periodUnit = periodUnit,
                        interval = intervalCount
                    )
                }.getOrNull()
            } else {
                null
            }
        }
        recurrenceRuleKind.equals(HABIT_RULE_KIND_SCHEDULED, ignoreCase = true) ->
            HabitRecurrenceRule.Scheduled(legacyRecurrence)
        else -> null
    }
}

private data class SchedulePersistencePayload(
    val recurrenceType: String,
    val intervalCount: Int,
    val weekdaysCsv: String?,
    val recurrenceRuleKind: String,
    val quotaTargetCompletions: Int?,
    val quotaPeriodUnit: String?
)

private const val HABIT_RULE_KIND_SCHEDULED = "SCHEDULED"
private const val HABIT_RULE_KIND_QUOTA = "QUOTA"
