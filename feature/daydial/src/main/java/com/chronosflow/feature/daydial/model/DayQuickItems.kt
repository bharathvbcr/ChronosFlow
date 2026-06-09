package com.chronosflow.feature.daydial.model

import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.HabitEventType
import com.chronosflow.core.domain.model.HabitRecurrenceRule
import com.chronosflow.core.domain.model.AppLaunchTarget
import com.chronosflow.core.domain.model.MedicationDoseEvent
import com.chronosflow.core.domain.model.MedicationDoseEventType
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.PlannerRecurrence
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.domain.model.buildLegacyHabitSchedule
import com.chronosflow.core.notifications.TaskContextCommand
import com.chronosflow.core.notifications.TaskContextCommandResolver
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val DAY_MINUTES = 24 * 60

internal enum class DayQuickItemKind {
    TASK,
    HABIT,
    MEDICATION
}

internal data class DayQuickItemUiModel(
    val id: String,
    val kind: DayQuickItemKind,
    val title: String,
    val detail: String,
    val status: String,
    val isDone: Boolean,
    val scheduledMinuteOfDay: Int? = null,
    val contextAction: DayQuickContextActionUiModel? = null
)

internal data class DayQuickContextActionUiModel(
    val label: String,
    val contentDescription: String,
    val taskCommand: TaskContextCommand? = null,
    val appLaunchTarget: AppLaunchTarget? = null
)

internal data class DayQuickItemsUiState(
    val tasks: List<DayQuickItemUiModel> = emptyList(),
    val habits: List<DayQuickItemUiModel> = emptyList(),
    val medications: List<DayQuickItemUiModel> = emptyList()
) {
    val isEmpty: Boolean
        get() = tasks.isEmpty() && habits.isEmpty() && medications.isEmpty()
}

internal fun buildDayQuickItemsState(
    selectedDate: LocalDate,
    tasks: List<Task>,
    taskSchedules: Map<String, TaskSchedule?> = emptyMap(),
    habits: List<Habit>,
    medications: List<MedicationPlan>
): DayQuickItemsUiState = DayQuickItemsUiState(
    tasks = tasks
        .mapNotNull { task -> task.toDayQuickItem(selectedDate, taskSchedules[task.id]) }
        .sortedWith(compareBy<DayQuickItemUiModel> { it.isDone }.thenBy { it.scheduledMinuteOfDay ?: Int.MAX_VALUE }),
    habits = habits
        .mapNotNull { habit -> habit.toDayQuickItem(selectedDate) }
        .sortedWith(compareBy<DayQuickItemUiModel> { it.isDone }.thenBy { it.scheduledMinuteOfDay ?: Int.MAX_VALUE }),
    medications = medications
        .flatMap { plan -> plan.toDayQuickMedicationItems(selectedDate) }
        .sortedWith(compareBy<DayQuickItemUiModel> { it.scheduledMinuteOfDay ?: Int.MAX_VALUE }.thenBy { it.isDone })
)

internal fun dayQuickPrimaryActionLabel(item: DayQuickItemUiModel): String = when (item.kind) {
    DayQuickItemKind.TASK -> if (item.isDone) "${item.title} completed" else "Complete ${item.title}"
    DayQuickItemKind.HABIT -> if (item.isDone) "${item.title} completed" else "Complete ${item.title}"
    DayQuickItemKind.MEDICATION -> if (item.isDone) "${item.title} recorded" else "Mark ${item.title} taken"
}

internal fun dayQuickSecondaryActionLabel(item: DayQuickItemUiModel): String = when (item.kind) {
    DayQuickItemKind.MEDICATION -> if (item.isDone) "${item.title} already recorded" else "Mark ${item.title} missed"
    else -> "Open ${item.title}"
}

internal fun dayQuickContextActionLabel(item: DayQuickItemUiModel): String =
    item.contextAction?.contentDescription ?: "Open context action for ${item.title}"

internal fun dayQuickGroupSummaryLabel(items: List<DayQuickItemUiModel>): String {
    val doneCount = items.count { it.isDone }
    val dueCount = items.size - doneCount
    return listOfNotNull(
        dueCount.takeIf { it > 0 }?.let { "$it due" },
        doneCount.takeIf { it > 0 }?.let { "$it done" }
    ).joinToString(" - ").ifBlank { "No items" }
}

internal fun dayQuickGroupOpenActionLabel(kind: DayQuickItemKind): String = when (kind) {
    DayQuickItemKind.TASK -> "View all tasks"
    DayQuickItemKind.HABIT -> "View all habits"
    DayQuickItemKind.MEDICATION -> "View all meds"
}

internal fun dayQuickEmptyActionLabel(kind: DayQuickItemKind): String = when (kind) {
    DayQuickItemKind.TASK -> "Open Tasks"
    DayQuickItemKind.HABIT -> "Open Habits"
    DayQuickItemKind.MEDICATION -> "Open Meds"
}

