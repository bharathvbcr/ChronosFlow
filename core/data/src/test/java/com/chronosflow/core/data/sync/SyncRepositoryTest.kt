package com.chronosflow.core.data.sync

import com.chronosflow.core.data.model.TimeBlockEntity
import com.chronosflow.core.domain.model.Task
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryTest {
    @Test
    fun `pushLocalChanges skips local reads when firebase is not configured`() = runTest {
        val source = FakeLocalSyncSource(sampleSnapshot())
        val gateway = FakeRemoteSyncGateway(configured = false)
        val repository = SyncRepository(source, gateway)

        val result = repository.pushLocalChanges()

        assertEquals(SyncResult.Disabled, result)
        assertEquals(0, source.snapshotCalls)
        assertNull(gateway.pushedBatch)
    }

    @Test
    fun `pushLocalChanges pushes local snapshot to remote gateway`() = runTest {
        val source = FakeLocalSyncSource(sampleSnapshot())
        val gateway = FakeRemoteSyncGateway(configured = true)
        val repository = SyncRepository(source, gateway)

        val result = repository.pushLocalChanges()

        assertEquals(SyncResult.Pushed(taskCount = 1, timeBlockCount = 1), result)
        assertEquals(1, source.snapshotCalls)
        assertEquals("task-1", gateway.pushedBatch?.tasks?.single()?.id)
        assertEquals("block-1", gateway.pushedBatch?.timeBlocks?.single()?.id)
    }

    @Test
    fun `pushLocalChanges reports retryable failure when remote push fails`() = runTest {
        val source = FakeLocalSyncSource(sampleSnapshot())
        val gateway = FakeRemoteSyncGateway(
            configured = true,
            pushError = IOException("network unavailable")
        )
        val repository = SyncRepository(source, gateway)

        val result = repository.pushLocalChanges()

        assertTrue(result is SyncResult.Failed)
        val failure = result as SyncResult.Failed
        assertTrue(failure.retryable)
        assertEquals("network unavailable", failure.message)
    }

    private class FakeLocalSyncSource(
        private val snapshot: LocalSyncSnapshot
    ) : LocalSyncSource {
        var snapshotCalls = 0

        override suspend fun snapshot(): LocalSyncSnapshot {
            snapshotCalls += 1
            return snapshot
        }
    }

    private class FakeRemoteSyncGateway(
        configured: Boolean,
        private val pushError: Throwable? = null
    ) : RemoteSyncGateway {
        override var isConfigured: Boolean = configured
        var pushedBatch: RemoteSyncBatch? = null

        override suspend fun push(batch: RemoteSyncBatch) {
            pushError?.let { throw it }
            pushedBatch = batch
        }
    }

    private fun sampleSnapshot(): LocalSyncSnapshot = LocalSyncSnapshot(
        tasks = listOf(
            Task(
                id = "task-1",
                title = "Draft sync plan",
                description = null,
                isCompleted = false,
                priority = 2,
                dueDate = null,
                createdAt = Instant.parse("2026-01-12T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-12T01:00:00Z")
            )
        ),
        timeBlocks = listOf(
            TimeBlockEntity(
                id = "block-1",
                date = LocalDate.parse("2026-01-12"),
                title = "Draft sync plan",
                category = "Deep Work",
                startMinuteOfDay = 540,
                durationMinutes = 45,
                timezone = "America/Chicago",
                source = "TASK",
                provenance = "TASK_CONVERTED",
                flexibility = "MOVABLE",
                energyLevel = 3,
                taskId = "task-1",
                calendarEventId = null,
                medicationPlanId = null,
                habitId = null,
                isLocked = false,
                isProtected = false,
                recurrenceRuleId = null,
                taskOccurrenceDate = LocalDate.parse("2026-01-12"),
                actualStartMinuteOfDay = null,
                actualEndMinuteOfDay = null,
                createdAt = Instant.parse("2026-01-12T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-12T01:00:00Z")
            )
        )
    )
}
