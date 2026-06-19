package com.ChronosFlow.VBCR.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class MedicationSchedule(
    val id: String,
    val medicationPlanId: String,
    val recurrence: PlannerRecurrence = PlannerRecurrence(),
    val plannerVisible: Boolean = true,
    val pausedUntil: LocalDate? = null,
    val windowMinutes: Int = 15,
    val isPrn: Boolean = false
)

enum class MedicationDoseEventType {
    SCHEDULED,
    TAKEN,
    SKIPPED,
    MISSED,
    SNOOZED,
    PAUSED,
    RESUMED
}

data class MedicationDoseEvent(
    val id: String,
    val medicationPlanId: String,
    val type: MedicationDoseEventType,
    val eventDate: LocalDate,
    val recordedAt: Instant,
    val scheduledMinuteOfDay: Int?,
    val reason: String?,
    val doseAmount: String?
)

data class MedicationSafetyProfile(
    val medicationPlanId: String,
    val form: String = "tablet",
    val route: String = "oral",
    val strength: String? = null,
    val instructions: String? = null,
    val mealTiming: String = "Anytime",
    val supplyRemaining: Int? = null,
    val refillThreshold: Int? = null,
    val pharmacyName: String? = null,
    val prescriberName: String? = null,
    val cautions: List<String> = emptyList()
) {
    val refillSoon: Boolean
        get() = supplyRemaining != null && refillThreshold != null && supplyRemaining <= refillThreshold
}

data class MedicationAnalytics(
    val adherenceRate: Float = 0f,
    val takenCountLast7Days: Int = 0,
    val missedCountLast14Days: Int = 0,
    val snoozedCountLast7Days: Int = 0,
    val refillSoon: Boolean = false
)

/** Per-day taken/missed counts for a medication plan, oldest first, one entry per day in the window. */
data class MedicationDailyAdherence(
    val date: LocalDate,
    val takenCount: Int,
    val missedCount: Int
)

fun deriveMedicationAdherenceTrend(
    events: List<MedicationDoseEvent>,
    windowDays: Int = 14,
    today: LocalDate = LocalDate.now()
): List<MedicationDailyAdherence> {
    if (windowDays <= 0) return emptyList()
    val start = today.minusDays((windowDays - 1).toLong())
    val byDate = events
        .filter { it.eventDate in start..today }
        .groupBy { it.eventDate }
    return (0 until windowDays).map { offset ->
        val date = start.plusDays(offset.toLong())
        val dayEvents = byDate[date].orEmpty()
        MedicationDailyAdherence(
            date = date,
            takenCount = dayEvents.count { it.type == MedicationDoseEventType.TAKEN },
            missedCount = dayEvents.count { it.type == MedicationDoseEventType.MISSED }
        )
    }
}

fun buildLegacyMedicationSchedule(
    medicationPlanId: String,
    primaryReminderMinute: Int,
    secondaryReminderMinute: Int? = null,
    plannerVisible: Boolean = true
): MedicationSchedule {
    val times = listOfNotNull(primaryReminderMinute, secondaryReminderMinute).distinct().sorted()
    return MedicationSchedule(
        id = "schedule-$medicationPlanId",
        medicationPlanId = medicationPlanId,
        recurrence = PlannerRecurrence(
            type = if (times.size > 1) {
                PlannerRecurrenceType.MULTIPLE_TIMES_DAILY
            } else {
                PlannerRecurrenceType.DAILY
            },
            timesOfDayMinutes = times
        ),
        plannerVisible = plannerVisible
    )
}

fun deriveMedicationAnalytics(
    events: List<MedicationDoseEvent>,
    today: LocalDate = LocalDate.now(),
    supplyRemaining: Int? = null,
    refillThreshold: Int? = null
): MedicationAnalytics {
    val recent14 = events.filter { it.eventDate >= today.minusDays(13) }
    val taken14 = recent14.filter { it.type == MedicationDoseEventType.TAKEN }
    val missed14 = recent14.count { it.type == MedicationDoseEventType.MISSED }
    val snoozed7 = events.count {
        it.type == MedicationDoseEventType.SNOOZED && it.eventDate >= today.minusDays(6)
    }
    val adherenceDenominator = taken14.size + missed14
    val adherenceRate = if (adherenceDenominator == 0) {
        0f
    } else {
        taken14.size.toFloat() / adherenceDenominator.toFloat()
    }
    return MedicationAnalytics(
        adherenceRate = adherenceRate,
        takenCountLast7Days = taken14.count { it.eventDate >= today.minusDays(6) },
        missedCountLast14Days = missed14,
        snoozedCountLast7Days = snoozed7,
        refillSoon = supplyRemaining != null && refillThreshold != null && supplyRemaining <= refillThreshold
    )
}
