package com.ChronosFlow.VBCR.core.notifications

import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.buildLegacyHabitSchedule
import java.time.LocalDate
import java.time.ZoneId

/**
 * The "folded reminders" fold — the Android mirror of iOS `BlockLiveActivityCoordinator.foldedReminders`.
 * When the current-block ("now" / up-next) live notification is showing, the single most-imminent DUE
 * reminder (a medication dose, a task, or a habit window) is surfaced ON that surface as a glanceable text
 * segment plus one action button. When fold is enabled, [AlarmDeliveryCoordinator] suppresses
 * separate med/task/habit banners for items already on this surface (iOS passive-demotion parity).
 *
 * Everything in this file is pure and Android-free so the ranking and per-kind due rules are unit-testable
 * without a device — the repository plumbing lives in [FoldedReminderResolver].
 */

/** Which entity a folded reminder came from — drives ranking priority and the action-button label/receiver. */
enum class FoldedReminderKind { MEDICATION, TASK, HABIT }

/**
 * A reminder that is already actionable today. [dueMinute] is the minute-of-day it became due (for ranking
 * the most-overdue first); [isOverdue] is true once at least [OVERDUE_THRESHOLD_MINUTES] have passed.
 */
data class FoldedReminder(
    val kind: FoldedReminderKind,
    val entityId: String,
    val title: String,
    val detail: String,
    val dueMinute: Int,
    val isOverdue: Boolean
)

/**
 * A dose/task/habit reads as "overdue" this many minutes after its time passes — matches the reminder
 * snooze interval so a just-due item still reads as "due now" first (mirrors iOS `overdueThreshold`).
 */
internal const val OVERDUE_THRESHOLD_MINUTES = 15

/** How many folded reminders the live surface carries at once before collapsing the rest into "＋N more". */
internal const val MAX_FOLDED_REMINDERS = 3

/**
 * Ranks folded reminders the way the live surface presents them: medication first (health-critical), then
 * task, then habit; within a kind the most-overdue (earliest [FoldedReminder.dueMinute]) first. Capped at
 * [max]. The first element is the primary chip / action.
 */
internal fun rankFoldedReminders(
    candidates: List<FoldedReminder>,
    max: Int = MAX_FOLDED_REMINDERS
): List<FoldedReminder> =
    candidates
        .sortedWith(compareBy({ kindWeight(it.kind) }, { it.dueMinute }))
        .take(max.coerceAtLeast(0))

/** Entity identity for fold suppression — mirrors iOS `FoldedReminderLiveResolver.EntityKey`. */
internal data class FoldedEntityKey(val kind: FoldedReminderKind, val entityId: String)

internal fun foldedEntityKeys(reminders: List<FoldedReminder>): Set<FoldedEntityKey> =
    reminders.map { FoldedEntityKey(it.kind, it.entityId) }.toSet()

/**
 * When fold is on, skip separate med/task/habit banners already surfaced on the live "now"
 * notification — mirrors iOS `ChronosNotifications.shouldSuppressFoldedReminderBanner`.
 */
internal fun shouldSuppressFoldedReminderBanner(
    folded: Set<FoldedEntityKey>,
    medicationPlanId: String?,
    taskId: String?,
    habitId: String?
): Boolean {
    if (folded.isEmpty()) return false
    medicationPlanId?.let { id ->
        if (folded.contains(FoldedEntityKey(FoldedReminderKind.MEDICATION, id))) return true
    }
    taskId?.let { id ->
        if (folded.contains(FoldedEntityKey(FoldedReminderKind.TASK, id))) return true
    }
    habitId?.let { id ->
        if (folded.contains(FoldedEntityKey(FoldedReminderKind.HABIT, id))) return true
    }
    return false
}

/** Ranking weight: medication 0 < task 1 < habit 2 (mirrors iOS `kindWeight`). */
private fun kindWeight(kind: FoldedReminderKind): Int = when (kind) {
    FoldedReminderKind.MEDICATION -> 0
    FoldedReminderKind.TASK -> 1
    FoldedReminderKind.HABIT -> 2
}

/** All reminder minutes for a plan — schedule times when present, else the legacy single minute. */
internal fun medicationReminderMinutes(plan: MedicationPlan): List<Int> {
    val scheduled = plan.schedule?.recurrence?.timesOfDayMinutes
        ?.map { ((it % 1440) + 1440) % 1440 }
        ?.filter { it in 0..1439 }
        ?.distinct()
        ?.sorted()
    return if (!scheduled.isNullOrEmpty()) scheduled else listOf(plan.reminderMinuteOfDay)
}

/** Whether reminders are suppressed because the plan is paused through [date] (inclusive). */
internal fun MedicationPlan.isPaused(date: LocalDate = LocalDate.now()): Boolean =
    schedule?.pausedUntil?.let { !it.isBefore(date) } == true

