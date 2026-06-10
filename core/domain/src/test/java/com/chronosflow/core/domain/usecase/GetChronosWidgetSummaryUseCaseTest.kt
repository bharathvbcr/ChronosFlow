package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.FocusSessionState
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.model.WidgetFocusState
import com.chronosflow.core.domain.repository.FocusSessionRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetChronosWidgetSummaryUseCaseTest {
    private val habitRepository: HabitRepository = mockk()
    private val medicationRepository: MedicationRepository = mockk()
    private val focusSessionRepository: FocusSessionRepository = mockk()
    private val timeBlockRepository: TimeBlockRepository = mockk()
    private val useCase = GetChronosWidgetSummaryUseCase(
        habitRepository = habitRepository,
        medicationRepository = medicationRepository,
        focusSessionRepository = focusSessionRepository,
        timeBlockRepository = timeBlockRepository
    )

    private val date = LocalDate.of(2026, 6, 10)

    @Test
    fun `selects first active habit and medication for widget`() = runTest {
        every { habitRepository.observeHabits() } returns flowOf(
            listOf(
                UseCaseTestFixtures.habit(id = "inactive-habit", title = "Archived", isActive = false),
                UseCaseTestFixtures.habit(id = "habit-1", title = "Morning walk", isActive = true)
            )
        )
        every { medicationRepository.observeMedicationPlans() } returns flowOf(
            listOf(
                UseCaseTestFixtures.medicationPlan(
                    id = "inactive-medication",
                    name = "Archived med",
                    isActive = false
                ),
                UseCaseTestFixtures.medicationPlan(
                    id = "medication-1",
                    name = "Vitamin D",
                    isActive = true
                )
            )
        )
        every { focusSessionRepository.observeRecoverableSession() } returns flowOf(FocusSessionState.Idle)
        every { timeBlockRepository.getTimeBlocksByDate(any()) } returns flowOf(emptyList())

        val summary = useCase()

        assertEquals("habit-1", summary.habitId)
        assertEquals("Morning walk", summary.habitTitle)
        assertEquals("medication-1", summary.medicationId)
        assertEquals("Vitamin D", summary.medicationName)
    }

    @Test
    fun `running session reports running state and remaining seconds`() = runTest {
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        every { medicationRepository.observeMedicationPlans() } returns flowOf(emptyList())
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(emptyList())
        val now = Instant.parse("2026-06-10T09:00:00Z")
        every { focusSessionRepository.observeRecoverableSession() } returns flowOf(
            FocusSessionState.Running(
                sessionId = "s1",
                blockId = null,
                startedAt = now,
                plannedEndAt = now.plusSeconds(600)
            )
        )

        val summary = useCase(today = date, nowMinuteOfDay = 9 * 60, now = now)

        assertEquals(WidgetFocusState.RUNNING, summary.focusState)
        assertEquals(600, summary.focusTimeLeftSeconds)
    }

    @Test
    fun `idle session surfaces current and next blocks`() = runTest {
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        every { medicationRepository.observeMedicationPlans() } returns flowOf(emptyList())
        every { focusSessionRepository.observeRecoverableSession() } returns flowOf(FocusSessionState.Idle)
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(
            listOf(
                block("now", startMinute = 9 * 60, durationMinutes = 60),
                block("next", startMinute = 11 * 60, durationMinutes = 30),
                block("past", startMinute = 7 * 60, durationMinutes = 30)
            )
        )

        val summary = useCase(
            today = date,
            nowMinuteOfDay = 9 * 60 + 30,
            now = Instant.parse("2026-06-10T09:30:00Z")
        )

        assertEquals(WidgetFocusState.IDLE, summary.focusState)
        assertEquals("now", summary.currentBlockTitle)
        assertEquals("next", summary.nextBlockTitle)
        assertEquals(11 * 60, summary.nextBlockStartMinuteOfDay)
    }

    @Test
    fun `no blocks leaves schedule fields null`() = runTest {
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        every { medicationRepository.observeMedicationPlans() } returns flowOf(emptyList())
        every { focusSessionRepository.observeRecoverableSession() } returns flowOf(FocusSessionState.Idle)
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(emptyList())

        val summary = useCase(
            today = date,
            nowMinuteOfDay = 12 * 60,
            now = Instant.parse("2026-06-10T12:00:00Z")
        )

        assertNull(summary.currentBlockTitle)
        assertNull(summary.nextBlockTitle)
        assertNull(summary.nextBlockStartMinuteOfDay)
    }

    private fun block(title: String, startMinute: Int, durationMinutes: Int): TimeBlock = TimeBlock(
        id = title,
        date = date,
        title = title,
        category = "work",
        startMinuteOfDay = startMinute,
        durationMinutes = durationMinutes,
        timezone = "UTC",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.MOVABLE,
        energyLevel = EnergyIntensity.fromLevel(2),
        source = "test",
        taskId = null,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
