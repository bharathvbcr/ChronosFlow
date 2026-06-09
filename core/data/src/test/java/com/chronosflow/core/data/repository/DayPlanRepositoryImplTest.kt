package com.chronosflow.core.data.repository

import app.cash.turbine.test
import com.chronosflow.core.data.dao.DayPlanDao
import com.chronosflow.core.data.model.DayPlanEntity
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.DayPlan
import com.chronosflow.core.domain.model.DayPlanStatus
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class DayPlanRepositoryImplTest {
    private val dayPlanDao: DayPlanDao = mockk()
    private val clock = Clock.fixed(Instant.parse("2026-05-27T11:00:00Z"), ZoneId.of("UTC"))
    private lateinit var repository: DayPlanRepositoryImpl

    @Before
    fun setup() {
        repository = DayPlanRepositoryImpl(dayPlanDao, clock)
    }

    @Test
    fun `getDayPlanByDate maps persisted per-date planner state`() = runTest {
        val date = LocalDate.parse("2026-05-27")
        every { dayPlanDao.getDayPlanByDate(date) } returns flowOf(
            DayPlanEntity(
                date = date,
                timezone = "America/Chicago",
                status = DayPlanStatus.PLANNED.name,
                lastAiSuggestionAt = Instant.parse("2026-05-27T10:00:00Z"),
                totalPlannedMinutes = 120,
                conflictCount = 1,
                updatedAt = Instant.parse("2026-05-27T10:30:00Z")
            )
        )

        repository.getDayPlanByDate(date).test {
            val plan = awaitItem()

            assertNotNull(plan)
            assertEquals(date, plan?.date)
            assertEquals(ZoneId.of("America/Chicago"), plan?.timezone)
            assertEquals(DayPlanStatus.PLANNED, plan?.status)
            assertEquals(emptyList<TimeBlock>(), plan?.blocks)
            assertEquals(0, plan?.conflicts?.size)
            awaitComplete()
        }
    }

    @Test
    fun `saveDayPlan persists dedicated per-date summary row`() = runTest {
        val date = LocalDate.parse("2026-05-27")
        coEvery { dayPlanDao.insertDayPlan(any()) } returns Unit

        repository.saveDayPlan(
            DayPlan(
                date = date,
                timezone = ZoneId.of("America/Chicago"),
                status = DayPlanStatus.PLANNED,
                blocks = listOf(sampleTimeBlock(date, durationMinutes = 45)),
                conflicts = emptyList(),
                review = null
            )
        )

        coVerify {
            dayPlanDao.insertDayPlan(
                match {
                    it.date == date &&
                        it.timezone == "America/Chicago" &&
                        it.status == DayPlanStatus.PLANNED.name &&
                        it.totalPlannedMinutes == 45 &&
                        it.conflictCount == 0 &&
                        it.updatedAt == Instant.parse("2026-05-27T11:00:00Z")
                }
            )
        }
    }

    private fun sampleTimeBlock(date: LocalDate, durationMinutes: Int): TimeBlock {
        val now = Instant.parse("2026-05-27T10:00:00Z")
        return TimeBlock(
            id = "block-1",
            date = date,
            title = "Focus",
            category = "Deep Work",
            startMinuteOfDay = 9 * 60,
            durationMinutes = durationMinutes,
            timezone = "America/Chicago",
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "USER",
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
    }
}
