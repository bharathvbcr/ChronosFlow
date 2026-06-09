package com.chronosflow.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TaskTest {

    @Test
    fun `task creation sets properties correctly`() {
        val now = Instant.now()
        val task = Task(
            id = "1",
            title = "Test Task",
            description = "Test Description",
            isCompleted = false,
            priority = 1,
            dueDate = now,
            createdAt = now,
            updatedAt = now
        )

        assertEquals("1", task.id)
        assertEquals("Test Task", task.title)
        assertEquals("Test Description", task.description)
        assertEquals(false, task.isCompleted)
        assertEquals(1, task.priority)
        assertEquals(now, task.dueDate)
        assertEquals(now, task.createdAt)
        assertEquals(now, task.updatedAt)
    }

    @Test
    fun `task copy allows modifying properties`() {
        val now = Instant.now()
        val task = Task(
            id = "1",
            title = "Test Task",
            description = null,
            isCompleted = false,
            priority = 1,
            dueDate = null,
            createdAt = now,
            updatedAt = now
        )

        val updatedTask = task.copy(isCompleted = true)

        assertTrue(updatedTask.isCompleted)
        assertEquals(task.id, updatedTask.id)
        assertEquals(task.title, updatedTask.title)
    }

    @Test
    fun `task can hold scheduling preferences and checklist`() {
        val now = Instant.now()
        val targetDate = LocalDate.of(2026, 5, 25)
        val task = Task(
            id = "1",
            title = "Plan launch",
            description = "Prep release notes",
            isCompleted = false,
            priority = 2,
            dueDate = null,
            createdAt = now,
            updatedAt = now,
            preferredDurationMinutes = 90,
            preferredStartMinuteOfDay = 9 * 60 + 30,
            targetDate = targetDate,
            checklist = listOf(
                TaskChecklistItem(
                    id = "item-1",
                    label = "Draft notes",
                    isCompleted = false
                ),
                TaskChecklistItem(
                    id = "item-2",
                    label = "Share with team",
                    isCompleted = true
                )
            )
        )

        assertEquals(90, task.preferredDurationMinutes)
        assertEquals(9 * 60 + 30, task.preferredStartMinuteOfDay)
        assertEquals(targetDate, task.targetDate)
        assertEquals(2, task.checklist.size)
        assertEquals("Draft notes", task.checklist.first().label)
        assertTrue(task.checklist.last().isCompleted)
    }

    @Test
    fun `task defaults to no preferences and empty checklist`() {
        val now = Instant.now()
        val task = Task(
            id = "1",
            title = "Inbox zero",
            description = null,
            isCompleted = false,
            priority = 0,
            dueDate = null,
            createdAt = now,
            updatedAt = now
        )

        assertNull(task.preferredDurationMinutes)
        assertNull(task.preferredStartMinuteOfDay)
        assertNull(task.targetDate)
        assertTrue(task.checklist.isEmpty())
    }

    @Test
    fun `task can hold linked contact and mixed actions`() {
        val now = Instant.now()
        val task = Task(
            id = "1",
            title = "Client follow-up",
            description = null,
            isCompleted = false,
            priority = 1,
            dueDate = null,
            createdAt = now,
            updatedAt = now,
            linkedContact = TaskContactSnapshot(
                displayName = "Alex Johnson",
                lookupKey = "lookup-1",
                methods = listOf(
                    TaskContactMethod(
                        id = "method-1",
                        kind = ContactMethodKind.EMAIL,
                        label = "work",
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
                    label = "Brief",
                    value = "https://example.com/brief",
                    isPrimary = true
                ),
                TaskAction(
                    id = "action-2",
                    type = TaskActionType.PHONE,
                    label = "Call Alex",
                    value = "+15551234567",
                    isPrimary = false
                )
            )
        )

        assertEquals("Alex Johnson", task.linkedContact?.displayName)
        assertEquals(ContactMethodKind.EMAIL, task.linkedContact?.methods?.single()?.kind)
        assertEquals(2, task.actions.size)
        assertEquals(TaskActionType.WEBSITE, task.actions.first().type)
    }

    @Test
    fun `task can hold linked and imported attachments`() {
        val now = Instant.now()
        val task = Task(
            id = "1",
            title = "Review files",
            description = null,
            isCompleted = false,
            priority = 1,
            dueDate = null,
            createdAt = now,
            updatedAt = now,
            attachments = listOf(
                TaskAttachment(
                    id = "attachment-1",
                    displayName = "brief.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 42_000,
                    kind = TaskAttachmentKind.FILE,
                    storageMode = TaskAttachmentStorageMode.LINKED,
                    reference = "content://docs/brief.pdf",
                    persistedUriPermission = true,
                    isFeaturedImage = false
                ),
                TaskAttachment(
                    id = "attachment-2",
                    displayName = "mockup.png",
                    mimeType = "image/png",
                    sizeBytes = 9_500,
                    kind = TaskAttachmentKind.IMAGE,
                    storageMode = TaskAttachmentStorageMode.IMPORTED,
                    reference = "task_attachments/mockup.png",
                    persistedUriPermission = false,
                    isFeaturedImage = true
                )
            )
        )

        assertEquals(2, task.attachments.size)
        assertEquals(TaskAttachmentStorageMode.LINKED, task.attachments.first().storageMode)
        assertEquals(TaskAttachmentKind.IMAGE, task.attachments.last().kind)
        assertTrue(task.attachments.last().isFeaturedImage)
    }
}
