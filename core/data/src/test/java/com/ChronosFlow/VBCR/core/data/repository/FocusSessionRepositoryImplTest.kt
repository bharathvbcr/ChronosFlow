package com.ChronosFlow.VBCR.core.data.repository

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.data.dao.FocusSessionDao
import com.ChronosFlow.VBCR.core.data.model.FocusSessionEntity
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FocusSessionRepositoryImplTest {
    private val dao: FocusSessionDao = mockk()
    private val repository = FocusSessionRepositoryImpl(dao)

    @Test
    fun `recoverable session returns null when none is available`() = runTest {
        every { dao.observeRecoverableSession() } returns flowOf(null)

        repository.observeRecoverableSession().test {
            val value = awaitItem()
            assertEquals(null, value)
            awaitComplete()
        }
    }

    @Test
    fun `get session by id maps running entity to running state`() = runTest {
        val startedAt = Instant.parse("2026-01-02T10:00:00Z")
        val plannedEndAt = Instant.parse("2026-01-02T11:00:00Z")
        coEvery { dao.getFocusSession("session-1") } returns FocusSessionEntity(
            id = "session-1",
            blockId = "block-1",
            state = "RUNNING",
            startedAt = startedAt,
            plannedEndAt = plannedEndAt,
            pausedAt = null,
            completedAt = null,
            totalSeconds = null,
            updatedAt = Instant.parse("2026-01-02T10:00:01Z")
        )

        val result = repository.getFocusSession("session-1")
        val running = result as? FocusSessionState.Running

        assertTrue(running != null)
        assertEquals("session-1", running!!.sessionId)
        assertEquals("block-1", running.blockId)
    }

    @Test
    fun `save writes focus session snapshot using entity converter`() = runTest {
        val state = FocusSessionState.Reviewing("session-2")
        coEvery { dao.insertFocusSession(any()) } returns Unit

        repository.saveFocusSession(state)

        coVerify {
            dao.insertFocusSession(
                match {
                    it.id == "session-2" &&
                        it.state == "REVIEWING" &&
                        it.blockId == null
                }
            )
        }
    }
}
