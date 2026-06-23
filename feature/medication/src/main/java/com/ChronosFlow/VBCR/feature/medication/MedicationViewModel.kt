package com.ChronosFlow.VBCR.feature.medication

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.ai.MedicationAdherenceAssistPlanner
import com.ChronosFlow.VBCR.core.ai.MedicationAssistPlanner
import com.ChronosFlow.VBCR.core.ai.RoutineAssistSource
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCopy
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.ai.genai.RewriteAssistUiState
import com.ChronosFlow.VBCR.core.ai.genai.RewriteStyle
import com.ChronosFlow.VBCR.core.ai.genai.refreshAssistUiSnapshot
import com.ChronosFlow.VBCR.core.ai.MedicationAssistRequest
import com.ChronosFlow.VBCR.core.ai.MedicationAssistSuggestion
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.MedicationSafetyProfile
import com.ChronosFlow.VBCR.core.domain.model.MedicationSchedule
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrence
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrenceType
import com.ChronosFlow.VBCR.core.domain.model.buildLegacyMedicationSchedule
import com.ChronosFlow.VBCR.core.domain.repository.SleepScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.PlannerPreferencesRepository
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveMedicationAdherenceTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ScheduleMedicationReminderUseCase
import com.ChronosFlow.VBCR.core.notifications.AlarmCapabilityRefresher
import com.ChronosFlow.VBCR.core.notifications.AlarmDeliveryCoordinator
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduleResult
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Immutable
data class MedicationAssistUiState(
    val isLoading: Boolean = false,
    val suggestions: List<MedicationAssistSuggestion> = emptyList(),
    val message: String? = null,
    val assistSnapshot: GenAiAssistUiSnapshot? = null
)

data class MedicationAdherenceSuggestion(
    val plan: MedicationPlan,
    val suggestedReminderMinute: Int,
    val reason: String,
    val source: RoutineAssistSource
)

