package com.ChronosFlow.VBCR.feature.medication

import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.MedicationSchedule
import com.ChronosFlow.VBCR.core.domain.model.MedicationDailyAdherence
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrence
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrenceType
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.MedicationSafetyProfile
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.PlannerPreferencesRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepScheduleRepository
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveMedicationAdherenceTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ScheduleMedicationReminderUseCase
import com.ChronosFlow.VBCR.core.ai.MedicationAssistRequest
import com.ChronosFlow.VBCR.core.ai.MedicationAssistSuggestion
import com.ChronosFlow.VBCR.core.ai.RoutineAssistSource
import com.ChronosFlow.VBCR.core.ai.MedicationAdherenceAssistPlanner
import com.ChronosFlow.VBCR.core.ai.MedicationAdherenceAssistResult
import com.ChronosFlow.VBCR.core.ai.MedicationAssistPlanner
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.ai.genai.RewriteAssistUiState
import com.ChronosFlow.VBCR.core.ai.genai.RewriteStyle
import com.ChronosFlow.VBCR.core.ai.genai.refreshAssistUiSnapshot
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduleResult
import com.ChronosFlow.VBCR.core.notifications.AlarmCapabilityRefresher
import com.ChronosFlow.VBCR.core.notifications.AlarmDeliveryCoordinator
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationViewModelTest {
    private val medicationRepository: MedicationRepository = mockk()
    private val plannerPreferencesRepository: PlannerPreferencesRepository = mockk(relaxed = true)
    private val scheduleMedicationReminderUseCase: ScheduleMedicationReminderUseCase = mockk()
    private val observeMedicationAdherenceTrendUseCase: ObserveMedicationAdherenceTrendUseCase = mockk()
    private val sleepScheduleRepository: SleepScheduleRepository = mockk()
    private val alarmScheduler: AlarmScheduler = mockk()
    private val medicationAssistPlanner: MedicationAssistPlanner = mockk()
    private val medicationAdherenceAssistPlanner: MedicationAdherenceAssistPlanner = mockk(relaxed = true)
    private val genAiAssistCoordinator: GenAiAssistCoordinator = mockk()
    private val alarmCapabilityRefresher: AlarmCapabilityRefresher = mockk()
    private val alarmDeliveryCoordinator: AlarmDeliveryCoordinator = mockk(relaxed = true)
    private lateinit var viewModel: MedicationViewModel
    private val requestSlot = slot<AlarmRequest>()
    private val originalTimeZone = TimeZone.getDefault()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        every { medicationRepository.observeMedicationPlans() } returns flowOf(emptyList())
        every { observeMedicationAdherenceTrendUseCase(any(), any()) } returns
            flowOf(emptyList<MedicationDailyAdherence>())
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule(
            enabled = true,
            startMinute = 21 * 60,
            endMinute = 7 * 60
        )
        every { plannerPreferencesRepository.getRecentMedicationTemplateIds() } returns emptyList()
        coEvery { medicationRepository.saveMedicationPlan(any()) } returns Unit
        coEvery { medicationRepository.addMedicationDoseEvent(any()) } returns Unit
        coEvery { scheduleMedicationReminderUseCase(any()) } returns Unit
        coEvery { medicationAssistPlanner.suggest(any()) } returns emptyList()
        coEvery { genAiAssistCoordinator.refreshAssistUiSnapshot() } returns GenAiAssistUiSnapshot(
            bannerTitle = "Gemini Nano ready",
            bannerMessage = "On-device assist",
            privacyModeLabel = "Gemini Nano (on-device)",
            aiDisabled = false
        )
        every {
            alarmScheduler.scheduleAlarmRequest(capture(requestSlot))
        } answers {
            AlarmScheduleResult.Scheduled(requestSlot.captured.id, exact = true)
        }
        every { alarmCapabilityRefresher.refreshes } returns MutableSharedFlow()
        viewModel = MedicationViewModel(
            medicationRepository = medicationRepository,
            plannerPreferencesRepository = plannerPreferencesRepository,
            scheduleMedicationReminderUseCase = scheduleMedicationReminderUseCase,
            observeMedicationAdherenceTrendUseCase = observeMedicationAdherenceTrendUseCase,
            sleepScheduleRepository = sleepScheduleRepository,
            alarmScheduler = alarmScheduler,
            medicationAssistPlanner = medicationAssistPlanner,
            medicationAdherenceAssistPlanner = medicationAdherenceAssistPlanner,
            genAiAssistCoordinator = genAiAssistCoordinator,
            alarmCapabilityRefresher = alarmCapabilityRefresher,
            alarmDeliveryCoordinator = alarmDeliveryCoordinator
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        TimeZone.setDefault(originalTimeZone)
        Dispatchers.resetMain()
    }

    @Test
    fun `night medication reminders defer to wake time`() = runTest(testDispatcher) {
        viewModel.addMedication(
            name = "Magnesium",
            dosage = "1",
            reminderMinuteOfDay = 22 * 60
        )

        val scheduledLocal = requestSlot.captured.scheduledFor.atZone(java.time.ZoneId.of("UTC"))
        assertEquals(7, scheduledLocal.hour)
        assertEquals(0, scheduledLocal.minute)
        assertEquals("Exact reminder scheduled for 7:00 AM", viewModel.status.value)
    }

    @Test
    fun `as needed medications do not schedule alarms`() = runTest(testDispatcher) {
        viewModel.addMedication(
            name = "Inhaler",
            dosage = "2",
            unit = "dose",
            schedule = MedicationSchedule(
                id = "schedule-prn",
                medicationPlanId = "",
                recurrence = PlannerRecurrence(type = PlannerRecurrenceType.PRN),
                plannerVisible = false,
                isPrn = true
            )
        )

        assertEquals("Inhaler saved as needed", viewModel.status.value)
        verify(exactly = 0) { alarmScheduler.scheduleAlarmRequest(any()) }
        coVerify(exactly = 0) { scheduleMedicationReminderUseCase(any()) }
    }

    @Test
    fun `selected weekday medications schedule the next matching date`() = runTest(testDispatcher) {
        val zone = ZoneId.of("UTC")
        val targetDate = LocalDate.now(zone).plusDays(2)
        val targetDay = targetDate.dayOfWeek

        viewModel.addMedication(
            name = "Weekly dose",
            dosage = "1",
            reminderMinuteOfDay = 10 * 60,
            schedule = MedicationSchedule(
                id = "schedule-selected",
                medicationPlanId = "",
                recurrence = PlannerRecurrence(
                    type = PlannerRecurrenceType.SELECTED_WEEKDAYS,
                    weekdays = setOf(targetDay),
                    timesOfDayMinutes = listOf(10 * 60)
                )
            )
        )

        val scheduledLocal = requestSlot.captured.scheduledFor.atZone(zone)
        assertEquals(targetDate, scheduledLocal.toLocalDate())
        assertEquals(targetDay, scheduledLocal.dayOfWeek)
        assertEquals(10, scheduledLocal.hour)
        assertEquals(0, scheduledLocal.minute)
    }

    @Test
    fun `updateMedication trims and preserves fallback values`() = runTest(testDispatcher) {
        val plan = medicationPlan(
            id = "plan-2",
            dosage = "2",
            unit = "mg",
            safetyProfile = medicationSafetyProfile(planId = "plan-2", remaining = 12, threshold = 10)
        )

        viewModel.updateMedication(
            plan = plan,
            name = "  Updated Med  ",
            dosage = "   ",
            unit = "   ",
            reminderMinuteOfDay = -45,
            notes = "  with food  ",
            refillNeededAfterDoses = 6,
            schedule = MedicationSchedule(
                id = "",
                medicationPlanId = "",
                recurrence = PlannerRecurrence(type = PlannerRecurrenceType.DAILY, timesOfDayMinutes = listOf(10 * 60))
            ),
            safetyProfile = medicationSafetyProfile(planId = "plan-2", remaining = 4, threshold = 2)
        )
        advanceUntilIdle()

        val updated = slot<MedicationPlan>().also { coVerify { medicationRepository.saveMedicationPlan(capture(it)) } }.captured
        assertEquals("Updated Med", updated.name)
        assertEquals("2", updated.dosage)
        assertEquals("mg", updated.unit)
        assertEquals(0, updated.reminderMinuteOfDay)
        assertEquals("with food", updated.notes)
        assertEquals("schedule-${plan.id}", updated.schedule?.id)
        assertEquals("plan-2", updated.safetyProfile?.medicationPlanId)
        assertEquals(6, updated.refillNeededAfterDoses)
    }

    @Test
    fun `duplicateMedication creates active copy and preserves linkage`() = runTest(testDispatcher) {
        val source = medicationPlan(
            id = "plan-3",
            name = "Vitamin D",
            dosage = "1000",
            unit = "iu",
            schedule = medicationSchedule(
                id = "schedule-3",
                medicationPlanId = "plan-3",
                recurrenceType = PlannerRecurrenceType.DAILY
            ),
            safetyProfile = medicationSafetyProfile("plan-3")
        )

        viewModel.duplicateMedication(source)
        advanceUntilIdle()

        val copy = slot<MedicationPlan>().also { coVerify { medicationRepository.saveMedicationPlan(capture(it)) } }.captured
        assertEquals("Vitamin D (copy)", copy.name)
        assertEquals(0, copy.missedCount)
        assertEquals(true, copy.isActive)
        assertEquals(source.schedule?.recurrence, copy.schedule?.recurrence)
        assertEquals(copy.id, copy.schedule?.medicationPlanId)
        assertEquals(copy.id, copy.safetyProfile?.medicationPlanId)
        assertEquals(true, viewModel.status.value?.isNotBlank() == true)
    }

    @Test
    fun `snoozeReminder schedules deferred reminder and records snooze event`() = runTest(testDispatcher) {
        val plan = medicationPlan(
            id = "plan-4",
            reminderMinuteOfDay = 8 * 60
        )
        val eventSlot = slot<MedicationDoseEvent>()
        coEvery { medicationRepository.addMedicationDoseEvent(capture(eventSlot)) } returns Unit

        viewModel.snoozeReminder(plan, 10)
        advanceUntilIdle()

        val event = eventSlot.captured
        assertEquals(MedicationDoseEventType.SNOOZED, event.type)
        assertEquals("Snoozed by 10 minutes", event.reason)
    }

    @Test
    fun `markDoseTaken decreases remaining supply and triggers refill warning when low`() = runTest(testDispatcher) {
        val plan = medicationPlan(
            id = "plan-5",
            dosage = "1",
            unit = "mg",
            safetyProfile = medicationSafetyProfile("plan-5", remaining = 2, threshold = 2)
        )
        val saveSlot = slot<MedicationPlan>()
        val eventSlot = slot<MedicationDoseEvent>()
        coEvery { medicationRepository.saveMedicationPlan(capture(saveSlot)) } returns Unit
        coEvery { medicationRepository.addMedicationDoseEvent(capture(eventSlot)) } returns Unit
        // markDoseTaken re-fetches the plan to avoid stale-read decrement on rapid double-tap.
        coEvery { medicationRepository.getMedicationPlanById("plan-5") } returns plan

        viewModel.markDoseTaken(plan)
        advanceUntilIdle()

        val event = eventSlot.captured
        assertEquals(MedicationDoseEventType.TAKEN, event.type)
        assertEquals("1", event.doseAmount)
        val updated = saveSlot.captured
        assertEquals(1, updated.safetyProfile?.supplyRemaining)
        verify { alarmDeliveryCoordinator.deliverLowSupplyWarning(updated, 1) }
    }

    @Test
    fun `markDoseMissed increments missed count and updates status`() = runTest(testDispatcher) {
        val plan = medicationPlan(id = "plan-6", missedCount = 3)

        viewModel.markDoseMissed(plan)
        advanceUntilIdle()

        val updated = slot<MedicationPlan>().also { coVerify { medicationRepository.saveMedicationPlan(capture(it)) } }.captured
        assertEquals(4, updated.missedCount)
        assertEquals("${plan.name} marked missed", viewModel.status.value)
    }

    @Test
    fun `skipDoseToday records skipped event and status`() = runTest(testDispatcher) {
        val plan = medicationPlan(id = "plan-7")

        val eventSlot = slot<MedicationDoseEvent>()
        coEvery { medicationRepository.addMedicationDoseEvent(capture(eventSlot)) } returns Unit
        viewModel.skipDoseToday(plan)
        advanceUntilIdle()

        assertEquals(MedicationDoseEventType.SKIPPED, eventSlot.captured.type)
        assertEquals("${plan.name} skipped for today", viewModel.status.value)
    }

    @Test
    fun `pausePlan clamps minimum days and sets pausedUntil date`() = runTest(testDispatcher) {
        val plan = medicationPlan(id = "plan-8")
        val pausedSlot = slot<MedicationPlan>()
        coEvery { medicationRepository.saveMedicationPlan(capture(pausedSlot)) } returns Unit

        viewModel.pausePlan(plan, days = 0)
        advanceUntilIdle()

        val paused = pausedSlot.captured
        assertEquals(LocalDate.now().plusDays(1), paused.schedule?.pausedUntil)
        assertEquals("${plan.name} paused", viewModel.status.value)
    }

    @Test
    fun `resumePlan clears paused until`() = runTest(testDispatcher) {
        val plan = medicationPlan(
            id = "plan-9",
            schedule = medicationSchedule(
                id = "plan-9-schedule",
                medicationPlanId = "plan-9",
                pausedUntil = LocalDate.now().plusDays(2),
                recurrenceType = PlannerRecurrenceType.DAILY
            )
        )
        val resumeSlot = slot<MedicationPlan>()
        coEvery { medicationRepository.saveMedicationPlan(capture(resumeSlot)) } returns Unit

        viewModel.resumePlan(plan)
        advanceUntilIdle()

        assertNull(resumeSlot.captured.schedule?.pausedUntil)
        assertEquals("${plan.name} resumed", viewModel.status.value)
    }

    @Test
    fun `archive marks plan inactive`() = runTest(testDispatcher) {
        val plan = medicationPlan(id = "plan-archive", isActive = true)
        val archivedSlot = slot<MedicationPlan>()
        coEvery { medicationRepository.saveMedicationPlan(capture(archivedSlot)) } returns Unit

        viewModel.archive(plan)
        advanceUntilIdle()

        assertEquals(false, archivedSlot.captured.isActive)
    }

    @Test
    fun `clearStatus and exact alarm permission action helpers reset state`() = runTest(testDispatcher) {
        viewModel.clearStatus()
        assertNull(viewModel.status.value)

        viewModel.dismissExactAlarmPermissionAction()
        assertEquals(false, viewModel.showExactAlarmPermissionAction.value)
    }

    @Test
    fun `rememberHistoryTemplateSelection moves selected id to front and persists`() = runTest(testDispatcher) {
        every { plannerPreferencesRepository.getRecentMedicationTemplateIds() } returns listOf("older", "older2", "older3", "older4", "older5", "older6")
        viewModel = MedicationViewModel(
            medicationRepository = medicationRepository,
            plannerPreferencesRepository = plannerPreferencesRepository,
            scheduleMedicationReminderUseCase = scheduleMedicationReminderUseCase,
            observeMedicationAdherenceTrendUseCase = observeMedicationAdherenceTrendUseCase,
            sleepScheduleRepository = sleepScheduleRepository,
            alarmScheduler = alarmScheduler,
            medicationAssistPlanner = medicationAssistPlanner,
            medicationAdherenceAssistPlanner = medicationAdherenceAssistPlanner,
            genAiAssistCoordinator = genAiAssistCoordinator,
            alarmCapabilityRefresher = alarmCapabilityRefresher,
            alarmDeliveryCoordinator = alarmDeliveryCoordinator
        )

        viewModel.rememberHistoryTemplateSelection("selected-id")
        verify { plannerPreferencesRepository.saveRecentMedicationTemplateIds(listOf("selected-id", "older", "older2", "older3", "older4")) }
    }

    @Test
    fun `adherence suggestions populate and apply moves the reminder`() = runTest(testDispatcher) {
        val plan = MedicationPlan(
            id = "plan-adherence",
            name = "Vitamin D",
            dosage = "1",
            unit = "tablet",
            notes = null,
            startAt = null,
            endAt = null,
            reminderMinuteOfDay = 8 * 60,
            takeWithFood = false,
            missedCount = 0,
            refillNeededAfterDoses = null,
            isActive = true
        )
        every { medicationRepository.observeMedicationPlans() } returns flowOf(listOf(plan))
        coEvery {
            medicationAdherenceAssistPlanner.suggestAdjustments(any(), any(), any())
        } returns listOf(
            MedicationAdherenceAssistResult(
                medicationPlanId = "plan-adherence",
                suggestedReminderMinute = 600,
                reason = "Usually taken around 10:00",
                source = RoutineAssistSource.LOCAL
            )
        )
        viewModel = MedicationViewModel(
            medicationRepository = medicationRepository,
            plannerPreferencesRepository = plannerPreferencesRepository,
            scheduleMedicationReminderUseCase = scheduleMedicationReminderUseCase,
            observeMedicationAdherenceTrendUseCase = observeMedicationAdherenceTrendUseCase,
            sleepScheduleRepository = sleepScheduleRepository,
            alarmScheduler = alarmScheduler,
            medicationAssistPlanner = medicationAssistPlanner,
            medicationAdherenceAssistPlanner = medicationAdherenceAssistPlanner,
            genAiAssistCoordinator = genAiAssistCoordinator,
            alarmCapabilityRefresher = alarmCapabilityRefresher,
            alarmDeliveryCoordinator = alarmDeliveryCoordinator
        )

        val suggestion = viewModel.adherenceSuggestions.value.single()
        assertEquals(600, suggestion.suggestedReminderMinute)

        viewModel.applyAdherenceSuggestion(suggestion)

        coVerify {
            medicationRepository.saveMedicationPlan(
                match { it.id == "plan-adherence" && it.reminderMinuteOfDay == 600 }
            )
        }
        assertEquals(emptyList<MedicationAdherenceSuggestion>(), viewModel.adherenceSuggestions.value)
    }

    @Test
    fun `requestMedicationAssist populates suggestions and clears state`() = runTest(testDispatcher) {
        val suggestion = MedicationAssistSuggestion.Reminder(
            id = "auto-1",
            label = "Morning",
            reason = "Routine",
            source = RoutineAssistSource.LOCAL,
            primaryMinute = 8 * 60
        )
        coEvery { medicationAssistPlanner.suggest(any()) } returns listOf(suggestion)

        viewModel.requestMedicationAssist(
            MedicationAssistRequest(
                name = "Vitamin D",
                dosage = "1000",
                unit = "IU",
                frequency = "Once daily",
                primaryReminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(suggestion), viewModel.assistState.value.suggestions)
        assertEquals(null, viewModel.assistState.value.message)
    }

    @Test
    fun `requestMedicationAssist captures planner failure message`() = runTest(testDispatcher) {
        coEvery { medicationAssistPlanner.suggest(any()) } throws IllegalStateException("planner down")

        viewModel.requestMedicationAssist(
            MedicationAssistRequest(
                name = "Vitamin D",
                dosage = "1000",
                unit = "IU",
                frequency = "Once daily",
                primaryReminderMinute = 8 * 60,
                secondaryReminderMinute = null,
                mealTiming = "Anytime",
                hasRefillTracking = false,
                notes = "",
                form = "tablet",
                route = "oral"
            )
        )
        advanceUntilIdle()

        assertEquals("planner down", viewModel.assistState.value.message)
        assertEquals(emptyList<MedicationAssistSuggestion>(), viewModel.assistState.value.suggestions)
    }

    @Test
    fun `rewriteMedicationNotes publishes a preview the form applies explicitly`() = runTest(testDispatcher) {
        coEvery {
            medicationAssistPlanner.rewriteNotes("take with a full glass of water in the morning", RewriteStyle.SHORTEN)
        } returns "Take with water in the morning"

        viewModel.rewriteMedicationNotes(
            text = "take with a full glass of water in the morning",
            style = RewriteStyle.SHORTEN,
            styleLabel = "Shorten"
        )
        advanceUntilIdle()

        val state = viewModel.rewriteState.value
        assertEquals(false, state.isLoading)
        assertEquals("Shorten", state.styleLabel)
        assertEquals("take with a full glass of water in the morning", state.original)
        assertEquals("Take with water in the morning", state.rewritten)
    }

    @Test
    fun `rewriteMedicationNotes reports when the rewrite tool is unavailable`() = runTest(testDispatcher) {
        coEvery { medicationAssistPlanner.rewriteNotes(any(), any()) } returns null

        viewModel.rewriteMedicationNotes(
            text = "take with a full glass of water in the morning",
            style = RewriteStyle.PROFESSIONAL,
            styleLabel = "Polish"
        )
        advanceUntilIdle()

        val state = viewModel.rewriteState.value
        assertNull(state.rewritten)
        assertEquals(
            "Rewrite is unavailable on this device right now — your wording is unchanged.",
            state.message
        )

        viewModel.clearMedicationRewrite()

        assertEquals(RewriteAssistUiState(), viewModel.rewriteState.value)
    }

    private fun medicationPlan(
        id: String,
        name: String = "test-$id",
        dosage: String = "10",
        unit: String = "mg",
        reminderMinuteOfDay: Int = 8 * 60,
        missedCount: Int = 0,
        isActive: Boolean = true,
        schedule: MedicationSchedule? = null,
        safetyProfile: MedicationSafetyProfile? = null
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = name,
        dosage = dosage,
        unit = unit,
        notes = null,
        startAt = LocalDateTime.of(2026, 5, 31, 8, 0),
        endAt = null,
        reminderMinuteOfDay = reminderMinuteOfDay,
        takeWithFood = false,
        missedCount = missedCount,
        refillNeededAfterDoses = null,
        isActive = isActive,
        schedule = schedule,
        safetyProfile = safetyProfile
    )

    private fun medicationSafetyProfile(planId: String, remaining: Int? = null, threshold: Int? = null): MedicationSafetyProfile =
        MedicationSafetyProfile(
            medicationPlanId = planId,
            supplyRemaining = remaining,
            refillThreshold = threshold,
            instructions = null
        )

    private fun medicationSchedule(
        id: String,
        medicationPlanId: String,
        recurrenceType: PlannerRecurrenceType,
        pausedUntil: LocalDate? = null
    ): MedicationSchedule = MedicationSchedule(
        id = id,
        medicationPlanId = medicationPlanId,
        recurrence = PlannerRecurrence(type = recurrenceType),
        pausedUntil = pausedUntil
    )
}
