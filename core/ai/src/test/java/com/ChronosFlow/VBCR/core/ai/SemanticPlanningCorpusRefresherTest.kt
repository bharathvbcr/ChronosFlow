package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.data.privacy.PrivacyPreferences
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticPlanningCorpusRefresherTest {
    private val semanticIndex = SemanticPlanningIndex()
    private val appSearchBridge: SemanticAppSearchBridge = mockk()
    private val privacyPreferences: PrivacyPreferences = mockk()
    private val timeBlockRepository: TimeBlockRepository = mockk()
    private val reviewRepository: ReviewRepository = mockk()
    private val habitRepository: HabitRepository = mockk()
    private val medicationRepository: MedicationRepository = mockk()
    private val taskRepository: TaskRepository = mockk()
    private val focusSessionRepository: FocusSessionRepository = mockk()

    @Test
    fun `refresh rebuilds searchable corpus from domain sources`() = runTest {
        val today = LocalDate.of(2026, 5, 27)
        every { privacyPreferences.redactCommandPaletteHistory() } returns false
        every { timeBlockRepository.getTimeBlocksByDate(today) } returns flowOf(emptyList())
        every { reviewRepository.observeDailyReview(today) } returns flowOf(null)
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        every { medicationRepository.observeMedicationPlans() } returns flowOf(emptyList())
        every { focusSessionRepository.observeRecoverableSession() } returns flowOf(
            FocusSessionState.ServiceKilledRecoverable("session-1")
        )
        every { taskRepository.getAllTasks() } returns flowOf(
            listOf(
                Task(
                    id = "task-1",
                    title = "Prepare launch brief",
                    description = "Write the assistant-first rollout summary",
                    isCompleted = false,
                    priority = 2,
                    dueDate = null,
                    createdAt = Instant.parse("2026-05-20T09:00:00Z"),
                    updatedAt = Instant.parse("2026-05-20T09:00:00Z")
                )
            )
        )
        coEvery { appSearchBridge.syncDocuments() } returns true

        val refresher = SemanticPlanningCorpusRefresher(
            semanticIndex = semanticIndex,
            appSearchBridge = appSearchBridge,
            privacyPreferences = privacyPreferences,
            timeBlockRepository = timeBlockRepository,
            reviewRepository = reviewRepository,
            habitRepository = habitRepository,
            medicationRepository = medicationRepository,
            taskRepository = taskRepository,
            focusSessionRepository = focusSessionRepository
        )

        val appSearchEnabled = refresher.refresh(
            clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        )

        assertTrue(appSearchEnabled)
        assertEquals(SemanticDocumentType.TASK, semanticIndex.query("launch brief").first().type)
        assertEquals(SemanticDocumentType.FOCUS, semanticIndex.query("focus").first().type)
    }
}