/** True when a TAKEN event covers the scheduled dose at [minute] on [date]. */
internal fun MedicationPlan.isDoseTaken(date: LocalDate, minute: Int): Boolean =
    recentDoseEvents.any {
        it.eventDate == date &&
            it.type == MedicationDoseEventType.TAKEN &&
            (it.scheduledMinuteOfDay == null || it.scheduledMinuteOfDay == minute)
    }

/**
 * Medication doses due right now: plan active, not paused, and the latest passed reminder minute
 * for today has not been taken. Supports multi-dose schedules via [medicationReminderMinutes].
 * Mirrors iOS `medicationReminders`.
 */
internal fun buildMedicationFoldedReminders(
    plans: List<MedicationPlan>,
    today: LocalDate,
    nowMinute: Int
): List<FoldedReminder> = plans.flatMap { plan ->
    if (!plan.isActive || plan.isPaused(today)) return@flatMap emptyList()
    val due = medicationReminderMinutes(plan).filter { it <= nowMinute }.maxOrNull()
        ?: return@flatMap emptyList()
    if (plan.isDoseTaken(today, due)) return@flatMap emptyList()
    val dose = "${plan.dosage} ${plan.unit}".trim()
    val detail = if (dose.isBlank()) {
        "Due ${formatCurrentBlockMinute(due)}"
    } else {
        "Due ${formatCurrentBlockMinute(due)} · $dose"
    }
    listOf(
        FoldedReminder(
            kind = FoldedReminderKind.MEDICATION,
            entityId = plan.id,
            title = plan.name,
            detail = detail,
            dueMinute = due,
            isOverdue = nowMinute - due >= OVERDUE_THRESHOLD_MINUTES
        )
    )
}

/**
 * Tasks due today whose due time has passed: not completed, [Task.dueDate] falls on [today] (in [zoneId]),
 * and its minute-of-day `<= nowMinute`. Detail reads "Due 9:00 AM", or "Due 9:00 AM · High priority" when
 * `priority >= 3`. Mirrors iOS `taskReminders`.
 */
internal fun buildTaskFoldedReminders(
    tasks: List<Task>,
    today: LocalDate,
    nowMinute: Int,
    zoneId: ZoneId
): List<FoldedReminder> = tasks.mapNotNull { task ->
    if (task.isCompleted) return@mapNotNull null
    val due = task.dueDate ?: return@mapNotNull null
    val dueLocal = due.atZone(zoneId).toLocalDateTime()
    if (dueLocal.toLocalDate() != today) return@mapNotNull null
    val dueMinute = dueLocal.hour * 60 + dueLocal.minute
    if (dueMinute > nowMinute) return@mapNotNull null
    val detail = if (task.priority >= 3) {
        "Due ${formatCurrentBlockMinute(dueMinute)} · High priority"
    } else {
        "Due ${formatCurrentBlockMinute(dueMinute)}"
    }
    FoldedReminder(
        kind = FoldedReminderKind.TASK,
        entityId = task.id,
        title = task.title,
        detail = detail,
        dueMinute = dueMinute,
        isOverdue = nowMinute - dueMinute >= OVERDUE_THRESHOLD_MINUTES
    )
}

/**
 * Habits whose window is open now: active, due today (recurrence lands and not completed/skipped/paused via
 * the shared [isReminderDueOnDate] rules), and the window (deferral, else start) has opened
 * (`open <= nowMinute`). Detail reads "Window open", or "Window open · 5-day streak" when a streak is running.
 * Mirrors iOS `habitReminders`; reuses [HabitDueRules] so the fold and the alarm scheduler never drift.
 */
internal fun buildHabitFoldedReminders(
    habits: List<Habit>,
    today: LocalDate,
    nowMinute: Int
): List<FoldedReminder> = habits.mapNotNull { habit ->
    if (!habit.isActive) return@mapNotNull null
    if (!habit.isReminderDueOnDate(today, habit.recurrenceReferenceDate(today))) return@mapNotNull null
    val schedule = habit.schedule ?: buildLegacyHabitSchedule(
        habitId = habit.id,
        cadence = habit.cadence,
        windowStartMinute = habit.windowStartMinute,
        windowEndMinute = habit.windowEndMinute,
        plannerVisible = habit.isBundled
    )
    // Honour a one-off deferral ("remind me later"); otherwise the window start.
    val open = schedule.deferUntilMinuteOfDay ?: schedule.targetStartMinute
    if (open > nowMinute) return@mapNotNull null
    val detail = if (habit.streakCount > 0) {
        "Window open · ${habit.streakCount}-day streak"
    } else {
        "Window open"
    }
    FoldedReminder(
        kind = FoldedReminderKind.HABIT,
        entityId = habit.id,
        title = habit.title,
        detail = detail,
        dueMinute = open,
        isOverdue = nowMinute - open >= OVERDUE_THRESHOLD_MINUTES
    )
}
