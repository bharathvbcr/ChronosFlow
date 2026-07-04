package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import android.util.Log
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Per-kind gate for the folded-reminder fold — each flag mirrors iOS `ChronosSettings` reminder toggles
 * (`notif.medication`, `notif.tasks`, `notif.habits`) stored in the shared preferences namespace.
 */
data class FoldToggles(
    val medication: Boolean = true,
    val task: Boolean = true,
    val habit: Boolean = true
) {
    companion object {
        val ALL = FoldToggles()

        /** Reads iOS-compatible reminder toggles from the shared preferences store (defaults ON). */
        fun fromPreferences(context: Context): FoldToggles {
            val prefs = context.getSharedPreferences(CHRONOS_PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_REMINDERS_ENABLED, true)) {
                return FoldToggles(medication = false, task = false, habit = false)
            }
            return FoldToggles(
                medication = prefs.getBoolean(KEY_MEDICATION_REMINDERS, true),
                task = prefs.getBoolean(KEY_TASK_REMINDERS, true),
                habit = prefs.getBoolean(KEY_HABIT_REMINDERS, true)
            )
        }

        private const val CHRONOS_PREFERENCES_NAME = "chronos_preferences"
        private const val KEY_REMINDERS_ENABLED = "notif.reminders"
        private const val KEY_MEDICATION_REMINDERS = "notif.medication"
        private const val KEY_TASK_REMINDERS = "notif.tasks"
        private const val KEY_HABIT_REMINDERS = "notif.habits"
    }
}

/**
 * Resolves the folded reminders to surface on the current-block live notification: reads today's medication
 * plans, tasks, and habits from the repositories, runs the pure [FoldedReminder] builders, ranks them
 * (medication → task → habit, most-overdue first) and returns the top few. Each repo read is defensively
 * wrapped — a failure yields no reminders for that kind rather than breaking the notification refresh,
 * matching the coordinator's style.
 */
@Singleton
class FoldedReminderResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val medicationRepository: MedicationRepository,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository
) {
    suspend fun resolve(
        now: LocalDateTime,
        zoneId: ZoneId,
        enabled: FoldToggles = FoldToggles.fromPreferences(context)
    ): List<FoldedReminder> {
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute
        val candidates = buildList {
            if (enabled.medication) {
                addAll(buildMedicationFoldedReminders(loadMedicationPlans(), today, nowMinute))
            }
            if (enabled.task) {
                addAll(buildTaskFoldedReminders(loadTasks(), today, nowMinute, zoneId))
            }
            if (enabled.habit) {
                addAll(buildHabitFoldedReminders(loadHabits(), today, nowMinute))
            }
        }
        return rankFoldedReminders(candidates)
    }

    private suspend fun loadMedicationPlans() = try {
        medicationRepository.observeMedicationPlans().first()
    } catch (ex: Exception) {
        Log.w(TAG, "Failed to load medication plans for folded reminders", ex)
        emptyList()
    }

    private suspend fun loadTasks() = try {
        taskRepository.getAllTasks().first()
    } catch (ex: Exception) {
        Log.w(TAG, "Failed to load tasks for folded reminders", ex)
        emptyList()
    }

    private suspend fun loadHabits() = try {
        habitRepository.observeHabits().first()
    } catch (ex: Exception) {
        Log.w(TAG, "Failed to load habits for folded reminders", ex)
        emptyList()
    }

    private companion object {
        const val TAG = "FoldedReminderResolver"
    }
}
