package com.chronosflow.appfunctions

import android.content.Intent
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import com.chronosflow.core.domain.model.ActualTimeSegment
import com.chronosflow.core.domain.model.ActualTimeSource
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.HabitEvent
import com.chronosflow.core.domain.model.HabitEventType
import com.chronosflow.core.domain.model.MedicationDoseEvent
import com.chronosflow.core.domain.model.MedicationDoseEventType
import com.chronosflow.core.domain.model.MoodEnergyCheckIn
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.repository.CalendarEventRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import com.chronosflow.core.domain.repository.MoodEnergyRepository
import com.chronosflow.core.domain.repository.ReviewRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.feature.focus.FocusService
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exposes core ChronosFlow planner capabilities as AppFunctions for AI assistants.
 */
@Singleton
class ChronosAppFunctions @Inject constructor(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val medicationRepository: MedicationRepository,
    private val moodEnergyRepository: MoodEnergyRepository,
    private val timeBlockRepository: TimeBlockRepository,
    private val reviewRepository: ReviewRepository,
    private val calendarEventRepository: CalendarEventRepository
) {

    /**
     * Creates a new task in ChronosFlow.
     * Call this when the user asks to schedule, remember, or add a task or to-do.
     *
     * @param title The title of the task.
     * @param description Optional description or notes for the task.
     * @param priority Optional priority level (higher numbers indicate higher priority).
     * @param preferredDurationMinutes Optional expected duration of the task in minutes.
     * @return True if the task was successfully created, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createTask(
        appFunctionContext: AppFunctionContext,
        title: String? = null,
        description: String? = null,
        priority: Int? = null,
        preferredDurationMinutes: Int? = null
    ): Boolean {
        return runAppFunction {
            val normalizedTitle = title.requiredText() ?: return@runAppFunction false
            val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedDuration = preferredDurationMinutes?.takeIf { it in 1..1440 }
            val now = Instant.now()
            val task = Task(
                id = UUID.randomUUID().toString(),
                title = normalizedTitle,
                description = normalizedDescription,
                isCompleted = false,
                priority = priority?.coerceIn(0, 5) ?: 1,
                dueDate = null,
                createdAt = now,
                updatedAt = now,
                preferredDurationMinutes = normalizedDuration,
                preferredStartMinuteOfDay = null,
                targetDate = LocalDate.now(),
                checklist = emptyList(),
                linkedContact = null,
                actions = emptyList(),
                attachments = emptyList()
            )
            withContext(Dispatchers.IO) {
                taskRepository.saveTask(task)
            }
            true
        }
    }

    /**
     * Starts an active focus session in ChronosFlow.
     * Call this when the user wants to start working, focusing, or start a Pomodoro/timer.
     *
     * @param durationMinutes Optional duration of the focus session in minutes.
     * @return True if the focus session was successfully initiated, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun startFocusSession(
        appFunctionContext: AppFunctionContext,
        durationMinutes: Int? = null
    ): Boolean {
        return runAppFunction {
            val context = appFunctionContext.context
            val durationSeconds = durationMinutes?.takeIf { it in 1..1440 }?.let { it * 60 }
            if (durationMinutes != null && durationSeconds == null) return@runAppFunction false
            val intent = Intent(context, FocusService::class.java).apply {
                action = FocusService.ACTION_START
                if (durationSeconds != null) {
                    putExtra(FocusService.EXTRA_TOTAL_SECONDS, durationSeconds)
                }
            }
            context.startForegroundService(intent)
            true
        }
    }

    /**
     * Logs a habit completion event.
     * Call this when the user completes or performs a habit/routine.
     *
     * @param habitId The ID of the habit that was completed.
     * @param reason Optional note or reflection for the habit completion.
     * @return True if the habit event was successfully recorded, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun logHabitCompleted(
        appFunctionContext: AppFunctionContext,
        habitId: String? = null,
        reason: String? = null
    ): Boolean {
        return runAppFunction {
            val normalizedHabitId = habitId.requiredText() ?: return@runAppFunction false
            val now = Instant.now()
            val event = HabitEvent(
                id = UUID.randomUUID().toString(),
                habitId = normalizedHabitId,
                type = HabitEventType.COMPLETED,
                eventDate = LocalDate.now(),
                recordedAt = now,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                startMinuteOfDay = null,
                endMinuteOfDay = null
            )
            withContext(Dispatchers.IO) {
                habitRepository.addHabitEvent(event)
            }
            true
        }
    }

    /**
     * Logs a habit skipped event.
     * Call this when the user decides to skip or defer a habit/routine today.
     *
     * @param habitId The ID of the habit that was skipped.
     * @param reason The reason why the habit is being skipped.
     * @return True if the skip event was successfully recorded, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun logHabitSkipped(
        appFunctionContext: AppFunctionContext,
        habitId: String? = null,
        reason: String? = null
    ): Boolean {
        return runAppFunction {
            val normalizedHabitId = habitId.requiredText() ?: return@runAppFunction false
            val now = Instant.now()
            val event = HabitEvent(
                id = UUID.randomUUID().toString(),
                habitId = normalizedHabitId,
                type = HabitEventType.SKIPPED,
                eventDate = LocalDate.now(),
                recordedAt = now,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                startMinuteOfDay = null,
                endMinuteOfDay = null
            )
            withContext(Dispatchers.IO) {
                habitRepository.addHabitEvent(event)
            }
            true
        }
    }

    /**
     * Logs that a medication dose was taken.
     * Call this when the user takes their medication.
     *
     * @param medicationPlanId The ID of the medication plan.
     * @param doseAmount Optional amount/dose taken (e.g. "1 tablet", "5ml").
     * @param reason Optional notes or reason for taking (especially for PRN/as-needed medications).
     * @return True if the event was successfully logged, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun logMedicationTaken(
        appFunctionContext: AppFunctionContext,
        medicationPlanId: String? = null,
        doseAmount: String? = null,
        reason: String? = null
    ): Boolean {
        return runAppFunction {
            val normalizedPlanId = medicationPlanId.requiredText() ?: return@runAppFunction false
            val now = Instant.now()
            val event = MedicationDoseEvent(
                id = UUID.randomUUID().toString(),
                medicationPlanId = normalizedPlanId,
                type = MedicationDoseEventType.TAKEN,
                eventDate = LocalDate.now(),
                recordedAt = now,
                scheduledMinuteOfDay = null,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                doseAmount = doseAmount?.trim()?.takeIf { it.isNotEmpty() }
            )
            withContext(Dispatchers.IO) {
                medicationRepository.addMedicationDoseEvent(event)
            }
            true
        }
    }

    /**
     * Logs that a medication dose was skipped.
     * Call this when the user skips or refuses a scheduled medication.
     *
     * @param medicationPlanId The ID of the medication plan.
     * @param reason The reason why the medication was skipped.
     * @return True if the event was successfully logged, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun logMedicationSkipped(
        appFunctionContext: AppFunctionContext,
        medicationPlanId: String? = null,
        reason: String? = null
    ): Boolean {
        return runAppFunction {
            val normalizedPlanId = medicationPlanId.requiredText() ?: return@runAppFunction false
            val now = Instant.now()
            val event = MedicationDoseEvent(
                id = UUID.randomUUID().toString(),
                medicationPlanId = normalizedPlanId,
                type = MedicationDoseEventType.SKIPPED,
                eventDate = LocalDate.now(),
                recordedAt = now,
                scheduledMinuteOfDay = null,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                doseAmount = null
            )
            withContext(Dispatchers.IO) {
                medicationRepository.addMedicationDoseEvent(event)
            }
            true
        }
    }

    /**
     * Logs a mood and energy check-in.
     * Call this when the user wants to log, record, or check-in their emotional state, energy, focus, or stress levels.
     *
     * @param moodScore Mood score from 1 (terrible) to 10 (excellent).
     * @param energyScore Energy level score from 1 (completely drained) to 10 (highly energetic).
     * @param stressScore Stress level score from 1 (no stress) to 10 (extreme stress).
     * @param focusScore Focus level score from 1 (highly distracted) to 10 (extreme focus).
     * @param notes Optional personal notes, feelings, or context for the check-in.
     * @return True if the check-in was successfully recorded, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun logMoodEnergyCheckIn(
        appFunctionContext: AppFunctionContext,
        moodScore: Int? = null,
        energyScore: Int? = null,
        stressScore: Int? = null,
        focusScore: Int? = null,
        notes: String? = null
    ): Boolean {
        return runAppFunction {
            val normalizedMood = moodScore?.takeIfScore() ?: return@runAppFunction false
            val normalizedEnergy = energyScore?.takeIfScore() ?: return@runAppFunction false
            val normalizedStress = stressScore?.takeIfScore() ?: return@runAppFunction false
            val normalizedFocus = focusScore?.takeIfScore() ?: return@runAppFunction false
            val now = LocalDateTime.now()
            val checkIn = MoodEnergyCheckIn(
                id = UUID.randomUUID().toString(),
                blockId = null,
                moodScore = normalizedMood,
                stressScore = normalizedStress,
                energyScore = normalizedEnergy,
                focusScore = normalizedFocus,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                recordedAt = now,
                checkInDate = LocalDate.now()
            )
            withContext(Dispatchers.IO) {
                moodEnergyRepository.save(checkIn)
            }
            true
        }
    }

    /**
     * Schedules a planned time block on the circular day planner timeline (Chronos Dial).
     * Call this when the user wants to schedule an activity, block of time, work session, study time, break, routine, or sleep block for a specific time and duration.
     *
     * @param title The title of the scheduled block (e.g. "Project Work", "Lunch Break", "Morning Yoga").
     * @param category The category of the block (e.g. "work", "study", "meals", "breaks", "routines", "sleep").
     * @param startMinuteOfDay The minute of the day when the block starts (0 to 1439, where 0 is midnight, 480 is 8:00 AM, 720 is noon, 840 is 2:00 PM, etc.).
     * @param durationMinutes The duration of the block in minutes (e.g. 60, 90, 120).
     * @param dateOptional Optional date of the block in YYYY-MM-DD format. Defaults to today's date if not specified.
     * @param flexibilityOptional Optional flexibility of the block. Allowed values: "FIXED", "MOVABLE", "RESIZABLE", "OPTIONAL". Defaults to "MOVABLE".
     * @param energyLevelOptional Optional energy intensity level from 1 (lowest energy/relaxation) to 5 (highest intensity focus). Defaults to 2 (moderate).
     * @return True if the block was successfully scheduled on the planner dial, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addTimeBlock(
        appFunctionContext: AppFunctionContext,
        title: String? = null,
        category: String? = null,
        startMinuteOfDay: Int? = null,
        durationMinutes: Int? = null,
        dateOptional: String? = null,
        flexibilityOptional: String? = null,
        energyLevelOptional: Int? = null
    ): Boolean {
        return runAppFunction {
            val normalizedTitle = title.requiredText() ?: return@runAppFunction false
            val normalizedCategory = category.requiredText() ?: return@runAppFunction false
            val normalizedStart = startMinuteOfDay?.takeIf { it in 0..1439 }
                ?: return@runAppFunction false
            val normalizedDuration = durationMinutes?.takeIf { it in 1..1440 }
                ?: return@runAppFunction false
            val targetDate = if (dateOptional != null) {
                LocalDate.parse(dateOptional)
            } else {
                LocalDate.now()
            }

            val flex = when (flexibilityOptional?.uppercase()) {
                "FIXED" -> BlockFlexibility.FIXED
                "RESIZABLE" -> BlockFlexibility.RESIZABLE
                "OPTIONAL" -> BlockFlexibility.OPTIONAL
                else -> BlockFlexibility.MOVABLE
            }

            val energy = EnergyIntensity.fromLevel(energyLevelOptional ?: 2)
            val now = Instant.now()

            val timeBlock = TimeBlock(
                id = UUID.randomUUID().toString(),
                date = targetDate,
                title = normalizedTitle,
                category = normalizedCategory,
                startMinuteOfDay = normalizedStart,
                durationMinutes = normalizedDuration,
                timezone = java.util.TimeZone.getDefault().id,
                provenance = BlockProvenance.USER_CREATED,
                flexibility = flex,
                energyLevel = energy,
                source = "appfunctions",
                taskId = null,
                calendarEventId = null,
                medicationPlanId = null,
                habitId = null,
                isLocked = false,
                isProtected = false,
                recurrenceRuleId = null,
                actualStartMinuteOfDay = null,
                actualEndMinuteOfDay = null,
                createdAt = now,
                updatedAt = now
            )
            withContext(Dispatchers.IO) {
                timeBlockRepository.saveTimeBlock(timeBlock)
            }
            true
        }
    }

    /**
     * Logs actual time spent on an activity (useful for planned versus actual comparison).
     * Call this when the user reports doing an activity, studying, working, or sleeping for a specific duration or window of time.
     *
     * @param activityTitle The title or description of the activity done.
     * @param startInstantIso The start date-time in ISO 8601 format (e.g. "2026-05-28T15:00:00Z").
     * @param endInstantIso The end date-time in ISO 8601 format (e.g. "2026-05-28T16:30:00Z").
     * @return True if the actual time segment was successfully recorded, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun logActualTimeSegment(
        appFunctionContext: AppFunctionContext,
        activityTitle: String? = null,
        startInstantIso: String? = null,
        endInstantIso: String? = null
    ): Boolean {
        return runAppFunction {
            if (activityTitle.requiredText() == null) return@runAppFunction false
            val normalizedStart = startInstantIso.requiredText() ?: return@runAppFunction false
            val normalizedEnd = endInstantIso.requiredText() ?: return@runAppFunction false
            val start = Instant.parse(normalizedStart)
            val end = Instant.parse(normalizedEnd)
            if (!end.isAfter(start)) return@runAppFunction false
            val segment = ActualTimeSegment(
                id = UUID.randomUUID().toString(),
                blockId = null,
                date = LocalDate.now(),
                startInstant = start,
                endInstant = end,
                source = ActualTimeSource.MANUAL_ENTRY,
                confidence = 1.0f
            )
            withContext(Dispatchers.IO) {
                reviewRepository.saveActualTimeSegment(segment)
            }
            true
        }
    }

    /**
     * Synchronizes and imports calendar events from the Android device's local calendar for the next 7 days.
     * Call this when the user requests to sync, update, refresh, or import their calendar events or schedule.
     *
     * @return True if the synchronization was successfully initiated, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun syncCalendarEvents(
        appFunctionContext: AppFunctionContext
    ): Boolean {
        return runAppFunction {
            val now = Instant.now()
            val end = now.plus(java.time.Duration.ofDays(7))
            withContext(Dispatchers.IO) {
                calendarEventRepository.syncFromDeviceCalendar(now, end)
            }
            true
        }
    }

    private suspend fun runAppFunction(block: suspend () -> Boolean): Boolean =
        try {
            block()
        } catch (e: Exception) {
            false
        }

    private fun String?.requiredText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun Int.takeIfScore(): Int? = takeIf { it in 1..10 }
}