private fun Task.toDayQuickItem(selectedDate: LocalDate, schedule: TaskSchedule?): DayQuickItemUiModel? {
    val dueDate = dueDate?.atZone(ZoneId.systemDefault())?.toLocalDate()
    val belongsToDate = targetDate == selectedDate ||
        dueDate == selectedDate ||
        schedule?.nextOccurrenceDate == selectedDate ||
        schedule?.lastCompletedOccurrenceDate == selectedDate
    if (!belongsToDate) return null
    val done = isCompleted || schedule?.lastCompletedOccurrenceDate == selectedDate
    val displayTitle = title.ifBlank { "Untitled task" }
    return DayQuickItemUiModel(
        id = id,
        kind = DayQuickItemKind.TASK,
        title = displayTitle,
        detail = taskQuickDetail(this, schedule),
        status = if (done) "Done" else "Due today",
        isDone = done,
        scheduledMinuteOfDay = preferredStartMinuteOfDay,
        contextAction = primaryTaskContextAction(displayTitle)
    )
}

private fun Task.primaryTaskContextAction(displayTitle: String): DayQuickContextActionUiModel? {
    val command = TaskContextCommandResolver.resolve(
        task = this,
        includeInternalCommands = false,
        includeCompleteCommand = false
    ).primaryExternalCommand ?: return null
    return DayQuickContextActionUiModel(
        label = command.shortLabel,
        contentDescription = "${command.shortLabel} for $displayTitle: ${command.label}",
        taskCommand = command
    )
}

private fun taskQuickDetail(task: Task, schedule: TaskSchedule?): String {
    val parts = buildList {
        task.preferredStartMinuteOfDay?.let { add("Around ${formatDayQuickMinute(it)}") }
        task.preferredDurationMinutes?.let { add(formatDayQuickDuration(it)) }
        if (schedule != null) add("Recurring")
        if (task.checklist.isNotEmpty()) add("${task.checklist.count { !it.isCompleted }} steps left")
    }
    return parts.joinToString(" - ").ifBlank { "Task" }
}

private fun Habit.toDayQuickItem(selectedDate: LocalDate): DayQuickItemUiModel? {
    if (!isActive) return null
    val schedule = schedule ?: buildLegacyHabitSchedule(
        habitId = id,
        cadence = cadence,
        windowStartMinute = windowStartMinute,
        windowEndMinute = windowEndMinute,
        plannerVisible = isBundled
    )
    val recurrenceDue = when (val rule = schedule.resolvedRecurrenceRule) {
        is HabitRecurrenceRule.Scheduled -> recurrenceOccursOn(
            date = selectedDate,
            recurrence = rule.recurrence,
            startDate = recentEvents.minOfOrNull { it.eventDate } ?: lastCompletedDate ?: selectedDate
        )
        is HabitRecurrenceRule.Quota -> true
    }
    val completed = lastCompletedDate == selectedDate ||
        recentEvents.any { it.eventDate == selectedDate && it.type == HabitEventType.COMPLETED }
    val skipped = schedule.skipDate == selectedDate ||
        recentEvents.any { it.eventDate == selectedDate && it.type == HabitEventType.SKIPPED }
    val paused = schedule.pausedUntil?.let { !it.isBefore(selectedDate) } == true
    if (!recurrenceDue && !completed && !skipped && !paused) return null
    val terminal = completed || skipped
    val status = when {
        completed -> "Done"
        skipped -> "Skipped"
        paused -> "Paused"
        else -> "Due today"
    }
    val displayTitle = title.ifBlank { "Untitled habit" }
    return DayQuickItemUiModel(
        id = id,
        kind = DayQuickItemKind.HABIT,
        title = displayTitle,
        detail = "${formatDayQuickMinute(schedule.targetStartMinute)} - ${formatDayQuickMinute(schedule.targetEndMinute)}",
        status = status,
        isDone = terminal,
        scheduledMinuteOfDay = schedule.targetStartMinute,
        contextAction = launchTarget?.toDayQuickContextAction(displayTitle)
    )
}

private fun AppLaunchTarget.toDayQuickContextAction(displayTitle: String): DayQuickContextActionUiModel {
    val actionLabel = label.toDayQuickLaunchLabel()
    return DayQuickContextActionUiModel(
        label = actionLabel,
        contentDescription = "$actionLabel for $displayTitle",
        appLaunchTarget = this
    )
}

private fun String.toDayQuickLaunchLabel(): String {
    val trimmed = trim().ifBlank { return "Open app" }
    return if (trimmed.startsWith("open", ignoreCase = true) ||
        trimmed.startsWith("launch", ignoreCase = true)
    ) {
        trimmed
    } else {
        "Open $trimmed"
    }
}

