package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.ContactMethodKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import com.ChronosFlow.VBCR.core.domain.model.TaskChecklistItem
import com.ChronosFlow.VBCR.core.domain.model.TaskContactMethod
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AddTaskUseCaseTest {

    private lateinit var repository: TaskRepository
    private lateinit var useCase: AddTaskUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = AddTaskUseCase(repository)
    }

    @Test
    fun `invoke calls repository saveTask`() = runTest {
        // Arrange
        val title = "Test Task"
        val description = "Description"
        coEvery { repository.saveTask(any()) } returns Unit

        // Act
        useCase(title, description)

        // Assert
        coVerify { repository.saveTask(match { it.title == title && it.description == description }) }
    }

    @Test
    fun `invoke saves checklist and scheduling preferences`() = runTest {
        val targetDate = LocalDate.of(2026, 5, 26)
        val checklist = listOf(
            TaskChecklistItem(id = "c1", label = "Outline", isCompleted = false),
            TaskChecklistItem(id = "c2", label = "Review", isCompleted = false)
        )
        coEvery { repository.saveTask(any()) } returns Unit

        useCase(
            title = "Launch prep",
            description = "Get the release ready",
            priority = 2,
            dueDate = null,
            preferredDurationMinutes = 60,
            preferredStartMinuteOfDay = 14 * 60,
            targetDate = targetDate,
            checklist = checklist
        )

        coVerify {
            repository.saveTask(
                match {
                    it.title == "Launch prep" &&
                        it.priority == 2 &&
                        it.preferredDurationMinutes == 60 &&
                        it.preferredStartMinuteOfDay == 14 * 60 &&
                        it.targetDate == targetDate &&
                        it.checklist == checklist
                }
            )
        }
    }

    @Test
    fun `invoke saves linked contact and task actions`() = runTest {
        val linkedContact = TaskContactSnapshot(
            displayName = "Alex Johnson",
            lookupKey = "lookup-1",
            methods = listOf(
                TaskContactMethod(
                    id = "m1",
                    kind = ContactMethodKind.PHONE,
                    label = "mobile",
                    value = "+15551234567",
                    normalizedValue = "+15551234567",
                    isPrimary = true
                )
            )
        )
        val actions = listOf(
            TaskAction(
                id = "a1",
                type = TaskActionType.WEBSITE,
                label = "Client site",
                value = "https://example.com",
                isPrimary = true
            )
        )
        coEvery { repository.saveTask(any()) } returns Unit

        useCase(
            title = "Follow up",
            linkedContact = linkedContact,
            actions = actions
        )

        coVerify {
            repository.saveTask(
                match {
                    it.title == "Follow up" &&
                        it.linkedContact == linkedContact &&
                        it.actions == actions
                }
            )
        }
    }

    @Test
    fun `invoke saves task attachments`() = runTest {
        val attachments = listOf(
            TaskAttachment(
                id = "attachment-1",
                displayName = "brief.pdf",
                mimeType = "application/pdf",
                sizeBytes = 1_024,
                kind = TaskAttachmentKind.FILE,
                storageMode = TaskAttachmentStorageMode.LINKED,
                reference = "content://docs/brief.pdf",
                persistedUriPermission = true
            ),
            TaskAttachment(
                id = "attachment-2",
                displayName = "hero.png",
                mimeType = "image/png",
                sizeBytes = 2_048,
                kind = TaskAttachmentKind.IMAGE,
                storageMode = TaskAttachmentStorageMode.IMPORTED,
                reference = "task_attachments/hero.png",
                isFeaturedImage = true
            )
        )
        coEvery { repository.saveTask(any()) } returns Unit

        useCase(
            title = "Attach assets",
            attachments = attachments
        )

        coVerify {
            repository.saveTask(
                match {
                    it.title == "Attach assets" &&
                        it.attachments == attachments
                }
            )
        }
    }
}
