package com.ChronosFlow.VBCR.appfunctions

import android.content.Intent
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSource
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.model.Goal
import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.CaptureSource
import com.ChronosFlow.VBCR.core.domain.model.InboxItem
import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyCheckIn
import com.ChronosFlow.VBCR.core.domain.model.ReadingItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingMetadataState
import com.ChronosFlow.VBCR.core.domain.model.ReadingUrls
import com.ChronosFlow.VBCR.core.domain.model.SleepSource
import com.ChronosFlow.VBCR.core.domain.model.SleepTrack
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.domain.repository.CalendarEventRepository
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import com.ChronosFlow.VBCR.core.domain.repository.GoalRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.InboxRepository
import com.ChronosFlow.VBCR.core.domain.repository.JournalRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.MoodEnergyRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReadingListRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import com.ChronosFlow.VBCR.core.domain.repository.RoutineRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepTrackRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.data.focus.ManualMissedBlockRegistry
import com.ChronosFlow.VBCR.core.data.reading.ReadingMetadataFetchWorker
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.readChronosUiBooleanSetting
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.ChronosFlow.VBCR.core.domain.usecase.ApplyRoutineToDateUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.RecordSleepUseCase
import com.ChronosFlow.VBCR.feature.focus.FocusService
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    private val calendarEventRepository: CalendarEventRepository,
    private val goalRepository: GoalRepository,
    private val journalRepository: JournalRepository,
    private val readingListRepository: ReadingListRepository,
    private val inboxRepository: InboxRepository,
    private val routineRepository: RoutineRepository,
    private val sleepTrackRepository: SleepTrackRepository,
    private val recordSleepUseCase: RecordSleepUseCase,
    private val applyRoutineToDateUseCase: ApplyRoutineToDateUseCase,
    private val timeBlockCompletionHandler: TimeBlockCompletionHandler,
    private val plannerService: PlannerService,
    private val focusSessionRepository: FocusSessionRepository,
    private val manualMissedBlockRegistry: ManualMissedBlockRegistry,
    private val featureFlagsSource: ChronosFeatureFlagsSource
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
     * Saves a link to the ChronosFlow reading list ("read later").
     * Call this when the user wants to save, bookmark, or remember an article, page, or link to read later.
     *
     * @param url The link to save (an http/https URL).
     * @param title Optional title for the saved link; the site domain is used when omitted.
     * @return True if the link was saved, false if no valid URL was provided.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addToReadingList(
        appFunctionContext: AppFunctionContext,
        url: String? = null,
        title: String? = null
    ): Boolean {
        return runAppFunction {
            val resolvedUrl = ReadingUrls.firstUrlIn(url) ?: return@runAppFunction false
            val context = appFunctionContext.context
            val domain = ReadingUrls.domainOf(resolvedUrl)
            val autoFetch = context.readChronosUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_READING_METADATA_AUTOFETCH, true
            )
            val now = Instant.now()
            val id = UUID.randomUUID().toString()
            withContext(Dispatchers.IO) {
                readingListRepository.save(
                    ReadingItem(
                        id = id,
                        url = resolvedUrl,
                        title = title?.trim()?.takeIf { it.isNotEmpty() } ?: domain,
                        domain = domain,
                        metadataState = if (autoFetch) ReadingMetadataState.PENDING else ReadingMetadataState.SKIPPED,
                        addedAt = now,
                        updatedAt = now
                    )
                )
            }
            if (autoFetch) {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ReadingMetadataFetchWorker.uniqueName(id),
                    ExistingWorkPolicy.REPLACE,
                    ReadingMetadataFetchWorker.request(id)
                )
            }
            true
        }
    }

    /**
     * Captures a quick note or link into the ChronosFlow inbox for later triage.
     * Call this when the user wants to quickly jot down, capture, or remember a thought to deal with later.
     *
     * @param text The note or link to capture.
     * @return True if the item was captured, false if the text was empty.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun captureToInbox(
        appFunctionContext: AppFunctionContext,
        text: String? = null
    ): Boolean {
        return runAppFunction {
            val normalized = text.requiredText() ?: return@runAppFunction false
            val now = Instant.now()
            withContext(Dispatchers.IO) {
                inboxRepository.save(
                    InboxItem(
                        id = UUID.randomUUID().toString(),
                        text = normalized,
                        url = ReadingUrls.firstUrlIn(normalized),
                        source = CaptureSource.SHARE,
                        createdAt = now
                    )
                )
            }
            true
        }
    }

    /**
     * Starts an active focus session in ChronosFlow.
     * Call this when the user wants to start working, focusing, or start a Pomodoro/timer.
     *
     * The session is persisted immediately so it is created even when the app is in the
     * background (the typical case for an agent-initiated call). The live countdown
     * notification appears as soon as the app can run a foreground service; if the system
     * blocks a background foreground-service start, the session is still recorded and the
     * timer (anchored to wall-clock time) surfaces when the app is next opened.
     *
     * @param durationMinutes Optional duration of the focus session in minutes (1..1440). Defaults to 25.
     * @return True if the focus session was successfully created, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun startFocusSession(
        appFunctionContext: AppFunctionContext,
        durationMinutes: Int? = null
    ): Boolean {
        return runAppFunction {
            val context = appFunctionContext.context
            val minutes = when {
                durationMinutes == null -> DEFAULT_FOCUS_MINUTES
                durationMinutes in 1..1440 -> durationMinutes
                else -> return@runAppFunction false
            }
            val durationSeconds = minutes * 60
            val sessionId = UUID.randomUUID().toString()
            val now = Instant.now()
            withContext(Dispatchers.IO) {
                focusSessionRepository.saveFocusSession(
                    FocusSessionState.Running(
                        sessionId = sessionId,
                        blockId = null,
                        startedAt = now,
                        plannedEndAt = now.plusSeconds(durationSeconds.toLong())
                    )
                )
            }
            // Best-effort live foreground service. Succeeds when the app may start an FGS
            // (e.g. it is foregrounded); a background-start block is expected and harmless
            // here because the session above is already persisted and recoverable.
            val intent = Intent(context, FocusService::class.java).apply {
                action = FocusService.ACTION_START
                putExtra(FocusService.EXTRA_SESSION_ID, sessionId)
                putExtra(FocusService.EXTRA_TOTAL_SECONDS, durationSeconds)
                putExtra(FocusService.EXTRA_TIME_LEFT_SECONDS, durationSeconds)
            }
            runCatching { context.startForegroundService(intent) }
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
            if (!featureEnabled { it.habitsEnabled }) return@runAppFunction false
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
            if (!featureEnabled { it.habitsEnabled }) return@runAppFunction false
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
            if (!featureEnabled { it.medicationEnabled }) return@runAppFunction false
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
            if (!featureEnabled { it.medicationEnabled }) return@runAppFunction false
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
                date = start.atZone(ZoneId.systemDefault()).toLocalDate(),
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
     * Marks an existing planned time block as completed.
     * Call this when the user says they finished, completed, or did a block that is already on
     * their plan — especially when they forgot to start its focus timer and just want it counted.
     *
     * Unlike [logActualTimeSegment], which records a free-floating activity, this links the
     * completion to a specific planned block and fills its full planned window as actual time, so
     * the daily review counts the block as done. Calling it again on an already-completed block is
     * a safe no-op (no duplicate time is logged).
     *
     * Identify the block by either [blockId] (from [getTodaySchedule], most precise) or [blockTitle]
     * for natural voice use (e.g. "I finished my deep work block"). When only a title is given, a
     * matching block on today's plan that is not yet completed is preferred.
     *
     * @param blockId The ID of the planned time block to mark complete (takes precedence).
     * @param blockTitle The title of a block on today's plan to mark complete, if no id is given.
     * @return True if the block is now (or was already) complete, false if no block matched.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun completeTimeBlock(
        appFunctionContext: AppFunctionContext,
        blockId: String? = null,
        blockTitle: String? = null
    ): Boolean {
        return runAppFunction {
            withContext(Dispatchers.IO) {
                val block = resolveTimeBlock(blockId, blockTitle)
                    ?: return@withContext false
                // Same path as the in-app action and the watch's complete-block action: logs the
                // planned window (idempotent), advances any recurring task + alarms on a fresh
                // completion, and clears a stale missed flag.
                timeBlockCompletionHandler.complete(block)
                true
            }
        }
    }

    /**
     * Marks a planned time block as missed (skipped or not done).
     * Call this when the user says they skipped, missed, or did not get to a block on their plan.
     * This is the inverse of [completeTimeBlock]; the block then surfaces as missed in the day
     * review.
     *
     * Identify the block by either [blockId] (from [getTodaySchedule], most precise) or [blockTitle]
     * for natural voice use (e.g. "I skipped my workout"). When only a title is given, a matching
     * block on today's plan that is not yet completed is preferred.
     *
     * @param blockId The ID of the planned time block to mark missed (takes precedence).
     * @param blockTitle The title of a block on today's plan to mark missed, if no id is given.
     * @return True if the block was marked missed, false if no block matched.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun markTimeBlockMissed(
        appFunctionContext: AppFunctionContext,
        blockId: String? = null,
        blockTitle: String? = null
    ): Boolean {
        return runAppFunction {
            withContext(Dispatchers.IO) {
                val block = resolveTimeBlock(blockId, blockTitle)
                    ?: return@withContext false
                manualMissedBlockRegistry.markMissed(block.id, block.date)
                true
            }
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

    /**
     * Creates a new long-term goal in ChronosFlow.
     * Call this when the user wants to set, add, or track a goal, objective, or target they want to work towards.
     *
     * @param title The title of the goal (e.g. "Read 12 books", "Run a marathon").
     * @param description Optional description, motivation, or notes for the goal.
     * @param targetValueOptional Optional numeric target to reach (e.g. 12 for "read 12 books"). Defaults to 1.
     * @param targetDateOptional Optional target completion date in YYYY-MM-DD format.
     * @return True if the goal was successfully created, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createGoal(
        appFunctionContext: AppFunctionContext,
        title: String? = null,
        description: String? = null,
        targetValueOptional: Int? = null,
        targetDateOptional: String? = null
    ): Boolean {
        return runAppFunction {
            if (!featureEnabled { it.goalsEnabled }) return@runAppFunction false
            val normalizedTitle = title.requiredText() ?: return@runAppFunction false
            val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
            val goal = Goal(
                id = UUID.randomUUID().toString(),
                title = normalizedTitle,
                description = normalizedDescription,
                category = "Personal",
                targetValue = targetValueOptional?.coerceAtLeast(1) ?: 1,
                startDate = LocalDate.now(),
                targetDate = targetDateOptional?.let { LocalDate.parse(it) },
                progressValue = 0,
                isCompleted = false
            )
            withContext(Dispatchers.IO) {
                goalRepository.saveGoal(goal)
            }
            true
        }
    }

    /**
     * Adds a journal entry for today.
     * Call this when the user wants to journal, reflect, write down their thoughts, or record a diary entry.
     *
     * @param text The body text of the journal entry.
     * @param promptOptional Optional prompt type or reflection prompt that this entry responds to.
     * @return True if the journal entry was successfully saved, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addJournalEntry(
        appFunctionContext: AppFunctionContext,
        text: String? = null,
        promptOptional: String? = null
    ): Boolean {
        return runAppFunction {
            if (!featureEnabled { it.journalEnabled }) return@runAppFunction false
            val normalizedBody = text.requiredText() ?: return@runAppFunction false
            val now = Instant.now()
            val entry = JournalEntry(
                id = UUID.randomUUID().toString(),
                entryDate = LocalDate.now(),
                createdAt = now,
                updatedAt = now,
                body = normalizedBody,
                promptType = promptOptional?.trim()?.ifEmpty { null },
                moodCheckInId = null,
                isPrimary = true
            )
            withContext(Dispatchers.IO) {
                journalRepository.save(entry)
            }
            true
        }
    }

    /**
     * Logs last night's sleep for today.
     * Call this when the user reports how they slept, wants to record their sleep, bedtime, wake time, or sleep quality.
     *
     * @param quality Sleep quality rating from 1 (terrible) to 5 (excellent). Required.
     * @param bedTimeIso Optional time the user went to bed in 24-hour HH:mm format (e.g. "23:30").
     * @param wakeTimeIso Optional time the user woke up in 24-hour HH:mm format (e.g. "07:15").
     * @param interruptions Optional number of times sleep was interrupted during the night.
     * @param windDownNotes Optional notes about the wind-down routine or how the night went.
     * @return True if the sleep log was successfully recorded, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun logSleep(
        appFunctionContext: AppFunctionContext,
        quality: Int? = null,
        bedTimeIso: String? = null,
        wakeTimeIso: String? = null,
        interruptions: Int? = null,
        windDownNotes: String? = null
    ): Boolean {
        return runAppFunction {
            if (!featureEnabled { it.sleepEnabled }) return@runAppFunction false
            val normalizedQuality = quality?.coerceIn(1, 5) ?: return@runAppFunction false
            val actualStartMinute = bedTimeIso.requiredText()?.let {
                val time = LocalTime.parse(it)
                time.hour * 60 + time.minute
            }
            val actualEndMinute = wakeTimeIso.requiredText()?.let {
                val time = LocalTime.parse(it)
                time.hour * 60 + time.minute
            }
            val today = LocalDate.now()
            val normalizedNotes = windDownNotes?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedInterruptions = interruptions?.coerceAtLeast(0)
            withContext(Dispatchers.IO) {
                // Merge into any existing track for the day (the table has a UNIQUE date and the DAO
                // REPLACEs on conflict, so a blind insert would wipe earlier-recorded fields).
                val existing = sleepTrackRepository.observeForDate(today).first()
                val base = existing ?: SleepTrack(
                    id = UUID.randomUUID().toString(),
                    date = today,
                    plannedStartMinute = null,
                    plannedEndMinute = null,
                    actualStartMinute = null,
                    actualEndMinute = null,
                    sleepQuality = normalizedQuality,
                    windDownNotes = null,
                    interruptedCount = 0
                )
                recordSleepUseCase(
                    base.copy(
                        sleepQuality = normalizedQuality,
                        actualStartMinute = actualStartMinute ?: base.actualStartMinute,
                        actualEndMinute = actualEndMinute ?: base.actualEndMinute,
                        windDownNotes = normalizedNotes ?: base.windDownNotes,
                        interruptedCount = normalizedInterruptions ?: base.interruptedCount,
                        // User-driven log; claim ownership so Health Connect sync won't overwrite it.
                        source = SleepSource.MANUAL
                    )
                )
            }
            true
        }
    }

    /**
     * Applies a saved routine to a day, scheduling all of its steps as time blocks on the planner dial.
     * Call this when the user wants to start, run, apply, or schedule a named routine (e.g. "morning routine", "evening wind-down") for a day.
     *
     * @param routineName The name/title of the routine to apply (matched case-insensitively).
     * @param dayOptional Optional day to apply the routine to: "today", "tomorrow", or a date in YYYY-MM-DD format. Defaults to today.
     * @return True if a matching routine was found and at least one block was scheduled, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun applyRoutine(
        appFunctionContext: AppFunctionContext,
        routineName: String? = null,
        dayOptional: String? = null
    ): Boolean {
        return runAppFunction {
            val normalizedName = routineName.requiredText() ?: return@runAppFunction false
            val targetDate = when (dayOptional?.trim()?.lowercase()) {
                "tomorrow" -> LocalDate.now().plusDays(1)
                null, "", "today" -> LocalDate.now()
                else -> LocalDate.parse(dayOptional.trim())
            }
            withContext(Dispatchers.IO) {
                val routine = routineRepository.observeRoutines().first()
                    .firstOrNull { it.title.equals(normalizedName, ignoreCase = true) }
                    ?: return@withContext false
                // Routine step offsets are stored as absolute minute-of-day, so the anchor is 0.
                val created = applyRoutineToDateUseCase(routine.id, targetDate, 0)
                created >= 1
            }
        }
    }

    /**
     * Reflows the remaining part of the day's plan, pulling overdue and upcoming flexible blocks
     * forward from the current time to close gaps after the day has slipped behind schedule.
     * Call this when the user is behind schedule and wants to catch up, reorganize, rebalance, or
     * "fix" the rest of their day. Blocks already in progress or completed are left where they are,
     * and fixed or locked blocks are never moved.
     *
     * @param dayOptional Optional day to reflow: "today" or a date in YYYY-MM-DD format. Defaults to today. For a future date the whole day is repacked; for today only the time from now onward is reflowed.
     * @return True if the remaining day was reflowed successfully, false if there were no flexible blocks to reorganize.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun reflowRemainingDay(
        appFunctionContext: AppFunctionContext,
        dayOptional: String? = null
    ): Boolean {
        return runAppFunction {
            val today = LocalDate.now()
            val targetDate = when (dayOptional?.trim()?.lowercase()) {
                "tomorrow" -> today.plusDays(1)
                null, "", "today" -> today
                else -> LocalDate.parse(dayOptional.trim())
            }
            val fromMinute = if (targetDate == today) {
                LocalTime.now().let { it.hour * 60 + it.minute }
            } else {
                null
            }
            withContext(Dispatchers.IO) {
                plannerService.rebalanceDay(targetDate, fromMinute) is PlannerOperationResult.Applied
            }
        }
    }

    /**
     * Returns today's planned blocks from the circular day planner (Chronos Dial), ordered by start time.
     *
     * @param appFunctionContext The execution context.
     * @return The list of scheduled blocks for today; empty if nothing is planned.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getTodaySchedule(
        appFunctionContext: AppFunctionContext
    ): List<ScheduledBlock> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val missedIds = manualMissedBlockRegistry.missedIdsForDate(today, manualMissedBlockRegistry.ids.value)
        timeBlockRepository.getTimeBlocksByDate(today).first()
            .sortedBy { it.startMinuteOfDay }
            .map { block ->
                ScheduledBlock(
                    id = block.id,
                    title = block.title,
                    category = block.category,
                    startMinuteOfDay = block.startMinuteOfDay,
                    durationMinutes = block.durationMinutes,
                    completed = block.actualStartMinuteOfDay != null,
                    missed = block.id in missedIds
                )
            }
    }

    /**
     * Lists the user's open (not yet completed) tasks, highest priority first.
     *
     * @param appFunctionContext The execution context.
     * @return The list of open tasks; empty if none remain.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listOpenTasks(
        appFunctionContext: AppFunctionContext
    ): List<TaskSummary> = withContext(Dispatchers.IO) {
        taskRepository.getAllTasks().first()
            .filter { !it.isCompleted }
            .sortedByDescending { it.priority }
            .map { task ->
                TaskSummary(
                    id = task.id,
                    title = task.title,
                    priority = task.priority,
                    preferredDurationMinutes = task.preferredDurationMinutes ?: 0
                )
            }
    }

    /**
     * Lists the user's active habits.
     * Provides the habit id required by logHabitCompleted and logHabitSkipped.
     *
     * @param appFunctionContext The execution context.
     * @return The list of active habits; empty if none are configured.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listHabits(
        appFunctionContext: AppFunctionContext
    ): List<HabitSummary> = withContext(Dispatchers.IO) {
        if (!featureEnabled { it.habitsEnabled }) return@withContext emptyList()
        habitRepository.observeHabits().first()
            .filter { it.isActive }
            .map { habit ->
                HabitSummary(
                    id = habit.id,
                    title = habit.title,
                    cadence = habit.cadence,
                    streakCount = habit.streakCount
                )
            }
    }

    /**
     * Lists the user's active medication plans.
     * Provides the plan id required by logMedicationTaken and logMedicationSkipped.
     *
     * @param appFunctionContext The execution context.
     * @return The list of active medication plans; empty if none are configured.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listMedications(
        appFunctionContext: AppFunctionContext
    ): List<MedicationSummary> = withContext(Dispatchers.IO) {
        if (!featureEnabled { it.medicationEnabled }) return@withContext emptyList()
        medicationRepository.observeMedicationPlans().first()
            .filter { it.isActive }
            .map { plan ->
                MedicationSummary(
                    id = plan.id,
                    name = plan.name,
                    dosage = listOf(plan.dosage, plan.unit)
                        .filter { it.isNotBlank() }
                        .joinToString(" "),
                    reminderMinuteOfDay = plan.reminderMinuteOfDay
                )
            }
    }

    /**
     * Resolves the target block for the block-status AppFunctions. Prefers an explicit [blockId];
     * otherwise matches [blockTitle] (case-insensitive) against today's plan, favouring a block
     * that still needs action when titles repeat. Returns null when neither identifies a block.
     */
    private suspend fun resolveTimeBlock(blockId: String?, blockTitle: String?): TimeBlock? {
        blockId.requiredText()?.let { id ->
            return timeBlockRepository.getTimeBlockById(id)
        }
        val title = blockTitle.requiredText() ?: return null
        val matches = timeBlockRepository.getTimeBlocksByDate(LocalDate.now()).first()
            .filter { it.title.equals(title, ignoreCase = true) }
        return matches.firstOrNull { it.actualStartMinuteOfDay == null } ?: matches.firstOrNull()
    }

    private suspend fun runAppFunction(block: suspend () -> Boolean): Boolean =
        try {
            block()
        } catch (e: Exception) {
            false
        }

    /**
     * True when the feature gating [select] is enabled in Developer settings. Agent-facing
     * functions for a disabled feature return false/empty so the assistant cannot create or
     * surface a surface the user has hidden.
     */
    private suspend fun featureEnabled(
        select: (com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags) -> Boolean
    ): Boolean = select(featureFlagsSource.current())

    private fun String?.requiredText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun Int.takeIfScore(): Int? = takeIf { it in 1..10 }

    private companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
    }
}