private fun MedicationPlan.toDayQuickMedicationItems(selectedDate: LocalDate): List<DayQuickItemUiModel> {
    if (!isActive) return emptyList()
    val startDate = startAt?.toLocalDate() ?: selectedDate
    if (selectedDate.isBefore(startDate)) return emptyList()
    val endDate = endAt?.toLocalDate()
    if (endDate != null && selectedDate.isAfter(endDate)) return emptyList()
    val schedule = schedule
    if (schedule?.isPrn == true || schedule?.recurrence?.type == PlannerRecurrenceType.PRN) return emptyList()
    if (schedule?.pausedUntil?.let { !it.isBefore(selectedDate) } == true) return emptyList()
    val recurrence = schedule?.recurrence ?: PlannerRecurrence()
    if (!recurrenceOccursOn(selectedDate, recurrence, startDate)) return emptyList()
    val doseTimes = recurrence.normalizedTimesOfDayMinutes
        .ifEmpty { listOf(normalizeDayQuickMinute(reminderMinuteOfDay)) }
    return doseTimes.map { minute ->
        val doseEvent = recentDoseEvents.firstTerminalDoseEvent(selectedDate, minute)
        val status = when (doseEvent?.type) {
            MedicationDoseEventType.TAKEN -> "Taken"
            MedicationDoseEventType.MISSED -> "Missed"
            MedicationDoseEventType.SKIPPED -> "Skipped"
            else -> "Due ${formatDayQuickMinute(minute)}"
        }
        DayQuickItemUiModel(
            id = "$id:$minute",
            kind = DayQuickItemKind.MEDICATION,
            title = name.ifBlank { "Medication" },
            detail = medicationQuickDetail(this),
            status = status,
            isDone = doseEvent != null,
            scheduledMinuteOfDay = minute
        )
    }
}

private fun List<MedicationDoseEvent>.firstTerminalDoseEvent(
    selectedDate: LocalDate,
    scheduledMinute: Int
): MedicationDoseEvent? = firstOrNull { event ->
    event.eventDate == selectedDate &&
        event.type in terminalMedicationEvents &&
        normalizeDayQuickMinute(event.scheduledMinuteOfDay ?: scheduledMinute) == scheduledMinute
}

private val terminalMedicationEvents = setOf(
    MedicationDoseEventType.TAKEN,
    MedicationDoseEventType.MISSED,
    MedicationDoseEventType.SKIPPED
)

private fun medicationQuickDetail(plan: MedicationPlan): String = buildList {
    add("${plan.dosage} ${plan.unit}".trim())
    if (plan.takeWithFood) add("with food")
    plan.safetyProfile?.takeIf { it.refillSoon }?.let { add("refill soon") }
}.joinToString(" - ")

private fun recurrenceOccursOn(
    date: LocalDate,
    recurrence: PlannerRecurrence,
    startDate: LocalDate
): Boolean = when (recurrence.type) {
    PlannerRecurrenceType.DAILY,
    PlannerRecurrenceType.MULTIPLE_TIMES_DAILY -> true
    PlannerRecurrenceType.WEEKDAYS -> date.dayOfWeek in weekdaySet
    PlannerRecurrenceType.WEEKENDS -> date.dayOfWeek in weekendSet
    PlannerRecurrenceType.SELECTED_WEEKDAYS -> recurrence.weekdays.isEmpty() || date.dayOfWeek in recurrence.weekdays
    PlannerRecurrenceType.EVERY_N_DAYS -> {
        val days = ChronoUnit.DAYS.between(startDate, date)
        days >= 0 && days % recurrence.interval.coerceAtLeast(1) == 0L
    }
    PlannerRecurrenceType.WEEKLY_INTERVAL -> {
        val allowedDays = recurrence.weekdays.ifEmpty { setOf(startDate.dayOfWeek) }
        val weeks = ChronoUnit.WEEKS.between(startDate.weekStart(), date.weekStart())
        weeks >= 0 && weeks % recurrence.interval.coerceAtLeast(1) == 0L && date.dayOfWeek in allowedDays
    }
    PlannerRecurrenceType.PRN -> false
}

private val weekdaySet = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)

private val weekendSet = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun LocalDate.weekStart(): LocalDate =
    minusDays((dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

private fun normalizeDayQuickMinute(minute: Int): Int =
    ((minute % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES

private fun formatDayQuickMinute(minute: Int): String {
    val normalized = normalizeDayQuickMinute(minute)
    val hour = normalized / 60
    val m = normalized % 60
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return "%d:%02d %s".format(displayHour, m, suffix)
}

private fun formatDayQuickDuration(durationMinutes: Int): String {
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${durationMinutes}m"
    }
}
