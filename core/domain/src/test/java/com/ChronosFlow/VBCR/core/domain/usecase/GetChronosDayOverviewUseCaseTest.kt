package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.WidgetFocusState
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetChronosDayOverviewUseCaseTest {
    private val timeBlockRepository: TimeBlockRepository = mockk()
    private val taskRepository: TaskRepository = mockk()
    private val habitRepository: HabitRepository = mockk()
    private val medicationRepository: MedicationRepository = mockk()
    private val focusSessionRepository: FocusSessionRepository = mockk()
    private val useCase = GetChronosDayOverviewUseCase(
        timeBlockRepository = timeBlockRepository,
        taskRepository = taskRepository,
        habitRepository = habitRepository,
        medicationRepository = medicationRepository,
        focusSessionRepository = focusSessionRepository
    )

    private val date = UseCaseTestFixtures.date

    private fun stubEmpty() {
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(emptyList())
        every { taskRepository.getAllTasks() } returns flowOf(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        every { medicationRepository.observeMedicationPlans() } returns flowOf(emptyList())
        every { focusSessionRepository.observeRecoverableSession() } returns flowOf(FocusSessionState.Idle)
    }

    @Test
    fun `drops past blocks and flags the current one`() = runTest {
        stubEmpty()
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(
            listOf(
                UseCaseTestFixtures.timeBlock(id = "past", startMinute = 7 * 60, durationMinutes = 30),
                UseCaseTestFixtures.timeBlock(id = "now", startMinute = 9 * 60, durationMinutes = 60),
                UseCaseTestFixtures.timeBlock(id = "next", startMinute = 11 * 60, durationMinutes = 30)
            )
        )

        val overview = useCase(today = date, nowMinuteOfDay = 9 * 60 + 30)

        assertEquals(listOf("now", "next"), overview.blocks.map { it.id })
        assertEquals("now", overview.currentBlock?.id)
        assertTrue(overview.currentBlock!!.isCurrent)
        assertEquals(10 * 60, overview.currentBlock!!.endMinuteOfDay)
        assertEquals("next", overview.nextBlock?.id)
        assertFalse(overview.nextBlock!!.isCurrent)
    }

    @Test
    fun `drops completed blocks even when their planned window is still open`() = runTest {
        stubEmpty()
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(
            listOf(
                UseCaseTestFixtures.timeBlock(id = "done", startMinute = 9 * 60, durationMinutes = 60)
                    .copy(actualStartMinuteOfDay = 9 * 60, actualEndMinuteOfDay = 9 * 60 + 20),
                UseCaseTestFixtures.timeBlock(id = "next", startMinute = 11 * 60, durationMinutes = 30)
            )
        )

        val overview = useCase(today = date, nowMinuteOfDay = 9 * 60 + 30)

        assertEquals(listOf("next"), overview.blocks.map { it.id })
        assertNull(overview.currentBlock)
        assertEquals("next", overview.nextBlock?.id)
    }

    @Test
    fun `all-day calendar notes are not treated as the current block`() = runTest {
        stubEmpty()
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(
            listOf(
                UseCaseTestFixtures.timeBlock(id = "birthday", startMinute = 0, durationMinutes = 1440).copy(
                    title = "Birthday",
                    provenance = BlockProvenance.CALENDAR_IMPORTED,
                    calendarEventId = 42L
                ),
                UseCaseTestFixtures.timeBlock(id = "now", startMinute = 9 * 60, durationMinutes = 60)
            )
        )

        val overview = useCase(today = date, nowMinuteOfDay = 9 * 60 + 30)

        assertEquals(listOf("now"), overview.blocks.map { it.id })
        assertEquals("now", overview.currentBlock?.id)
    }

    @Test
    fun `no current block leaves currentBlock null but keeps upcoming`() = runTest {
        stubEmpty()
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(
            listOf(UseCaseTestFixtures.timeBlock(id = "later", startMinute = 15 * 60, durationMinutes = 45))
        )

        val overview = useCase(today = date, nowMinuteOfDay = 12 * 60)

        assertNull(overview.currentBlock)
        assertEquals("later", overview.nextBlock?.id)
    }

    @Test
    fun `open tasks are sorted urgent first then oldest first`() = runTest {
        stubEmpty()
        val older = Instant.parse("2026-05-20T10:00:00Z")
        val newer = Instant.parse("2026-05-26T10:00:00Z")
        every { taskRepository.getAllTasks() } returns flowOf(
            listOf(
                UseCaseTestFixtures.task(id = "done", isCompleted = true),
                UseCaseTestFixtures.task(id = "normal-new").copy(priority = 0, createdAt = newer),
                UseCaseTestFixtures.task(id = "urgent").copy(priority = 2, createdAt = newer),
                UseCaseTestFixtures.task(id = "normal-old").copy(priority = 0, createdAt = older)
            )
        )

        val overview = useCase(today = date, nowMinuteOfDay = 12 * 60)

        assertEquals(listOf("urgent", "normal-old", "normal-new"), overview.openTasks.map { it.id })
    }

    @Test
    fun `habits keep only active ones with undone first and done-today flag`() = runTest {
        stubEmpty()
        every { habitRepository.observeHabits() } returns flowOf(
            listOf(
                UseCaseTestFixtures.habit(
                    id = "done-today",
                    title = "Stretch",
                    streakCount = 4,
                    lastCompletedDate = date
                ),
                UseCaseTestFixtures.habit(
                    id = "archived",
                    title = "Old habit",
                    isActive = false
                ),
                UseCaseTestFixtures.habit(
                    id = "pending",
                    title = "Morning walk",
                    streakCount = 7,
                    lastCompletedDate = date.minusDays(1)
                )
            )
        )

        val overview = useCase(today = date, nowMinuteOfDay = 12 * 60)

        assertEquals(listOf("pending", "done-today"), overview.habits.map { it.id })
        assertFalse(overview.habits[0].isDoneToday)
        assertTrue(overview.habits[1].isDoneToday)
        assertEquals(1, overview.habitsDoneToday)
        assertEquals(7, overview.habits[0].streakCount)
    }

    @Test
    fun `medications keep active plans pending first with taken-today from dose events`() = runTest {
        stubEmpty()
        val takenToday = UseCaseTestFixtures.medicationPlan(id = "taken", name = "Vitamin D").copy(
            reminderMinuteOfDay = 8 * 60,
            recentDoseEvents = listOf(
                doseEvent(planId = "taken", type = MedicationDoseEventType.TAKEN, eventDate = date)
            )
        )
        val takenYesterday = UseCaseTestFixtures.medicationPlan(id = "pending", name = "Iron").copy(
            reminderMinuteOfDay = 20 * 60,
            recentDoseEvents = listOf(
                doseEvent(planId = "pending", type = MedicationDoseEventType.TAKEN, eventDate = date.minusDays(1))
            )
        )
        val inactive = UseCaseTestFixtures.medicationPlan(id = "inactive", isActive = false)
        every { medicationRepository.observeMedicationPlans() } returns flowOf(
            listOf(takenToday, takenYesterday, inactive)
        )

        val overview = useCase(today = date, nowMinuteOfDay = 12 * 60)

        assertEquals(listOf("pending", "taken"), overview.medications.map { it.id })
        assertFalse(overview.medications[0].isTakenToday)
        assertTrue(overview.medications[1].isTakenToday)
        assertEquals("1 tablet", overview.medications[0].doseLabel)
    }

    @Test
    fun `running focus session reports state and remaining seconds`() = runTest {
        stubEmpty()
        val now = Instant.parse("2026-05-27T09:00:00Z")
        every { focusSessionRepository.observeRecoverableSession() } returns flowOf(
            FocusSessionState.Running(
                sessionId = "s1",
                blockId = null,
                startedAt = now,
                plannedEndAt = now.plusSeconds(600)
            )
        )

        val overview = useCase(today = date, nowMinuteOfDay = 9 * 60, now = now)

        assertEquals(WidgetFocusState.RUNNING, overview.focus.state)
        assertEquals(600, overview.focus.timeLeftSeconds)
    }

    private fun doseEvent(
        planId: String,
        type: MedicationDoseEventType,
        eventDate: LocalDate
    ) = MedicationDoseEvent(
        id = "$planId-$eventDate",
        medicationPlanId = planId,
        type = type,
        eventDate = eventDate,
        recordedAt = UseCaseTestFixtures.now,
        scheduledMinuteOfDay = null,
        reason = null,
        doseAmount = null
    )
}
