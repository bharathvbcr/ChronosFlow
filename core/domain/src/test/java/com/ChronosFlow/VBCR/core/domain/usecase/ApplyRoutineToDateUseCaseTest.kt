package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.Routine
import com.ChronosFlow.VBCR.core.domain.model.RoutineStep
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.repository.RoutineRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ApplyRoutineToDateUseCaseTest {
    private val routineRepository: RoutineRepository = mockk()
    private val timeBlockRepository: TimeBlockRepository = mockk()
    private val useCase = ApplyRoutineToDateUseCase(routineRepository, timeBlockRepository)

    @Test
    fun `creates one block per step at start plus offset carrying routineId`() = runTest {
        coEvery { routineRepository.getRoutineById("r1") } returns Routine(
            id = "r1",
            title = "Morning",
            isActive = true,
            lastCompletedDate = null,
            steps = listOf(
                RoutineStep(id = "s1", title = "Stretch", offsetMinute = 0, durationMinutes = 10),
                RoutineStep(id = "s2", title = "Journal", offsetMinute = 15, durationMinutes = 20)
            )
        )
        val saved = mutableListOf<TimeBlock>()
        coEvery { timeBlockRepository.saveTimeBlock(any()) } answers {
            saved += firstArg<TimeBlock>()
        }

        val created = useCase(routineId = "r1", date = LocalDate.of(2026, 6, 11), startMinuteOfDay = 360)

        assertEquals(2, created)
        assertEquals(listOf(360, 375), saved.map { it.startMinuteOfDay })
        assertEquals(listOf("r1", "r1"), saved.map { it.routineId })
        assertEquals(listOf("ROUTINE", "ROUTINE"), saved.map { it.category })
    }

    @Test
    fun `steps crossing midnight roll onto the next date`() = runTest {
        coEvery { routineRepository.getRoutineById("r1") } returns Routine(
            id = "r1",
            title = "Wind down",
            isActive = true,
            lastCompletedDate = null,
            steps = listOf(
                RoutineStep(id = "s1", title = "Tea", offsetMinute = 0, durationMinutes = 15),
                RoutineStep(id = "s2", title = "Lights out", offsetMinute = 60, durationMinutes = 30)
            )
        )
        val saved = mutableListOf<TimeBlock>()
        coEvery { timeBlockRepository.saveTimeBlock(any()) } answers {
            saved += firstArg<TimeBlock>()
        }

        // 23:30 anchor: first step stays same day, second (00:30) rolls to the next date.
        val date = LocalDate.of(2026, 6, 11)
        useCase(routineId = "r1", date = date, startMinuteOfDay = 1410)

        assertEquals(listOf(1410, 30), saved.map { it.startMinuteOfDay })
        assertEquals(listOf(date, date.plusDays(1)), saved.map { it.date })
    }

    @Test
    fun `returns zero when routine is missing`() = runTest {
        coEvery { routineRepository.getRoutineById("missing") } returns null

        assertEquals(0, useCase(routineId = "missing", date = LocalDate.of(2026, 6, 11), startMinuteOfDay = 0))
    }
}
