package com.ChronosFlow.VBCR.core.data.repository

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.data.dao.MedicationDao
import com.ChronosFlow.VBCR.core.data.dao.MedicationDoseEventDao
import com.ChronosFlow.VBCR.core.data.dao.MedicationSafetyProfileDao
import com.ChronosFlow.VBCR.core.data.dao.MedicationScheduleDao
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.data.model.MedicationDoseEventEntity
import com.ChronosFlow.VBCR.core.data.model.MedicationPlanEntity
import com.ChronosFlow.VBCR.core.data.model.MedicationSafetyProfileEntity
import com.ChronosFlow.VBCR.core.data.model.MedicationScheduleEntity
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.MedicationSafetyProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class MedicationRepositoryImplTest {
    private val medicationDao: MedicationDao = mockk()
    private val medicationScheduleDao: MedicationScheduleDao = mockk()
    private val medicationSafetyProfileDao: MedicationSafetyProfileDao = mockk()
    private val medicationDoseEventDao: MedicationDoseEventDao = mockk()
    private val repository = MedicationRepositoryImpl(
        medicationDao,
        medicationScheduleDao,
        medicationSafetyProfileDao,
        medicationDoseEventDao
    )

    @Test
    fun `observe medication plans maps default schedule and profile`() = runTest {
        every { medicationDao.observeMedicationPlans() } returns flowOf(listOf(planEntity()))
        every { medicationScheduleDao.observeAllSchedules() } returns flowOf(emptyList())
        every { medicationSafetyProfileDao.observeAllProfiles() } returns flowOf(emptyList())
        every { medicationDoseEventDao.observeAllEvents() } returns flowOf(emptyList())

        repository.observeMedicationPlans().test {
            val plans = awaitItem()
            assertEquals(1, plans.size)
            assertEquals("plan-1", plans[0].id)
            assertEquals("schedule-plan-1", plans[0].schedule?.id)
            assertEquals("tablet", plans[0].safetyProfile?.form)
            awaitComplete()
        }
    }

    @Test
    fun `get medication plan by id falls back to legacy schedule and profile`() = runTest {
        coEvery { medicationDao.getMedicationPlanById("plan-1") } returns planEntity()
        coEvery { medicationScheduleDao.getScheduleForPlan("plan-1") } returns null
        coEvery { medicationSafetyProfileDao.getProfileForPlan("plan-1") } returns null
        coEvery { medicationDoseEventDao.getEventsForPlan("plan-1") } returns emptyList()

        val plan = repository.getMedicationPlanById("plan-1")
        assertEquals("plan-1", plan?.id)
        assertEquals("schedule-plan-1", plan?.schedule?.id)
    }

    @Test
    fun `save medication plan writes plan, schedule and profile`() = runTest {
        coEvery { medicationDao.insertMedicationPlan(any()) } returns Unit
        coEvery { medicationScheduleDao.upsertSchedule(any()) } returns Unit
        coEvery { medicationSafetyProfileDao.upsertProfile(any()) } returns Unit

        repository.saveMedicationPlan(medicationPlanDomain())

        coVerify { medicationDao.insertMedicationPlan(any()) }
        coVerify { medicationScheduleDao.upsertSchedule(any()) }
        coVerify { medicationSafetyProfileDao.upsertProfile(any()) }
    }

    @Test
    fun `dose event insert and plan delete are forwarded`() = runTest {
        coEvery { medicationDoseEventDao.insertEvent(any()) } returns Unit
        coEvery { medicationDao.deleteMedicationPlan(any()) } returns Unit

        repository.addMedicationDoseEvent(
            MedicationDoseEvent(
                id = "event-1",
                medicationPlanId = "plan-1",
                type = MedicationDoseEventType.TAKEN,
                eventDate = LocalDate.parse("2026-01-03"),
                recordedAt = Instant.parse("2026-01-03T07:00:00Z"),
                scheduledMinuteOfDay = 420,
                reason = null,
                doseAmount = "1 tab"
            )
        )
        repository.deleteMedicationPlan(medicationPlanDomain())

        coVerify { medicationDoseEventDao.insertEvent(any()) }
        coVerify { medicationDao.deleteMedicationPlan(any()) }
    }

    @Test
    fun `observe dose events between maps entities to domain`() = runTest {
        val start = LocalDate.parse("2026-01-01")
        val end = LocalDate.parse("2026-01-07")
        val event = MedicationDoseEvent(
            id = "dose-1",
            medicationPlanId = "plan-1",
            type = MedicationDoseEventType.TAKEN,
            eventDate = LocalDate.parse("2026-01-03"),
            recordedAt = Instant.parse("2026-01-03T07:00:00Z"),
            scheduledMinuteOfDay = 420,
            reason = null,
            doseAmount = "1 tab"
        )
        every { medicationDoseEventDao.observeEventsBetween(start, end) } returns flowOf(listOf(event.toEntity()))

        repository.observeDoseEventsBetween(start, end).test {
            val events = awaitItem()
            assertEquals(1, events.size)
            assertEquals("dose-1", events[0].id)
            assertEquals(MedicationDoseEventType.TAKEN, events[0].type)
            awaitComplete()
        }
    }

    private fun planEntity(): MedicationPlanEntity = MedicationPlanEntity(
        id = "plan-1",
        name = "Hydration",
        dosage = "5ml",
        unit = "tablet",
        notes = "Before bed reminder",
        startAt = null,
        endAt = null,
        reminderMinuteOfDay = 420,
        takeWithFood = false,
        missedCount = 0,
        refillNeededAfterDoses = 4,
        isActive = true
    )

    private fun medicationPlanDomain(): MedicationPlan = MedicationPlan(
        id = "plan-1",
        name = "Hydration",
        dosage = "5ml",
        unit = "tablet",
        notes = "Before bed reminder",
        startAt = null,
        endAt = null,
        reminderMinuteOfDay = 420,
        takeWithFood = false,
        missedCount = 0,
        refillNeededAfterDoses = 4,
        isActive = true,
        schedule = null,
        safetyProfile = null
    )
}
