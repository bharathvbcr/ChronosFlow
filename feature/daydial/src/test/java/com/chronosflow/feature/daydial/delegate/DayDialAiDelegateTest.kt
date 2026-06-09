package com.chronosflow.feature.daydial.delegate

import app.cash.turbine.test
import com.chronosflow.core.ai.ChronosAIPlanner
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.ProposedSuggestionBlock
import com.chronosflow.core.ai.StructuredDayPlanSuggestion
import com.chronosflow.core.data.privacy.AssistantPreferences
import com.chronosflow.core.ai.genai.GenAiRuntimeStatus
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.ApplyAiPlanUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialAiDelegateTest {

    private val repository: TimeBlockRepository = mockk()
    private val sleepScheduleRepository: SleepScheduleRepository = mockk()
    private val aiPlanner: ChronosAIPlanner = mockk()
    private val applyAiPlanUseCase: ApplyAiPlanUseCase = mockk()
    private val assistantPreferences: AssistantPreferences = mockk(relaxed = true)
    private val runtimeStatus = MutableStateFlow(GenAiRuntimeStatus())

    init {
        every { aiPlanner.genAiRuntimeStatus } returns runtimeStatus
        coEvery { aiPlanner.refreshGenAiStatus() } returns runtimeStatus.value
        coEvery { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        every { assistantPreferences.assistantPrivacyModeValue() } returns PrivacyMode.ON_DEVICE_ONLY.name
        every { assistantPreferences.preferPreviewNanoModel() } returns false
    }

    private val delegate = DayDialAiDelegate(
        repository = repository,
        sleepScheduleRepository = sleepScheduleRepository,
        aiPlanner = aiPlanner,
        planExplainAssistPlanner = mockk(relaxed = true),
        recommendationPlanInterpreter = mockk(relaxed = true),
        applyAiPlanUseCase = applyAiPlanUseCase,
        assistantPreferences = assistantPreferences
    )

    @Test
    fun initialPrivacyModeUsesPersistedAssistantPreference() = runTest(UnconfinedTestDispatcher()) {
        every { assistantPreferences.assistantPrivacyModeValue() } returns PrivacyMode.CLOUD_ALLOWED.name

        val persistedDelegate = DayDialAiDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            aiPlanner = aiPlanner,
            planExplainAssistPlanner = mockk(relaxed = true),
            recommendationPlanInterpreter = mockk(relaxed = true),
            applyAiPlanUseCase = applyAiPlanUseCase,
            assistantPreferences = assistantPreferences
        )

        persistedDelegate.privacyMode.test {
            assertEquals(PrivacyMode.CLOUD_ALLOWED, awaitItem())
        }
    }

    @Test
    fun setPrivacyModePersistsAssistantPreference() {
        delegate.setPrivacyMode(PrivacyMode.CLOUD_ALLOWED)

        io.mockk.verify {
            assistantPreferences.setAssistantPrivacyModeValue(PrivacyMode.CLOUD_ALLOWED.name)
        }
    }

    @Test
    fun previewModelFlagUsesPersistedAssistantPreference() = runTest(UnconfinedTestDispatcher()) {
        every { assistantPreferences.preferPreviewNanoModel() } returns true

        val persistedDelegate = DayDialAiDelegate(
            repository = repository,
            sleepScheduleRepository = sleepScheduleRepository,
            aiPlanner = aiPlanner,
            planExplainAssistPlanner = mockk(relaxed = true),
            recommendationPlanInterpreter = mockk(relaxed = true),
            applyAiPlanUseCase = applyAiPlanUseCase,
            assistantPreferences = assistantPreferences
        )

        persistedDelegate.previewOnDeviceModel.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun setPreviewOnDeviceModelPersistsPreference() {
        delegate.setPreviewOnDeviceModel(true)

        io.mockk.verify {
            assistantPreferences.setPreferPreviewNanoModel(true)
        }
    }

    @Test
    fun rejectAllSuggestionsClearsStagedAiBlocks() = runTest(UnconfinedTestDispatcher()) {
        val date = LocalDate.of(2026, 5, 8)
        coEvery { repository.getTimeBlocksByDate(date) } returns flowOf(emptyList())
        coEvery {
            aiPlanner.generateReviewBackedDayPlan(any(), any(), any(), any(), any())
        } returns structuredSuggestion()

        delegate.requestPlan(
            scope = this,
            date = date,
            goals = listOf("protect focus"),
            reviewProvider = { reviewSummary(date) }
        )

        delegate.suggestedBlocks.test {
            val suggestions = awaitItem()
            assertEquals(2, suggestions.size)

            delegate.rejectAllSuggestions()
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun requestPlanClearsPreviousSuggestionsBeforePublishingFreshOnes() = runTest(UnconfinedTestDispatcher()) {
        val date = LocalDate.of(2026, 5, 8)
        val secondSuggestionDeferred = CompletableDeferred<StructuredDayPlanSuggestion>()
        var requestCount = 0
        coEvery { repository.getTimeBlocksByDate(date) } returns flowOf(emptyList())
        coEvery {
            aiPlanner.generateReviewBackedDayPlan(any(), any(), any(), any(), any())
        } coAnswers {
            requestCount += 1
            if (requestCount == 1) {
                structuredSuggestion()
            } else {
                secondSuggestionDeferred.await()
            }
        }

        delegate.requestPlan(
            scope = this,
            date = date,
            goals = listOf("protect focus"),
            reviewProvider = { reviewSummary(date) }
        )

        delegate.suggestedBlocks.test {
            assertEquals(2, awaitItem().size)

            delegate.requestPlan(
                scope = this@runTest,
                date = date,
                goals = listOf("rebalance"),
                reviewProvider = { reviewSummary(date) }
            )

            assertTrue(awaitItem().isEmpty())
            secondSuggestionDeferred.complete(
                structuredSuggestion(
                    reason = "Second pass",
                    explanation = "Fresh suggestions",
                    suggestions = listOf(suggestion("suggestion-3", "Admin wrap-up"))
                )
            )
            val refreshed = awaitItem()
            assertEquals(1, refreshed.size)
            assertEquals("suggestion-3", refreshed.single().id)
        }
    }

    @Test
    fun requestPlanFiltersSuggestionsThatOverlapSleepWindow() = runTest(UnconfinedTestDispatcher()) {
        val date = LocalDate.of(2026, 5, 8)
        coEvery { repository.getTimeBlocksByDate(date) } returns flowOf(emptyList())
        coEvery { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule(
            enabled = true,
            startMinute = 21 * 60,
            endMinute = 7 * 60
        )
        coEvery {
            aiPlanner.generateReviewBackedDayPlan(any(), any(), any(), any(), any())
        } returns structuredSuggestion(
            suggestions = listOf(
                suggestion("sleep", "Late admin", startMinuteOfDay = 22 * 60),
                suggestion("day", "Morning focus", startMinuteOfDay = 9 * 60)
            )
        )

        delegate.requestPlan(
            scope = this,
            date = date,
            goals = listOf("protect focus"),
            reviewProvider = { reviewSummary(date) }
        )

        delegate.suggestedBlocks.test {
            val suggestions = awaitItem()
            assertEquals(1, suggestions.size)
            assertEquals("day", suggestions.single().id)
        }
    }

    @Test
    fun requestPlanStagesSuggestionsWithoutApplyingThem() = runTest(UnconfinedTestDispatcher()) {
        val date = LocalDate.of(2026, 5, 8)
        coEvery { repository.getTimeBlocksByDate(date) } returns flowOf(emptyList())
        coEvery {
            aiPlanner.generateReviewBackedDayPlan(any(), any(), any(), any(), any())
        } returns structuredSuggestion()

        delegate.requestPlan(
            scope = this,
            date = date,
            goals = listOf("protect focus"),
            reviewProvider = { reviewSummary(date) }
        )

        delegate.suggestedBlocks.test {
            assertEquals(2, awaitItem().size)
        }
        coVerify(exactly = 0) { applyAiPlanUseCase(any()) }
    }

    @Test
    fun applyAiSuggestionsWritesOnlyAfterExplicitApplyAction() = runTest(UnconfinedTestDispatcher()) {
        val date = LocalDate.of(2026, 5, 8)
        coEvery { repository.getTimeBlocksByDate(date) } returns flowOf(emptyList())
        coEvery {
            aiPlanner.generateReviewBackedDayPlan(any(), any(), any(), any(), any())
        } returns structuredSuggestion()
        coEvery { applyAiPlanUseCase(any()) } returns 2

        delegate.requestPlan(
            scope = this,
            date = date,
            goals = listOf("protect focus"),
            reviewProvider = { reviewSummary(date) }
        )
        delegate.suggestedBlocks.test {
            assertEquals(2, awaitItem().size)
        }

        delegate.applyAiSuggestions(
            scope = this,
            date = date,
            onResult = { result, _ ->
                assertEquals("Applied 2 AI suggestions", result.message)
            }
        )

        coVerify(exactly = 1) { applyAiPlanUseCase(match { it.size == 2 }) }
    }

    private fun structuredSuggestion(
        reason: String = "Review-backed plan",
        explanation: String = "Review these suggestions before applying them.",
        suggestions: List<ProposedSuggestionBlock> = listOf(
            suggestion("suggestion-1", "Deep work"),
            suggestion("suggestion-2", "Recovery")
        )
    ): StructuredDayPlanSuggestion = StructuredDayPlanSuggestion(
        proposedBlocks = suggestions,
        reason = reason,
        conflictsResolved = listOf("Protected focus"),
        requireConfirmation = true,
        explanation = explanation
    )

    private fun suggestion(
        id: String,
        title: String,
        startMinuteOfDay: Int = 9 * 60
    ): ProposedSuggestionBlock = ProposedSuggestionBlock(
        id = id,
        title = title,
        category = "AI",
        startMinuteOfDay = startMinuteOfDay,
        durationMinutes = 45,
        provenance = BlockProvenance.AI_SUGGESTED,
        flexibility = BlockFlexibility.OPTIONAL,
        isLocked = false,
        isProtected = title.contains("work", ignoreCase = true),
        timezone = "America/Chicago"
    )

    private fun reviewSummary(date: LocalDate): DailyReviewSummary = DailyReviewSummary(
        date = date,
        plannedMinutes = 120,
        actualMinutes = 60,
        missedMinutes = 30,
        driftMinutes = 60,
        completedBlockCount = 1,
        missedBlockCount = 1,
        insights = emptyList()
    )
}
