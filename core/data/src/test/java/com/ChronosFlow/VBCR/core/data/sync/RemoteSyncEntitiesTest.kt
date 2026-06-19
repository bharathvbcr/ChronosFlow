package com.ChronosFlow.VBCR.core.data.sync

import com.ChronosFlow.VBCR.core.data.model.TimeBlockEntity
import com.ChronosFlow.VBCR.core.domain.model.ContactMethodKind
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import com.ChronosFlow.VBCR.core.domain.model.TaskChecklistItem
import com.ChronosFlow.VBCR.core.domain.model.TaskContactMethod
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteSyncEntitiesTest {
    @Test
    fun `task payload preserves syncable task fields`() {
        val remote = sampleTask().toRemoteEntity()

        assertEquals("task-1", remote.id)
        assertEquals(1_768_262_400_000L, remote.dueDateEpochMillis)
        assertEquals("2026-01-12", remote.targetDate)
        assertEquals(1, remote.checklist.size)
        assertEquals("EMAIL", remote.linkedContact?.methods?.single()?.kind)
        assertEquals("WEBSITE", remote.actions.single().type)
        assertEquals("FILE", remote.attachments.single().kind)

        val map = remote.toFirestoreMap()
        assertEquals("task-1", map["id"])
        assertEquals(1_768_262_400_000L, map["dueDateEpochMillis"])
        assertEquals("2026-01-12", map["targetDate"])
        @Suppress("UNCHECKED_CAST")
        val checklist = map["checklist"] as List<Map<String, Any?>>
        assertEquals("Draft brief", checklist.single()["label"])
    }

    @Test
    fun `time block payload preserves syncable schedule fields`() {
        val remote = sampleTimeBlock().toRemoteEntity()

        assertEquals("block-1", remote.id)
        assertEquals("2026-01-12", remote.date)
        assertEquals("TASK_CONVERTED", remote.provenance)
        assertEquals("MOVABLE", remote.flexibility)
        assertEquals(3, remote.energyLevel)
        assertEquals("2026-01-12", remote.taskOccurrenceDate)
        assertEquals(1_768_176_000_000L, remote.createdAtEpochMillis)

        val map = remote.toFirestoreMap()
        assertEquals("block-1", map["id"])
        assertEquals("task-1", map["taskId"])
        assertEquals(540, map["startMinuteOfDay"])
    }

    private fun sampleTask(): Task = Task(
        id = "task-1",
        title = "Draft sync plan",
        description = "Ship Firestore foundation",
        isCompleted = false,
        priority = 3,
        dueDate = Instant.parse("2026-01-13T00:00:00Z"),
        createdAt = Instant.parse("2026-01-12T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-12T01:00:00Z"),
        preferredDurationMinutes = 45,
        preferredStartMinuteOfDay = 540,
        targetDate = LocalDate.parse("2026-01-12"),
        checklist = listOf(
            TaskChecklistItem(
                id = "check-1",
                label = "Draft brief",
                isCompleted = true
            )
        ),
        linkedContact = TaskContactSnapshot(
            displayName = "Alex Lee",
            lookupKey = "lookup-1",
            methods = listOf(
                TaskContactMethod(
                    id = "contact-method-1",
                    kind = ContactMethodKind.EMAIL,
                    label = "Work",
                    value = "alex@example.com",
                    normalizedValue = "alex@example.com",
                    isPrimary = true
                )
            )
        ),
        actions = listOf(
            TaskAction(
                id = "action-1",
                type = TaskActionType.WEBSITE,
                label = "Open plan",
                value = "https://example.com",
                isPrimary = true
            )
        ),
        attachments = listOf(
            TaskAttachment(
                id = "attachment-1",
                displayName = "Brief.pdf",
                mimeType = "application/pdf",
                sizeBytes = 1024,
                kind = TaskAttachmentKind.FILE,
                storageMode = TaskAttachmentStorageMode.LINKED,
                reference = "content://brief",
                persistedUriPermission = true
            )
        )
    )

    private fun sampleTimeBlock(): TimeBlockEntity = TimeBlockEntity(
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
        isProtected = true,
        recurrenceRuleId = null,
        taskOccurrenceDate = LocalDate.parse("2026-01-12"),
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.parse("2026-01-12T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-12T01:00:00Z")
    )
}
