package com.ChronosFlow.VBCR.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Set on folded-reminder action intents so receivers refresh the live block surface instead of cancelling it. */
const val EXTRA_REFRESH_CURRENT_BLOCK = "com.ChronosFlow.VBCR.extra.REFRESH_CURRENT_BLOCK"

/** Action label for the primary folded reminder chip on the current-block live notification. */
internal fun foldedReminderActionLabel(context: Context, reminder: FoldedReminder): String =
    when (reminder.kind) {
        FoldedReminderKind.MEDICATION -> context.getString(R.string.folded_reminder_action_take)
        FoldedReminderKind.TASK -> context.getString(R.string.folded_reminder_action_complete)
        FoldedReminderKind.HABIT -> context.getString(R.string.folded_reminder_action_mark)
    }

/** Icon for the folded-reminder action button — one per kind, matching the reminder banners. */
internal fun foldedReminderActionIcon(reminder: FoldedReminder): Int = when (reminder.kind) {
    FoldedReminderKind.MEDICATION -> R.drawable.ic_notif_medication
    FoldedReminderKind.TASK -> R.drawable.ic_notif_task
    FoldedReminderKind.HABIT -> R.drawable.ic_notif_habit
}

/**
 * Pending intent for the primary folded reminder on the live "now" surface. Uses
 * [EXTRA_REFRESH_CURRENT_BLOCK] so the action receiver re-renders the notification after mutating data.
 */
internal fun buildFoldedReminderActionPendingIntent(
    context: Context,
    reminder: FoldedReminder,
    requestCode: Int
): PendingIntent? {
    val intent = when (reminder.kind) {
        FoldedReminderKind.MEDICATION -> Intent(context, MedicationActionReceiver::class.java).apply {
            action = MedicationActionReceiver.ACTION_TAKE
            putExtra(MedicationActionReceiver.EXTRA_MEDICATION_PLAN_ID, reminder.entityId)
        }
        FoldedReminderKind.TASK -> Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_COMPLETE
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, reminder.entityId)
        }
        FoldedReminderKind.HABIT -> Intent(context, HabitActionReceiver::class.java).apply {
            action = HabitActionReceiver.ACTION_COMPLETE
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, reminder.entityId)
        }
    }
    intent.putExtra(EXTRA_REFRESH_CURRENT_BLOCK, true)
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** Header subtext for reminder-only mode, e.g. "Reminder · now" or "3 reminders due". */
internal fun foldedReminderSubText(context: Context, reminders: List<FoldedReminder>): String {
    if (reminders.isEmpty()) return context.getString(R.string.folded_reminder_subtext_now)
    if (reminders.size > 1) {
        return context.getString(R.string.folded_reminder_subtext_count, reminders.size)
    }
    val primary = reminders.first()
    return if (primary.isOverdue) {
        context.getString(R.string.folded_reminder_subtext_overdue)
    } else {
        context.getString(R.string.folded_reminder_subtext_now)
    }
}

/** Title for reminder-only mode; redaction drops entity names. */
internal fun foldedReminderTitle(
    context: Context,
    reminders: List<FoldedReminder>,
    redact: Boolean
): String {
    if (reminders.isEmpty()) return context.getString(R.string.folded_reminder_generic_title)
    if (redact) return context.getString(R.string.folded_reminder_generic_title)
    return reminders.first().title.ifBlank { context.getString(R.string.folded_reminder_generic_title) }
}

/** Body for reminder-only mode — primary detail plus a "+N more" suffix when capped. */
internal fun foldedReminderBody(reminders: List<FoldedReminder>, redact: Boolean): String {
    if (reminders.isEmpty()) return ""
    val primary = reminders.first()
    val chip = if (redact) {
        when (primary.kind) {
            FoldedReminderKind.MEDICATION -> "Dose due"
            FoldedReminderKind.TASK -> "Task due"
            FoldedReminderKind.HABIT -> "Habit due"
        }
    } else {
        primary.detail
    }
    val extra = if (reminders.size > 1) " · +${reminders.size - 1} more" else ""
    return chip + extra
}

/** Appends the primary folded reminder as a trailing chip on an active/up-next body line. */
internal fun appendFoldedRemindersToBody(
    base: String,
    reminders: List<FoldedReminder>,
    redact: Boolean
): String {
    if (reminders.isEmpty()) return base
    val chip = foldedReminderBody(reminders, redact)
    return "$base · $chip"
}

internal fun foldedReminderActionRequestCode(reminder: FoldedReminder): Int =
    (FOLDED_ACTION_REQUEST_BASE + reminder.kind.ordinal + reminder.entityId.hashCode()) and Int.MAX_VALUE

/** Up to [maxFoldedActions] actionable chips; extras beyond that stay text-only in the body. */
internal data class FoldedActionSlots(
    val primaryIntent: PendingIntent?,
    val primaryLabel: String?,
    val primaryIcon: Int?,
    val secondaryIntent: PendingIntent?,
    val secondaryLabel: String?,
    val secondaryIcon: Int?
)

internal fun buildFoldedActionSlots(
    context: Context,
    folded: List<FoldedReminder>,
    maxFoldedActions: Int
): FoldedActionSlots {
    if (folded.isEmpty() || maxFoldedActions <= 0) {
        return FoldedActionSlots(null, null, null, null, null, null)
    }
    val primary = folded.first()
    val secondary = folded.getOrNull(1)?.takeIf { maxFoldedActions >= 2 }
    return FoldedActionSlots(
        primaryIntent = buildFoldedReminderActionPendingIntent(
            context,
            primary,
            foldedReminderActionRequestCode(primary)
        ),
        primaryLabel = foldedReminderActionLabel(context, primary),
        primaryIcon = foldedReminderActionIcon(primary),
        secondaryIntent = secondary?.let {
            buildFoldedReminderActionPendingIntent(
                context,
                it,
                foldedReminderActionRequestCode(it)
            )
        },
        secondaryLabel = secondary?.let { foldedReminderActionLabel(context, it) },
        secondaryIcon = secondary?.let { foldedReminderActionIcon(it) }
    )
}

private const val FOLDED_ACTION_REQUEST_BASE = 43_000
