package com.chronosflow.core.data.backup

import com.chronosflow.core.data.sync.RemoteSyncBatch
import com.chronosflow.core.data.sync.RemoteTaskActionEntity
import com.chronosflow.core.data.sync.RemoteTaskAttachmentEntity
import com.chronosflow.core.data.sync.RemoteTaskChecklistItemEntity
import com.chronosflow.core.data.sync.RemoteTaskContactEntity
import com.chronosflow.core.data.sync.RemoteTaskContactMethodEntity
import com.chronosflow.core.data.sync.RemoteTaskEntity
import com.chronosflow.core.data.sync.RemoteTimeBlockEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosPortableBackupCodecTest {
    private val codec = ChronosPortableBackupCodec()

    @Test
    fun `encodes a versioned portable transfer snapshot and round trips app data`() {
        val batch = sampleBatch()

        val encoded = codec.encode(batch)
        val decoded = codec.decode(encoded)

        assertTrue(encoded.contains("\"formatVersion\":1"))
        assertEquals(batch, decoded)
    }

    private fun sampleBatch(): RemoteSyncBatch = RemoteSyncBatch(
        tasks = listOf(
            RemoteTaskEntity(
                id = "task-1",
                title = "Plan transfer",
                description = "Keep data portable",
                isCompleted = false,
                priority = 4,
                dueDateEpochMillis = 1_768_262_400_000L,
                createdAtEpochMillis = 1_768_176_000_000L,
                updatedAtEpochMillis = 1_768_179_600_000L,
                preferredDurationMinutes = 30,
                preferredStartMinuteOfDay = 540,
                targetDate = "2026-01-12",
                checklist = listOf(
                    RemoteTaskChecklistItemEntity(
                        id = "check-1",
                        label = "Verify JSON | escaping",
                        isCompleted = true
                    )
                ),
                linkedContact = RemoteTaskContactEntity(
                    displayName = "Alex Lee",
                    lookupKey = "lookup-1",
                    methods = listOf(
                        RemoteTaskContactMethodEntity(
                            id = "method-1",
                            kind = "EMAIL",
                            label = "Work",
                            value = "alex@example.com",
                            normalizedValue = "alex@example.com",
                            isPrimary = true
                        )
                    )
                ),
                actions = listOf(
                    RemoteTaskActionEntity(
                        id = "action-1",
                        type = "WEBSITE",
                        label = "Open",
                        value = "https://example.com",
                        isPrimary = true
                    )
                ),
                attachments = listOf(
                    RemoteTaskAttachmentEntity(
                        id = "attachment-1",
                        displayName = "Brief.pdf",
                        mimeType = "application/pdf",
                        sizeBytes = 2048,
                        kind = "FILE",
                        storageMode = "LINKED",
                        reference = "content://brief",
                        persistedUriPermission = true,
                        isFeaturedImage = false
                    )
                )
            )
        ),
        timeBlocks = listOf(
            RemoteTimeBlockEntity(
                id = "block-1",
                date = "2026-01-12",
                title = "Plan transfer",
                category = "Deep Work",
                startMinuteOfDay = 540,
                durationMinutes = 30,
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
                isProtected = true,
                recurrenceRuleId = null,
                taskOccurrenceDate = "2026-01-12",
                actualStartMinuteOfDay = null,
                actualEndMinuteOfDay = null,
                createdAtEpochMillis = 1_768_176_000_000L,
                updatedAtEpochMillis = 1_768_179_600_000L
            )
        )
    )
}
