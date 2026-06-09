package com.chronosflow.feature.habits

import com.chronosflow.core.ai.HabitAssistPlanner
import com.chronosflow.core.ai.HabitAssistRequest
import com.chronosflow.core.ai.HabitAssistSuggestion
import com.chronosflow.core.ai.HabitRepairAssistPlanner
import com.chronosflow.core.ai.HabitRepairAssistResult
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.RoutineAssistSource
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.GenAiAssistUiSnapshot
import com.chronosflow.core.ai.genai.GenAiRuntimeStatus
import com.chronosflow.core.ai.genai.assistUiSnapshot
import com.chronosflow.core.ai.genai.refreshAssistUiSnapshot
import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.HabitEventType
import com.chronosflow.core.domain.model.HabitSchedule
import com.chronosflow.core.domain.model.PlannerRecurrence
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.PlannerPreferencesRepository
import com.chronosflow.core.domain.usecase.CompleteHabitUseCase
import com.chronosflow.core.domain.usecase.GetActiveHabitsUseCase
import com.chronosflow.core.domain.usecase.HabitStreak
import com.chronosflow.core.domain.usecase.ObserveHabitStreaksUseCase
import com.chronosflow.core.notifications.HabitReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitViewModelTest {
    private val habitRepository: HabitRepository = mockk()
    private val plannerPreferencesRepository: PlannerPreferencesRepository = mockk(relaxed = true)
    private val getActiveHabitsUseCase: GetActiveHabitsUseCase = mockk()
    private val observeHabitStreaksUseCase: ObserveHabitStreaksUseCase = mockk()
    private val completeHabitUseCase: CompleteHabitUseCase = mockk()
    private val habitAssistPlanner: HabitAssistPlanner = mockk()
    private val habitRepairAssistPlanner: HabitRepairAssistPlanner = mockk()
    private val habitReminderScheduler: HabitReminderScheduler = mockk(relaxed = true)
    private val genAiAssistCoordinator: GenAiAssistCoordinator = mockk()

    private val allHabits: MutableStateFlow<List<Habit>> = MutableStateFlow(emptyList())
    private val activeHabits: MutableStateFlow<List<Habit>> = MutableStateFlow(emptyList())
    private val streaks: MutableStateFlow<List<HabitStreak>> = MutableStateFlow(emptyList())
    private val assistantRuntimeStatus: MutableStateFlow<GenAiRuntimeStatus> =
        MutableStateFlow(GenAiRuntimeStatus())
    private val defaultAssistSnapshot: GenAiAssistUiSnapshot
        get() = genAiAssistCoordinator.assistUiSnapshot()

    private lateinit var viewModel: HabitViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { habitRepository.observeHabits() } returns allHabits
        every { getActiveHabitsUseCase() } returns activeHabits
        every { observeHabitStreaksUseCase() } returns streaks
        coEvery { habitRepairAssistPlanner.suggestRepairs(any(), any(), any()) } returns emptyList()
        coEvery { habitAssistPlanner.suggest(any()) } returns emptyList()
        coEvery { habitRepository.saveHabit(any()) } returns Unit
        every { genAiAssistCoordinator.privacyMode() } returns PrivacyMode.ON_DEVICE_ONLY
        every { genAiAssistCoordinator.runtimeStatus } returns assistantRuntimeStatus
        coEvery { genAiAssistCoordinator.refreshRuntimeStatus() } returns GenAiRuntimeStatus()
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addHabit is ignored when title is blank`() = runTest {
        viewModel.addHabit("   ")
        runCurrent()

        coVerify(exactly = 0) { habitRepository.saveHabit(any()) }
        coVerify(exactly = 0) { habitReminderScheduler.syncUpcomingHabitReminder(any()) }
    }

    @Test
    fun `addHabit normalizes title and clamps numeric window and difficulty`() = runTest {
        viewModel.addHabit(
            title = "  Morning run  ",
            cadence = "Daily",
            windowStartMinute = -20,
            windowEndMinute = 5,
            difficulty = 10,
            isBundled = true
        )
        runCurrent()

        coVerify {
            habitRepository.saveHabit(
                match {
                    it.title == "Morning run" &&
                        it.windowStartMinute == 0 &&
                        it.windowEndMinute == 15 &&
                        it.difficulty == 5 &&
                        it.isBundled &&
                        it.cadence == "Daily" &&
                        it.schedule?.targetStartMinute == 0 &&
                        it.schedule?.targetEndMinute == 15
                }
            )
        }
        coVerify { habitReminderScheduler.syncUpcomingHabitReminder(any(), any(), any()) }
    }

    @Test
    fun `updateHabit is ignored when title is blank`() = runTest {
        val habit = habit(id = "h-update", title = "Morning")

        viewModel.updateHabit(
            habit = habit,
            title = "   ",
            cadence = "Daily",
            windowStartMinute = 120,
            windowEndMinute = 240,
            difficulty = 3,
            isBundled = false
        )
        runCurrent()

        coVerify(exactly = 0) { habitRepository.saveHabit(any()) }
    }

    @Test
    fun `updateHabit trims title and clamps values`() = runTest {
        val habit = habit(id = "h-update", title = "Morning")
        coEvery { habitRepository.saveHabit(any()) } returns Unit

        viewModel.updateHabit(
            habit = habit,
            title = "  Evening walk  ",
            cadence = "   Weekly   ",
            windowStartMinute = 2000,
            windowEndMinute = -10,
            difficulty = 0,
            isBundled = false,
            schedule = null
        )
        runCurrent()

        coVerify {
            habitRepository.saveHabit(
                match {
                    it.id == "h-update" &&
                        it.title == "Evening walk" &&
                        it.windowStartMinute == 1425 &&
                        it.windowEndMinute == 1440 &&
                        it.difficulty == 1 &&
                        it.cadence == "Weekly" &&
                        it.schedule?.targetStartMinute == 1425 &&
                        it.schedule?.targetEndMinute == 1440
                }
            )
        }
        coVerify { habitReminderScheduler.syncUpcomingHabitReminder(any(), any(), any()) }
    }

    @Test
    fun `completeHabit uses updated habit when repository lookup is empty`() = runTest {
        val habit = habit(id = "h-complete", title = "Read")
        coEvery { completeHabitUseCase(any(), any()) } returns Unit
        coEvery { habitRepository.getHabitById("h-complete") } returns null

        val completeDate = LocalDate.of(2026, 5, 30)
        viewModel.completeHabit(habit, completeDate)
        runCurrent()

        coVerify { completeHabitUseCase(habit, completeDate) }
        coVerify { habitRepository.getHabitById("h-complete") }
        coVerify {
            habitReminderScheduler.syncUpcomingHabitReminder(
                match {
                    it.id == "h-complete" && it.lastCompletedDate == completeDate
                },
                any(),
                any()
            )
        }
    }

    @Test
    fun `pauseHabit saves paused state and appends pause event`() = runTest {
        val habit = habit(id = "h-pause", title = "Meditate", isBundled = false)
        coEvery { habitRepository.saveHabit(any()) } returns Unit
        coEvery { habitRepository.addHabitEvent(any()) } returns Unit

        viewModel.pauseHabit(habit, days = 2, reason = "Focus day")
        runCurrent()

        coVerify {
            habitRepository.saveHabit(
                match {
                    it.id == "h-pause" &&
                        it.schedule?.pausedUntil == LocalDate.now().plusDays(2)
                }
            )
        }
        coVerify {
            habitRepository.addHabitEvent(
                match {
                    it.type == HabitEventType.PAUSED &&
                        it.habitId == "h-pause" &&
                        it.reason == "Focus day" &&
                        it.eventDate == LocalDate.now()
                }
            )
        }
        coVerify { habitReminderScheduler.syncUpcomingHabitReminder(any(), any(), any()) }
    }

    @Test
    fun `resumeHabit clears pause state and appends resume event`() = runTest {
        val habit = habit(
            id = "h-resume",
            title = "Read",
            isBundled = false,
            schedule = schedule("h-resume", "h-resume")
        )
        coEvery { habitRepository.saveHabit(any()) } returns Unit
        coEvery { habitRepository.addHabitEvent(any()) } returns Unit

        viewModel.resumeHabit(habit)
        runCurrent()

        coVerify {
            habitRepository.addHabitEvent(
                match {
                    it.type == HabitEventType.RESUMED &&
                        it.habitId == "h-resume" &&
                        it.reason == "Resumed" &&
                        it.eventDate == LocalDate.now()
                }
            )
        }
        coVerify {
            habitRepository.saveHabit(
                match {
                    it.id == "h-resume" &&
                        it.schedule?.pausedUntil == null &&
                        it.schedule?.skipDate == null &&
                        it.schedule?.deferUntilMinuteOfDay == null
                }
            )
        }
    }

    @Test
    fun `skipHabitToday appends skip event and updates schedule`() = runTest {
        val habit = habit(id = "h-skip", title = "Stretch", isBundled = false)
        coEvery { habitRepository.saveHabit(any()) } returns Unit
        coEvery { habitRepository.addHabitEvent(any()) } returns Unit

        viewModel.skipHabitToday(habit)
        runCurrent()

        coVerify {
            habitRepository.saveHabit(
                match {
                    it.id == "h-skip" &&
                        it.schedule?.skipDate == LocalDate.now()
                }
            )
        }
        coVerify {
            habitRepository.addHabitEvent(
                match {
                    it.type == HabitEventType.SKIPPED &&
                        it.habitId == "h-skip" &&
                        it.reason == "Skipped today"
                }
            )
        }
        coVerify { habitReminderScheduler.syncUpcomingHabitReminder(any(), any(), any()) }
    }

    @Test
    fun `deferHabit saves deferred schedule and records defer event`() = runTest {
        val habit = habit(id = "h-defer", title = "Run", isBundled = false)
        coEvery { habitRepository.saveHabit(any()) } returns Unit
        coEvery { habitRepository.addHabitEvent(any()) } returns Unit

        viewModel.deferHabit(habit, minutes = 25, reason = "Late")
        runCurrent()

        coVerify {
            habitRepository.saveHabit(
                match {
                    it.id == "h-defer" &&
                        it.schedule?.deferUntilMinuteOfDay != null &&
                        it.schedule?.deferUntilMinuteOfDay in 0..1439
                }
            )
        }
        coVerify {
            habitRepository.addHabitEvent(
                match {
                    it.type == HabitEventType.DEFERRED &&
                        it.habitId == "h-defer" &&
                        it.reason == "Late"
                }
            )
        }
    }

    @Test
    fun `archiveHabit disables habit and cancels reminders`() = runTest {
        val habit = habit(id = "h-archive", title = "Archive me", isActive = true)
        coEvery { habitRepository.saveHabit(any()) } returns Unit

        viewModel.archiveHabit(habit)
        runCurrent()

        coVerify {
            habitRepository.saveHabit(
                match { !it.isActive && it.id == "h-archive" }
            )
        }
        coVerify { habitReminderScheduler.cancelUpcomingHabitReminders("h-archive", any()) }
    }

    @Test
    fun `duplicateHabit copies habit and resets progress counters`() = runTest {
        val original = habit(id = "h-duplicate", title = "Write", streakCount = 3, lastCompletedDate = LocalDate.now())
        coEvery { habitRepository.saveHabit(any()) } returns Unit

        viewModel.duplicateHabit(original)
        runCurrent()

        coVerify {
            habitRepository.saveHabit(
                match {
                    it.title == "Write (copy)" &&
                        it.streakCount == 0 &&
                        it.lastCompletedDate == null &&
                        it.isActive &&
                        it.schedule == null
                }
            )
        }
    }

    @Test
    fun `rememberHistoryTemplateSelection prepends selection and persists ids`() = runTest {
        viewModel = createViewModel(listOf("one", "two", "three", "four", "five"))
        viewModel.rememberHistoryTemplateSelection("three")

        assertEquals(listOf("three", "one", "two", "four", "five"), viewModel.recentHistoryTemplateIds.value)
        verify { plannerPreferencesRepository.saveRecentHabitTemplateIds(listOf("three", "one", "two", "four", "five")) }
    }

    @Test
    fun `rememberHistoryTemplateSelection keeps selected id unique and capped`() = runTest {
        viewModel = createViewModel(listOf("one", "two", "three", "four", "five"))
        viewModel.rememberHistoryTemplateSelection("new")

        assertEquals(listOf("new", "one", "two", "three", "four"), viewModel.recentHistoryTemplateIds.value)
        verify { plannerPreferencesRepository.saveRecentHabitTemplateIds(listOf("new", "one", "two", "three", "four")) }
    }

    @Test
    fun `requestHabitAssist saves non-empty suggestions and snapshot`() = runTest {
        val request = HabitAssistRequest(
            title = "Read",
            cadence = "Daily",
            startMinute = 8 * 60,
            endMinute = 20 * 60,
            difficulty = 2,
            isBundled = false
        )
        val suggestions = listOf(
            HabitAssistSuggestion.Title(
                id = "local:habit:title:1",
                label = "Read",
                reason = "Local fallback",
                source = RoutineAssistSource.LOCAL,
                title = "Read"
            )
        )
        coEvery { habitAssistPlanner.suggest(request) } returns suggestions

        viewModel.requestHabitAssist(request)
        runCurrent()

        val snapshot = viewModel.assistState.value
        assertEquals(false, snapshot.isLoading)
        assertEquals(defaultAssistSnapshot, snapshot.assistSnapshot)
        assertEquals(suggestions, snapshot.suggestions)
        assertEquals(null, snapshot.message)
    }

    @Test
    fun `requestHabitAssist handles no suggestions from planner`() = runTest {
        val request = HabitAssistRequest(
            title = "Read",
            cadence = "Daily",
            startMinute = 8 * 60,
            endMinute = 20 * 60,
            difficulty = 2,
            isBundled = false
        )
        coEvery { habitAssistPlanner.suggest(request) } returns emptyList()

        viewModel.requestHabitAssist(request)
        runCurrent()

        val snapshot = viewModel.assistState.value
        assertEquals(false, snapshot.isLoading)
        assertEquals(defaultAssistSnapshot, snapshot.assistSnapshot)
        assertEquals("No suggestions available", snapshot.message)
        assertEquals(emptyList<HabitAssistSuggestion>(), snapshot.suggestions)
    }

    @Test
    fun `requestHabitAssist surfaces snapshot and message on planner failure`() = runTest {
        val request = HabitAssistRequest(
            title = "Read",
            cadence = "Daily",
            startMinute = 8 * 60,
            endMinute = 20 * 60,
            difficulty = 2,
            isBundled = false
        )
        coEvery { habitAssistPlanner.suggest(any()) } throws IllegalStateException("planner down")
        every { genAiAssistCoordinator.privacyMode() } returns PrivacyMode.ON_DEVICE_ONLY

        viewModel.requestHabitAssist(request)
        runCurrent()

        val snapshot = viewModel.assistState.value
        assertEquals(false, snapshot.isLoading)
        assertEquals(defaultAssistSnapshot, snapshot.assistSnapshot)
        assertEquals("planner down", snapshot.message)
        assertEquals(emptyList<HabitAssistSuggestion>(), snapshot.suggestions)
    }

    @Test
    fun `clearHabitAssist resets UI state`() = runTest {
        val request = HabitAssistRequest(
            title = "Read",
            cadence = "Daily",
            startMinute = 8 * 60,
            endMinute = 20 * 60,
            difficulty = 2,
            isBundled = false
        )
        coEvery { habitAssistPlanner.suggest(request) } returns listOf(
            HabitAssistSuggestion.Title(
                id = "local:habit:title:1",
                label = "Read",
                reason = "Local fallback",
                source = RoutineAssistSource.LOCAL,
                title = "Read"
            )
        )
        coEvery { habitRepairAssistPlanner.suggestRepairs(any(), any(), any()) } returns emptyList()

        viewModel.requestHabitAssist(request)
        runCurrent()
        viewModel.clearHabitAssist()

        assertEquals(HabitAssistUiState(), viewModel.assistState.value)
    }

    @Test
    fun `repair suggestion uses planner output when habits become due`() = runTest {
        coEvery { habitRepairAssistPlanner.suggestRepairs(any(), any(), any()) } returns
            listOf(
                HabitRepairAssistResult(
                    habitId = "h-repair",
                    suggestedStartMinute = 480,
                    suggestedEndMinute = 500,
                    reason = "Recovery needed",
                    source = RoutineAssistSource.LOCAL
                )
            )
        coEvery { habitReminderScheduler.cancelUpcomingHabitReminders(any()) } returns Unit
        every { getActiveHabitsUseCase() } returns MutableStateFlow(
            listOf(
                habit(
                    id = "h-repair",
                    title = "Repair this",
                    windowEndMinute = 1,
                    lastCompletedDate = LocalDate.of(2026, 5, 29)
                )
            )
        )

        viewModel = createViewModel()
        runCurrent()

        assertEquals(1, viewModel.repairSuggestions.value.size)
        assertEquals("h-repair", viewModel.repairSuggestions.value.single().habit.id)
        assertEquals(480, viewModel.repairSuggestions.value.single().suggestedStartMinute)
        assertEquals("Recovery needed", viewModel.repairSuggestions.value.single().reason)
    }

    private fun createViewModel(
        recentTemplateIds: List<String> = listOf("recent-template-1")
    ): HabitViewModel {
        every { plannerPreferencesRepository.getRecentHabitTemplateIds() } returns recentTemplateIds
        return HabitViewModel(
            habitRepository = habitRepository,
            plannerPreferencesRepository = plannerPreferencesRepository,
            getActiveHabitsUseCase = getActiveHabitsUseCase,
            observeHabitStreaksUseCase = observeHabitStreaksUseCase,
            completeHabitUseCase = completeHabitUseCase,
            habitAssistPlanner = habitAssistPlanner,
            habitRepairAssistPlanner = habitRepairAssistPlanner,
            habitReminderScheduler = habitReminderScheduler,
            genAiAssistCoordinator = genAiAssistCoordinator
        )
    }

    private fun habit(
        id: String,
        title: String,
        isBundled: Boolean = false,
        schedule: HabitSchedule? = null,
        streakCount: Int = 0,
        difficulty: Int = 2,
        lastCompletedDate: LocalDate? = null,
        isActive: Boolean = true,
        windowEndMinute: Int = 9 * 60
    ): Habit = Habit(
        id = id,
        title = title,
        cadence = "Daily",
        windowStartMinute = 8 * 60,
        windowEndMinute = windowEndMinute,
        difficulty = difficulty,
        isBundled = isBundled,
        streakCount = streakCount,
        lastCompletedDate = lastCompletedDate,
        isActive = isActive,
        schedule = schedule
    )

    private fun schedule(
        id: String,
        habitId: String = "h-duplicate"
    ): HabitSchedule = HabitSchedule(
        id = id,
        habitId = habitId,
        recurrence = PlannerRecurrence(type = PlannerRecurrenceType.WEEKDAYS),
        targetStartMinute = 8 * 60,
        targetEndMinute = 9 * 60
    )
}
