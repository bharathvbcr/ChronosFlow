package com.ChronosFlow.VBCR.core.notifications

import com.ChronosFlow.VBCR.core.domain.model.ContactMethodKind
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import com.ChronosFlow.VBCR.core.domain.model.TaskContactMethod
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskContextCommandResolverTest {
    @Test
    fun `contact phone outranks explicit primary action`() {
        val task = sampleTask(
            linkedContact = TaskContactSnapshot(
                displayName = "Alex",
                methods = listOf(
                    TaskContactMethod("m1", ContactMethodKind.PHONE, "mobile", "+15551234567", "+15551234567", true)
                )
            ),
            actions = listOf(TaskAction("a1", TaskActionType.WEBSITE, "Docs", "https://example.com", true))
        )

        val commands = TaskContextCommandResolver.resolve(task).commands

        assertEquals(TaskContextCommandKind.CALL, commands.first().kind)
        assertEquals("Call", commands.first().shortLabel)
    }

    @Test
    fun `featured image outranks first file attachment`() {
        val task = sampleTask(
            attachments = listOf(
                TaskAttachment("f1", "brief.pdf", "application/pdf", null, TaskAttachmentKind.FILE, TaskAttachmentStorageMode.LINKED, "content://brief"),
                TaskAttachment("i1", "photo.png", "image/png", null, TaskAttachmentKind.IMAGE, TaskAttachmentStorageMode.LINKED, "content://photo", isFeaturedImage = true)
            )
        )

        val commands = TaskContextCommandResolver.resolve(task).externalCommands

        assertEquals(TaskContextCommandKind.IMAGE, commands.first().kind)
        assertEquals("Open photo", commands.first().shortLabel)
    }

    @Test
    fun `no external command falls back to edit`() {
        val commands = TaskContextCommandResolver.resolve(sampleTask().copy(isCompleted = true)).commands

        assertEquals(TaskContextCommandKind.EDIT, commands.first().kind)
    }

    @Test
    fun `app action becomes app command`() {
        val commands = TaskContextCommandResolver.resolve(
            sampleTask(
                actions = listOf(
                    TaskAction("a1", TaskActionType.APP, "Open Journal", "com.example.journal", true)
                )
            )
        ).externalCommands

        assertEquals(TaskContextCommandKind.APP, commands.first().kind)
        assertEquals("Open app", commands.first().shortLabel)
    }

    private fun sampleTask(
        linkedContact: TaskContactSnapshot? = null,
        actions: List<TaskAction> = emptyList(),
        attachments: List<TaskAttachment> = emptyList()
    ): Task = Task(
        id = "task-1",
        title = "Follow up",
        description = null,
        isCompleted = false,
        priority = 0,
        dueDate = null,
        createdAt = Instant.parse("2026-05-25T12:00:00Z"),
        updatedAt = Instant.parse("2026-05-25T12:00:00Z"),
        linkedContact = linkedContact,
        actions = actions,
        attachments = attachments
    )
}