/**
 * A planned block on the day planner timeline.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ScheduledBlock(
    /** Unique id of the block. */
    val id: String,
    /** Title of the block. */
    val title: String,
    /** Category of the block (e.g. work, study, meals, breaks, routines, sleep). */
    val category: String,
    /** Minute of the day the block starts (0 to 1439, where 480 is 8:00 AM). */
    val startMinuteOfDay: Int,
    /** Duration of the block in minutes. */
    val durationMinutes: Int,
    /** True if the block already has actual time logged (completed); pass false ones to completeTimeBlock. */
    val completed: Boolean,
    /** True if the block was marked missed/skipped. A block is neither completed nor missed until acted on. */
    val missed: Boolean
)

/**
 * A summary of an open task.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TaskSummary(
    /** Unique id of the task. */
    val id: String,
    /** Title of the task. */
    val title: String,
    /** Priority from 0 (lowest) to 5 (highest). */
    val priority: Int,
    /** Expected duration in minutes, or 0 if unspecified. */
    val preferredDurationMinutes: Int
)

/**
 * A summary of an active habit.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class HabitSummary(
    /** Unique id of the habit; pass as habitId to logHabitCompleted or logHabitSkipped. */
    val id: String,
    /** Title of the habit. */
    val title: String,
    /** How often the habit recurs (e.g. daily, weekly). */
    val cadence: String,
    /** Current completion streak in days. */
    val streakCount: Int
)

/**
 * A summary of an active medication plan.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MedicationSummary(
    /** Unique id of the plan; pass as medicationPlanId to logMedicationTaken or logMedicationSkipped. */
    val id: String,
    /** Medication name. */
    val name: String,
    /** Dosage amount including unit (e.g. "1 tablet", "500 mg"). */
    val dosage: String,
    /** Minute of the day the daily reminder fires (0 to 1439). */
    val reminderMinuteOfDay: Int
)