private val weekdaySet = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)
private val weekendSet = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val plannerPreferencesRepository: PlannerPreferencesRepository,
    private val scheduleMedicationReminderUseCase: ScheduleMedicationReminderUseCase,
    observeMedicationAdherenceTrendUseCase: ObserveMedicationAdherenceTrendUseCase,
    private val sleepScheduleRepository: SleepScheduleRepository,
    private val alarmScheduler: AlarmScheduler,
    private val medicationAssistPlanner: MedicationAssistPlanner,
    private val medicationAdherenceAssistPlanner: MedicationAdherenceAssistPlanner,
    private val genAiAssistCoordinator: GenAiAssistCoordinator,
    private val alarmCapabilityRefresher: AlarmCapabilityRefresher,
    private val alarmDeliveryCoordinator: AlarmDeliveryCoordinator
) : ViewModel() {
    // Declared before init: the adherence collector can run synchronously
    // during construction under an unconfined dispatcher.
    private val _adherenceSuggestions = MutableStateFlow<List<MedicationAdherenceSuggestion>>(emptyList())
    val adherenceSuggestions = _adherenceSuggestions.asStateFlow()

    private val _adherenceAssistSnapshot = MutableStateFlow<GenAiAssistUiSnapshot?>(null)
    val adherenceAssistSnapshot = _adherenceAssistSnapshot.asStateFlow()

    init {
        viewModelScope.launch {
            alarmCapabilityRefresher.refreshes.collect {
                refreshExactAlarmPermissionState()
            }
        }
        viewModelScope.launch {
            medicationRepository.observeMedicationPlans().collect { current ->
                refreshAdherenceSuggestions(current)
            }
        }
    }

    val plans = medicationRepository.observeMedicationPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Per-day taken/missed dose counts over the trailing 14 days, oldest first. */
    val adherenceTrend = observeMedicationAdherenceTrendUseCase(windowDays = 14)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val _showExactAlarmPermissionAction = MutableStateFlow(false)
    val showExactAlarmPermissionAction = _showExactAlarmPermissionAction.asStateFlow()

    private val _recentHistoryTemplateIds = MutableStateFlow(plannerPreferencesRepository.getRecentMedicationTemplateIds())
    val recentHistoryTemplateIds = _recentHistoryTemplateIds.asStateFlow()

    private val _assistState = MutableStateFlow(MedicationAssistUiState())
    val assistState = _assistState.asStateFlow()

    private val _rewriteState = MutableStateFlow(RewriteAssistUiState())
    val rewriteState = _rewriteState.asStateFlow()

    private suspend fun refreshAdherenceSuggestions(current: List<MedicationPlan>) {
        val active = current.filter { it.isActive }
        if (active.isEmpty()) {
            _adherenceSuggestions.value = emptyList()
            return
        }
        _adherenceAssistSnapshot.value =
            runCatching { genAiAssistCoordinator.refreshAssistUiSnapshot() }.getOrNull()
        val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val results = runCatching {
            medicationAdherenceAssistPlanner.suggestAdjustments(active, LocalDate.now(), now)
        }.getOrDefault(emptyList())
        _adherenceSuggestions.value = results.mapNotNull { result ->
            active.firstOrNull { it.id == result.medicationPlanId }?.let { plan ->
                MedicationAdherenceSuggestion(
                    plan = plan,
                    suggestedReminderMinute = result.suggestedReminderMinute,
                    reason = result.reason,
                    source = result.source
                )
            }
        }
    }

    /** Applies the suggested reminder time through the same path as the edit form. */
    fun applyAdherenceSuggestion(suggestion: MedicationAdherenceSuggestion) {
        val plan = suggestion.plan
        updateMedication(
            plan = plan,
            name = plan.name,
            dosage = plan.dosage,
            unit = plan.unit,
            reminderMinuteOfDay = suggestion.suggestedReminderMinute,
            takeWithFood = plan.takeWithFood,
            refillNeededAfterDoses = plan.refillNeededAfterDoses,
            notes = plan.notes
        )
        _adherenceSuggestions.value =
            _adherenceSuggestions.value.filterNot { it.plan.id == plan.id }
    }

    fun dismissAdherenceSuggestion(suggestion: MedicationAdherenceSuggestion) {
        _adherenceSuggestions.value =
            _adherenceSuggestions.value.filterNot { it.plan.id == suggestion.plan.id }
    }

    fun addMedication(
        name: String,
        dosage: String,
        unit: String = "dose",
        reminderMinuteOfDay: Int = 9 * 60,
        takeWithFood: Boolean = false,
        refillNeededAfterDoses: Int? = null,
        notes: String? = null,
        schedule: MedicationSchedule? = null,
        safetyProfile: MedicationSafetyProfile? = null
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val planId = UUID.randomUUID().toString()
            val plan = MedicationPlan(
                id = planId,
                name = name.trim(),
                dosage = dosage.trim().ifBlank { "1" },
                unit = unit.trim().ifBlank { "dose" },
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                startAt = LocalDateTime.now(),
                endAt = null,
                reminderMinuteOfDay = reminderMinuteOfDay.coerceIn(0, 1439),
                takeWithFood = takeWithFood,
                missedCount = 0,
                refillNeededAfterDoses = refillNeededAfterDoses,
                isActive = true,
                schedule = schedule?.copy(
                    id = schedule.id.ifBlank { "schedule-$planId" },
                    medicationPlanId = planId
                ) ?: buildLegacyMedicationSchedule(
                    medicationPlanId = planId,
                    primaryReminderMinute = reminderMinuteOfDay
                ),
                safetyProfile = safetyProfile?.copy(
                    medicationPlanId = planId,
                    supplyRemaining = safetyProfile.supplyRemaining ?: refillNeededAfterDoses,
                    refillThreshold = safetyProfile.refillThreshold ?: refillNeededAfterDoses
                ) ?: defaultSafetyProfile(
                    planId = planId,
                    unit = unit,
                    notes = notes,
                    takeWithFood = takeWithFood,
                    refillNeededAfterDoses = refillNeededAfterDoses
                )
            )
            medicationRepository.saveMedicationPlan(plan)
            _status.value = scheduleReminders(plan)
        }
    }

    fun updateMedication(
        plan: MedicationPlan,
        name: String,
        dosage: String,
        unit: String,
        reminderMinuteOfDay: Int,
        takeWithFood: Boolean = false,
        refillNeededAfterDoses: Int? = null,
        notes: String? = null,
        schedule: MedicationSchedule? = null,
        safetyProfile: MedicationSafetyProfile? = null
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val updated = plan.copy(
                name = name.trim(),
                dosage = dosage.trim().ifBlank { plan.dosage },
                unit = unit.trim().ifBlank { plan.unit },
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                reminderMinuteOfDay = reminderMinuteOfDay.coerceIn(0, 1439),
                takeWithFood = takeWithFood,
                refillNeededAfterDoses = refillNeededAfterDoses,
                schedule = schedule?.copy(
                    id = schedule.id.ifBlank { plan.schedule?.id ?: "schedule-${plan.id}" },
                    medicationPlanId = plan.id
                ) ?: plan.schedule ?: buildLegacyMedicationSchedule(
                    medicationPlanId = plan.id,
                    primaryReminderMinute = reminderMinuteOfDay
                ),
                safetyProfile = safetyProfile?.copy(
                    medicationPlanId = plan.id,
                    supplyRemaining = safetyProfile.supplyRemaining ?: refillNeededAfterDoses,
                    refillThreshold = safetyProfile.refillThreshold ?: refillNeededAfterDoses
                ) ?: plan.safetyProfile?.copy(
                    instructions = notes?.trim()?.takeIf { it.isNotBlank() }
                ) ?: defaultSafetyProfile(
                    planId = plan.id,
                    unit = unit,
                    notes = notes,
                    takeWithFood = takeWithFood,
                    refillNeededAfterDoses = refillNeededAfterDoses
                )
            )
            medicationRepository.saveMedicationPlan(updated)
            _status.value = scheduleReminders(updated)
        }
    }

    fun duplicateMedication(plan: MedicationPlan) {
        viewModelScope.launch {
            val copyId = UUID.randomUUID().toString()
            val copy = plan.copy(
                id = copyId,
                name = "${plan.name} (copy)",
                missedCount = 0,
                isActive = true,
                schedule = plan.schedule?.copy(
                    id = "schedule-$copyId",
                    medicationPlanId = copyId,
                    pausedUntil = null
                ),
                safetyProfile = plan.safetyProfile?.copy(
                    medicationPlanId = copyId
                )
            )
            medicationRepository.saveMedicationPlan(copy)
            _status.value = scheduleReminders(copy)
        }
    }

    fun snoozeReminder(plan: MedicationPlan, snoozeMinutes: Int) {
        viewModelScope.launch {
            val now = Instant.now()
            val requestedSnoozeFor = now.plusSeconds(snoozeMinutes.coerceIn(5, 24 * 60) * 60L)
            val snoozedFor = deferIfSleeping(requestedSnoozeFor)
            val request = AlarmRequest(
                id = UUID.randomUUID().toString(),
                type = AlarmRequestType.MEDICATION,
                scheduledFor = snoozedFor,
                title = plan.name,
                message = "Snoozed dose · ${plan.dosage} ${plan.unit}",
                medicationPlanId = plan.id,
                blockId = null,
                reliability = AlarmReliability.EXACT,
                deliveryState = AlarmDeliveryState.PENDING,
                createdAt = now,
                updatedAt = now
            )
            val result = alarmScheduler.scheduleAlarmRequest(request)
            scheduleMedicationReminderUseCase(request.withScheduleResult(result))
            medicationRepository.addMedicationDoseEvent(
                MedicationDoseEvent(
                    id = UUID.randomUUID().toString(),
                    medicationPlanId = plan.id,
                    type = MedicationDoseEventType.SNOOZED,
                    eventDate = LocalDate.now(),
                    recordedAt = Instant.now(),
                    scheduledMinuteOfDay = plan.reminderMinuteOfDay,
                    reason = "Snoozed by $snoozeMinutes minutes",
                    doseAmount = null
                )
            )
            _status.value = if (snoozedFor == requestedSnoozeFor) {
                "Snoozed ${plan.name} for $snoozeMinutes minutes"
            } else {
                "Snoozed ${plan.name} until ${formatMinute(snoozedFor.localMinuteOfDay())}"
            }
        }
    }

    fun markDoseTaken(plan: MedicationPlan) {
        viewModelScope.launch {
            medicationRepository.addMedicationDoseEvent(
                MedicationDoseEvent(
                    id = UUID.randomUUID().toString(),
                    medicationPlanId = plan.id,
                    type = MedicationDoseEventType.TAKEN,
                    eventDate = LocalDate.now(),
                    recordedAt = Instant.now(),
                    scheduledMinuteOfDay = plan.reminderMinuteOfDay,
                    reason = null,
                    doseAmount = plan.dosage
                )
            )
            // Re-fetch to get the latest supplyRemaining; avoids a stale-read decrement
            // when the user taps "Taken" twice in rapid succession.
            val latestPlan = medicationRepository.getMedicationPlanById(plan.id) ?: plan
            val remaining = latestPlan.safetyProfile?.supplyRemaining?.let { (it - 1).coerceAtLeast(0) }
            val updated = latestPlan.copy(
                safetyProfile = latestPlan.safetyProfile?.copy(supplyRemaining = remaining)
            )
            medicationRepository.saveMedicationPlan(updated)

            // Trigger low supply warning if remaining drops below/equals the refill threshold
            val safetyProfile = latestPlan.safetyProfile
            val threshold = safetyProfile?.refillThreshold
            if (remaining != null && threshold != null && remaining <= threshold) {
                alarmDeliveryCoordinator.deliverLowSupplyWarning(updated, remaining)
            }

            _status.value = "${plan.name} dose recorded"
        }
    }

    fun markDoseMissed(plan: MedicationPlan) {
        viewModelScope.launch {
            medicationRepository.addMedicationDoseEvent(
                MedicationDoseEvent(
                    id = UUID.randomUUID().toString(),
                    medicationPlanId = plan.id,
                    type = MedicationDoseEventType.MISSED,
                    eventDate = LocalDate.now(),
                    recordedAt = Instant.now(),
                    scheduledMinuteOfDay = plan.reminderMinuteOfDay,
                    reason = "Marked missed",
                    doseAmount = null
                )
            )
            medicationRepository.saveMedicationPlan(plan.copy(missedCount = plan.missedCount + 1))
            _status.value = "${plan.name} marked missed"
        }
    }

    fun skipDoseToday(plan: MedicationPlan) {
        viewModelScope.launch {
            medicationRepository.addMedicationDoseEvent(
                MedicationDoseEvent(
                    id = UUID.randomUUID().toString(),
                    medicationPlanId = plan.id,
                    type = MedicationDoseEventType.SKIPPED,
                    eventDate = LocalDate.now(),
                    recordedAt = Instant.now(),
                    scheduledMinuteOfDay = plan.reminderMinuteOfDay,
                    reason = "Skipped today",
                    doseAmount = null
                )
            )
            _status.value = "${plan.name} skipped for today"
        }
    }

    fun pausePlan(plan: MedicationPlan, days: Int) {
        viewModelScope.launch {
            val updated = plan.copy(
                schedule = (plan.schedule ?: buildLegacyMedicationSchedule(
                    medicationPlanId = plan.id,
                    primaryReminderMinute = plan.reminderMinuteOfDay
                )).copy(pausedUntil = LocalDate.now().plusDays(days.toLong().coerceAtLeast(1)))
            )
            medicationRepository.saveMedicationPlan(updated)
            medicationRepository.addMedicationDoseEvent(
                MedicationDoseEvent(
                    id = UUID.randomUUID().toString(),
                    medicationPlanId = plan.id,
                    type = MedicationDoseEventType.PAUSED,
                    eventDate = LocalDate.now(),
                    recordedAt = Instant.now(),
                    scheduledMinuteOfDay = null,
                    reason = "Paused for $days day(s)",
                    doseAmount = null
                )
            )
            _status.value = "${plan.name} paused"
        }
    }

    fun resumePlan(plan: MedicationPlan) {
        viewModelScope.launch {
            val updated = plan.copy(
                schedule = (plan.schedule ?: buildLegacyMedicationSchedule(
                    medicationPlanId = plan.id,
                    primaryReminderMinute = plan.reminderMinuteOfDay
                )).copy(pausedUntil = null)
            )
            medicationRepository.saveMedicationPlan(updated)
            medicationRepository.addMedicationDoseEvent(
                MedicationDoseEvent(
                    id = UUID.randomUUID().toString(),
                    medicationPlanId = plan.id,
                    type = MedicationDoseEventType.RESUMED,
                    eventDate = LocalDate.now(),
                    recordedAt = Instant.now(),
                    scheduledMinuteOfDay = null,
                    reason = "Resumed",
                    doseAmount = null
                )
            )
            _status.value = "${plan.name} resumed"
        }
    }

    fun archive(plan: MedicationPlan) {
        viewModelScope.launch {
            medicationRepository.saveMedicationPlan(plan.copy(isActive = false))
        }
    }

    fun clearStatus() {
        _status.value = null
    }

    fun openExactAlarmSettings() {
        alarmScheduler.routeToExactAlarmSetting()
    }

    fun dismissExactAlarmPermissionAction() {
        _showExactAlarmPermissionAction.value = false
    }

    private fun refreshExactAlarmPermissionState() {
        if (_showExactAlarmPermissionAction.value) {
            _showExactAlarmPermissionAction.value = !alarmScheduler.canScheduleExactAlarms()
        }
    }

    fun rememberHistoryTemplateSelection(selectedId: String) {
        val updated = buildList {
            add(selectedId)
            _recentHistoryTemplateIds.value.filterNot { it == selectedId }.take(4).forEach(::add)
        }
        _recentHistoryTemplateIds.value = updated
        plannerPreferencesRepository.saveRecentMedicationTemplateIds(updated)
    }

    fun requestMedicationAssist(request: MedicationAssistRequest) {
        viewModelScope.launch {
            val snapshot = runCatching { genAiAssistCoordinator.refreshAssistUiSnapshot() }.getOrNull()
            _assistState.value = MedicationAssistUiState(isLoading = true, assistSnapshot = snapshot)
            val suggestions = runCatching { medicationAssistPlanner.suggest(request) }
                .getOrElse { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _assistState.value = MedicationAssistUiState(
                        message = throwable.message ?: "No suggestions available",
                        assistSnapshot = snapshot
                    )
                    return@launch
                }
            // On-device spelling clean-up of the captured medication name (ML Kit GenAI Proofreading),
            // surfaced as an extra Details suggestion. Best-effort: any failure is ignored.
            val refinedName = runCatching {
                request.name.takeIf { it.isNotBlank() }?.let { medicationAssistPlanner.refineName(it) }
            }.getOrNull()
            val merged = (listOfNotNull(refinedName) + suggestions).distinctBy { it.id }
            _assistState.value = if (merged.isNotEmpty()) {
                MedicationAssistUiState(
                    suggestions = merged,
                    assistSnapshot = snapshot,
                    message = snapshot?.takeIf { it.aiDisabled }?.let { GenAiAssistCopy.disabledAssistMessage() }
                )
            } else {
                MedicationAssistUiState(
                    message = "No suggestions available",
                    assistSnapshot = snapshot
                )
            }
        }
    }

    fun clearMedicationAssist() {
        _assistState.value = MedicationAssistUiState()
    }

    /**
     * Rewrites the free-text medication notes with the on-device ML Kit GenAI Rewriting feature.
     * Notes only — name, dose, and frequency are never rewritten. The result is published as a
     * preview the form must explicitly apply; the field is never replaced automatically.
     */
    fun rewriteMedicationNotes(text: String, style: RewriteStyle, styleLabel: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _rewriteState.value = RewriteAssistUiState(isLoading = true, styleLabel = styleLabel)
            val rewritten = runCatching { medicationAssistPlanner.rewriteNotes(trimmed, style) }.getOrNull()
            _rewriteState.value = if (rewritten != null) {
                RewriteAssistUiState(styleLabel = styleLabel, original = text, rewritten = rewritten)
            } else {
                RewriteAssistUiState(
                    message = "Rewrite is unavailable on this device right now — your wording is unchanged."
                )
            }
        }
    }

    fun clearMedicationRewrite() {
        _rewriteState.value = RewriteAssistUiState()
    }

    private suspend fun scheduleReminders(plan: MedicationPlan): String {
        if (!plan.isActive) {
            return "Medication archived"
        }
        val pausedUntil = plan.schedule?.pausedUntil
        if (pausedUntil != null && !pausedUntil.isBefore(LocalDate.now())) {
            return "Medication paused until $pausedUntil"
        }
        if (plan.schedule?.isPrn == true || plan.schedule?.recurrence?.type == PlannerRecurrenceType.PRN) {
            return "${plan.name} saved as needed"
        }
        val now = Instant.now()
        val reminderMinutes = buildList {
            val scheduledTimes = plan.schedule?.recurrence?.normalizedTimesOfDayMinutes.orEmpty()
            if (scheduledTimes.isEmpty()) {
                add(plan.reminderMinuteOfDay)
            } else {
                addAll(scheduledTimes)
            }
        }.distinct().sorted()
        val messages = reminderMinutes.map { minute ->
            scheduleReminderAt(plan, minute, nextMedicationOccurrence(plan, minute, now))
        }
        return messages.lastOrNull() ?: "Reminders updated"
    }

    private suspend fun scheduleReminderAt(plan: MedicationPlan, minuteOfDay: Int, scheduledFor: Instant): String {
        val deferredScheduledFor = deferIfSleeping(scheduledFor)
        val request = AlarmRequest(
            id = UUID.randomUUID().toString(),
            type = AlarmRequestType.MEDICATION,
            scheduledFor = deferredScheduledFor,
            title = plan.name,
            message = "Take ${plan.dosage} ${plan.unit}",
            medicationPlanId = plan.id,
            blockId = null,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val result = alarmScheduler.scheduleAlarmRequest(request)
        val persistedRequest = request.withScheduleResult(result)
        scheduleMedicationReminderUseCase(persistedRequest)
        _showExactAlarmPermissionAction.value = result is AlarmScheduleResult.Scheduled && !result.exact ||
            result is AlarmScheduleResult.ExactDenied
        return when (result) {
            is AlarmScheduleResult.Scheduled -> if (result.exact) {
                "Exact reminder scheduled for ${formatMinute(deferredScheduledFor.localMinuteOfDay())}"
            } else {
                "Reminder scheduled with a 10-minute fallback window"
            }
            is AlarmScheduleResult.ExactDenied -> "Exact alarm permission is required for this reminder"
            is AlarmScheduleResult.PermissionDenied -> "Notification permission is required for medication reminders"
            is AlarmScheduleResult.Skipped -> "Reminder skipped: ${result.reason}"
        }
    }

    private fun nextMedicationOccurrence(
        plan: MedicationPlan,
        minuteOfDay: Int,
        now: Instant
    ): Instant {
        val zone = ZoneId.systemDefault()
        val localNow = now.atZone(zone)
        val today = localNow.toLocalDate()
        val startDate = plan.startAt?.toLocalDate() ?: today
        val recurrence = plan.schedule?.recurrence ?: PlannerRecurrence()
        val normalizedMinute = ((minuteOfDay % (24 * 60)) + (24 * 60)) % (24 * 60)
        repeat(370) { offset ->
            val candidateDate = today.plusDays(offset.toLong())
            if (candidateDate.matchesMedicationRecurrence(recurrence, startDate)) {
                val candidate = candidateDate
                    .atStartOfDay()
                    .plusMinutes(normalizedMinute.toLong())
                    .atZone(zone)
                    .toInstant()
                if (candidate.isAfter(now)) {
                    return candidate
                }
            }
        }
        return today
            .plusDays(1)
            .atStartOfDay()
            .plusMinutes(normalizedMinute.toLong())
            .atZone(zone)
            .toInstant()
    }

    private fun LocalDate.matchesMedicationRecurrence(
        recurrence: PlannerRecurrence,
        startDate: LocalDate
    ): Boolean = when (recurrence.type) {
        PlannerRecurrenceType.DAILY,
        PlannerRecurrenceType.MULTIPLE_TIMES_DAILY -> true
        PlannerRecurrenceType.WEEKDAYS -> dayOfWeek in weekdaySet
        PlannerRecurrenceType.WEEKENDS -> dayOfWeek in weekendSet
        PlannerRecurrenceType.SELECTED_WEEKDAYS -> recurrence.weekdays.isEmpty() || dayOfWeek in recurrence.weekdays
        PlannerRecurrenceType.EVERY_N_DAYS -> {
            val days = ChronoUnit.DAYS.between(startDate, this)
            days >= 0 && days % recurrence.interval.coerceAtLeast(1) == 0L
        }
        PlannerRecurrenceType.WEEKLY_INTERVAL -> {
            val weeks = ChronoUnit.WEEKS.between(startDate.weekStart(), weekStart())
            val allowedDays = recurrence.weekdays.ifEmpty { setOf(startDate.dayOfWeek) }
            weeks >= 0 && weeks % recurrence.interval.coerceAtLeast(1) == 0L && dayOfWeek in allowedDays
        }
        PlannerRecurrenceType.PRN -> false
    }

    private fun LocalDate.weekStart(): LocalDate =
        minusDays((dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    private fun AlarmRequest.withScheduleResult(result: AlarmScheduleResult): AlarmRequest {
        val now = Instant.now()
        val (reliability, deliveryState, failureReason) = when (result) {
            is AlarmScheduleResult.Scheduled -> if (result.exact) {
                Triple(AlarmReliability.EXACT, AlarmDeliveryState.SCHEDULED, null)
            } else {
                Triple(
                    AlarmReliability.DEGRADED_WINDOW,
                    AlarmDeliveryState.DEGRADED,
                    "Exact alarm permission unavailable; scheduled with fallback window"
                )
            }
            is AlarmScheduleResult.ExactDenied -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                "Exact alarm permission denied"
            )
            is AlarmScheduleResult.PermissionDenied -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                "Notification permission denied"
            )
            is AlarmScheduleResult.Skipped -> Triple(
                AlarmReliability.BLOCKED,
                AlarmDeliveryState.FAILED,
                result.reason
            )
        }
        return copy(
            reliability = reliability,
            deliveryState = deliveryState,
            updatedAt = now,
            failureReason = failureReason
        )
    }

    private fun deferIfSleeping(scheduledFor: Instant): Instant {
        val sleepSchedule = sleepScheduleRepository.getSleepSchedule()
        return sleepSchedule.deferInstant(scheduledFor, ZoneId.systemDefault())
    }

    private fun Instant.localMinuteOfDay(): Int {
        val localDateTime = atZone(ZoneId.systemDefault())
        return localDateTime.hour * 60 + localDateTime.minute
    }

    private companion object {
        const val TAG = "MedicationViewModel"
    }
}

private fun defaultSafetyProfile(
    planId: String,
    unit: String,
    notes: String?,
    takeWithFood: Boolean,
    refillNeededAfterDoses: Int?
): MedicationSafetyProfile {
    val normalizedUnit = unit.trim().lowercase()
    val normalizedForm = when (normalizedUnit) {
        "tablet", "capsule", "liquid", "drop", "inhaler", "injection", "patch", "powder" -> normalizedUnit
        "ml" -> "liquid"
        else -> "tablet"
    }
    return MedicationSafetyProfile(
        medicationPlanId = planId,
        form = normalizedForm,
        route = if (normalizedForm == "inhaler") "inhaled" else "oral",
        instructions = notes?.trim()?.takeIf { it.isNotBlank() },
        mealTiming = if (takeWithFood) "With food" else "Anytime",
        supplyRemaining = refillNeededAfterDoses,
        refillThreshold = refillNeededAfterDoses
    )
}

fun formatMinute(minute: Int): String {
    val hour = (minute / 60).floorMod(24)
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return "$displayHour:${(minute % 60).toString().padStart(2, '0')} $suffix"
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
