package com.ChronosFlow.VBCR.core.data.repository

import androidx.room.withTransaction
import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.data.dao.TaskDao
import com.ChronosFlow.VBCR.core.data.model.TaskActionEntity
import com.ChronosFlow.VBCR.core.data.model.TaskAttachmentEntity
import com.ChronosFlow.VBCR.core.data.model.TaskContactMethodEntity
import com.ChronosFlow.VBCR.core.data.model.TaskContactSnapshotEntity
import com.ChronosFlow.VBCR.core.data.model.TaskContactWithMethods
import com.ChronosFlow.VBCR.core.data.model.TaskEntity
import com.ChronosFlow.VBCR.core.data.model.TaskChecklistItemEntity
import com.ChronosFlow.VBCR.core.data.model.TaskWithChecklistItems
import com.ChronosFlow.VBCR.core.data.sync.SyncMutationNotifier
import com.ChronosFlow.VBCR.core.domain.model.ContactMethodKind
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import com.ChronosFlow.VBCR.core.domain.model.TaskChecklistItem
import com.ChronosFlow.VBCR.core.domain.model.TaskContactMethod
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TaskRepositoryImplTest {

    private val database: ChronosDatabase = mockk()
    private val taskDao: TaskDao = mockk()
    private val syncMutationNotifier = RecordingSyncMutationNotifier()
    private lateinit var repository: TaskRepositoryImpl

    @Before
    fun setup() {
        syncMutationNotifier.reset()
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambdaSlot = slot<suspend () -> Unit>()
        coEvery { database.withTransaction(capture(transactionLambdaSlot)) } coAnswers {
            transactionLambdaSlot.captured.invoke()
        }
        repository = TaskRepositoryImpl(database, taskDao, syncMutationNotifier)
    }

    @Test
    fun `getAllTasks maps entities to domain models`() = runTest {
        val now = Instant.now()
        val entities = listOf(
            TaskWithChecklistItems(
                task = TaskEntity(
                    id = "1",
                    title = "Task 1",
                    description = null,
                    isCompleted = false,
                    priority = 0,
                    dueDate = null,
                    createdAt = now,
                    updatedAt = now,
                    preferredDurationMinutes = 45,
                    preferredStartMinuteOfDay = 10 * 60,
                    targetDate = LocalDate.of(2026, 5, 27)
                ),
                checklistItems = listOf(
                    TaskChecklistItemEntity(
                        id = "item-1",
                        taskId = "1",
                        label = "Outline",
                        isCompleted = false,
                        sortOrder = 0
                    )
                ),
                contactSnapshot = TaskContactWithMethods(
                    snapshot = TaskContactSnapshotEntity(
                        taskId = "1",
                        displayName = "Alex Johnson",
                        lookupKey = "lookup-1"
                    ),
                    methods = listOf(
                        TaskContactMethodEntity(
                            id = "method-1",
                            taskId = "1",
                            kind = "PHONE",
                            label = "mobile",
                            value = "+15551234567",
                            normalizedValue = "+15551234567",
                            isPrimary = true,
                            sortOrder = 0
                        )
                    )
                ),
                taskActions = listOf(
                    TaskActionEntity(
                        id = "action-1",
                        taskId = "1",
                        type = "WEBSITE",
                        label = "Brief",
                        value = "https://example.com/brief",
                        isPrimary = true,
                        sortOrder = 0
                    )
                ),
                taskAttachments = listOf(
                    TaskAttachmentEntity(
                        id = "attachment-1",
                        taskId = "1",
                        displayName = "brief.pdf",
                        mimeType = "application/pdf",
                        sizeBytes = 4_096,
                        kind = "FILE",
                        storageMode = "LINKED",
                        reference = "content://docs/brief.pdf",
                        persistedUriPermission = true,
                        isFeaturedImage = false,
                        sortOrder = 0
                    )
                )
            )
        )
        every { taskDao.observeAllTasksWithChecklist() } returns flowOf(entities)

        repository.getAllTasks().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Task 1", result[0].title)
            assertEquals(45, result[0].preferredDurationMinutes)
            assertEquals(10 * 60, result[0].preferredStartMinuteOfDay)
            assertEquals(LocalDate.of(2026, 5, 27), result[0].targetDate)
            assertEquals("Outline", result[0].checklist.single().label)
            assertEquals("Alex Johnson", result[0].linkedContact?.displayName)
            assertEquals("Brief", result[0].actions.single().label)
            assertEquals("brief.pdf", result[0].attachments.single().displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveTask calls DAO insertTask`() = runTest {
        val now = Instant.now()
        val task = Task(
            id = "1",
            title = "Task 1",
            description = null,
            isCompleted = false,
            priority = 0,
            dueDate = null,
            createdAt = now,
            updatedAt = now,
            preferredDurationMinutes = 30,
            preferredStartMinuteOfDay = 9 * 60,
            targetDate = LocalDate.of(2026, 5, 28),
            checklist = listOf(
                TaskChecklistItem(id = "item-1", label = "Check email", isCompleted = false)
            )
        )
        coEvery { taskDao.upsertTask(any()) } returns Unit
        coEvery { taskDao.replaceChecklistItems(any(), any()) } returns Unit
        coEvery { taskDao.replaceTaskContact(any(), any(), any()) } returns Unit
        coEvery { taskDao.replaceTaskActions(any(), any()) } returns Unit
        coEvery { taskDao.replaceTaskAttachments(any(), any()) } returns Unit

        repository.saveTask(task)

        coVerify {
            taskDao.upsertTask(
                match {
                    it.id == "1" &&
                        it.title == "Task 1" &&
                        it.preferredDurationMinutes == 30 &&
                        it.preferredStartMinuteOfDay == 9 * 60 &&
                        it.targetDate == LocalDate.of(2026, 5, 28)
                }
            )
        }
        coVerify {
            taskDao.replaceChecklistItems(
                "1",
                match { items ->
                    items.size == 1 &&
                        items.single().taskId == "1" &&
                        items.single().label == "Check email"
                }
            )
        }
        coVerify { taskDao.replaceTaskContact("1", null, emptyList()) }
        coVerify { taskDao.replaceTaskActions("1", emptyList()) }
        coVerify { taskDao.replaceTaskAttachments("1", emptyList()) }
        assertEquals(1, syncMutationNotifier.callCount)
    }

    @Test
    fun `saveTask persists contact snapshot and actions`() = runTest {
        val now = Instant.now()
        val task = Task(
            id = "1",
            title = "Task 1",
            description = null,
            isCompleted = false,
            priority = 0,
            dueDate = null,
            createdAt = now,
            updatedAt = now,
            linkedContact = TaskContactSnapshot(
                displayName = "Alex Johnson",
                lookupKey = "lookup-1",
                methods = listOf(
                    TaskContactMethod(
                        id = "method-1",
                        kind = ContactMethodKind.PHONE,
                        label = "mobile",
                        value = "+15551234567",
                        normalizedValue = "+15551234567",
                        isPrimary = true
                    )
                )
            ),
            actions = listOf(
                TaskAction(
                    id = "action-1",
                    type = TaskActionType.WEBSITE,
                    label = "Docs",
                    value = "https://example.com",
                    isPrimary = true
                )
            ),
            attachments = listOf(
                TaskAttachment(
                    id = "attachment-1",
                    displayName = "mockup.png",
                    mimeType = "image/png",
                    sizeBytes = 9_216,
                    kind = TaskAttachmentKind.IMAGE,
                    storageMode = TaskAttachmentStorageMode.IMPORTED,
                    reference = "task_attachments/mockup.png",
                    persistedUriPermission = false,
                    isFeaturedImage = true
                )
            )
        )
        coEvery { taskDao.upsertTask(any()) } returns Unit
        coEvery { taskDao.replaceChecklistItems(any(), any()) } returns Unit
        coEvery { taskDao.replaceTaskContact(any(), any(), any()) } returns Unit
        coEvery { taskDao.replaceTaskActions(any(), any()) } returns Unit
        coEvery { taskDao.replaceTaskAttachments(any(), any()) } returns Unit

        repository.saveTask(task)

        coVerify {
            taskDao.replaceTaskContact(
                "1",
                TaskContactSnapshotEntity(
                    taskId = "1",
                    displayName = "Alex Johnson",
                    lookupKey = "lookup-1"
                ),
                listOf(
                    TaskContactMethodEntity(
                        id = "method-1",
                        taskId = "1",
                        kind = "PHONE",
                        label = "mobile",
                        value = "+15551234567",
                        normalizedValue = "+15551234567",
                        isPrimary = true,
                        sortOrder = 0
                    )
                )
            )
        }
        coVerify {
            taskDao.replaceTaskActions(
                "1",
                listOf(
                    TaskActionEntity(
                        id = "action-1",
                        taskId = "1",
                        type = "WEBSITE",
                        label = "Docs",
                        value = "https://example.com",
                        isPrimary = true,
                        sortOrder = 0
                    )
                )
            )
        }
        coVerify {
            taskDao.replaceTaskAttachments(
                "1",
                listOf(
                    TaskAttachmentEntity(
                        id = "attachment-1",
                        taskId = "1",
                        displayName = "mockup.png",
                        mimeType = "image/png",
                        sizeBytes = 9_216,
                        kind = "IMAGE",
                        storageMode = "IMPORTED",
                        reference = "task_attachments/mockup.png",
                        persistedUriPermission = false,
                        isFeaturedImage = true,
                        sortOrder = 0
                    )
                )
            )
        }
        assertEquals(1, syncMutationNotifier.callCount)
    }

    @Test
    fun `deleteTask deletes DAO row and queues sync`() = runTest {
        val now = Instant.now()
        val task = Task(
            id = "1",
            title = "Task 1",
            description = null,
            isCompleted = false,
            priority = 0,
            dueDate = null,
            createdAt = now,
            updatedAt = now
        )
        coEvery { taskDao.deleteTask(any()) } returns Unit

        repository.deleteTask(task)

        coVerify { taskDao.deleteTask(match { it.id == "1" }) }
        assertEquals(1, syncMutationNotifier.callCount)
    }

    // -------------------------------------------------------------------------
    // Transaction atomicity — rollback tests
    // -------------------------------------------------------------------------

    @Test
    fun `saveTask when replaceChecklistItems throws propagates exception without notifying sync`() =
        runTest {
            val now = Instant.now()
            val task = Task(
                id = "rollback-task",
                title = "Atomicity test",
                description = null,
                isCompleted = false,
                priority = 0,
                dueDate = null,
                createdAt = now,
                updatedAt = now,
                checklist = listOf(
                    TaskChecklistItem(id = "ci-1", label = "Step 1", isCompleted = false)
                )
            )
            syncMutationNotifier.reset()
            // upsertTask succeeds but replaceChecklistItems fails.
            coEvery { taskDao.upsertTask(any()) } returns Unit
            coEvery { taskDao.replaceChecklistItems(any(), any()) } throws
                RuntimeException("Simulated DB write failure in checklist replace")
            coEvery { taskDao.replaceTaskContact(any(), any(), any()) } returns Unit
            coEvery { taskDao.replaceTaskActions(any(), any()) } returns Unit
            coEvery { taskDao.replaceTaskAttachments(any(), any()) } returns Unit

            // withTransaction mock: runs the lambda and rethrows any exception (mimics rollback).
            // The @Before already set this up to just invoke the lambda, so the thrown exception
            // will propagate out of saveTask.
            var caughtException: Exception? = null
            try {
                repository.saveTask(task)
            } catch (e: RuntimeException) {
                caughtException = e
            }

            // The exception must propagate to the caller — not swallowed.
            assert(caughtException != null) {
                "saveTask must propagate the exception from a failed checklist write"
            }
            // Sync notification must NOT fire when the transaction failed.
            assertEquals(
                "Sync mutation must NOT be notified when the transaction fails",
                0,
                syncMutationNotifier.callCount
            )
        }

    @Test
    fun `saveTask when upsertTask throws does not attempt checklist write or notify sync`() =
        runTest {
            val now = Instant.now()
            val task = Task(
                id = "rollback-task-2",
                title = "Atomicity test 2",
                description = null,
                isCompleted = false,
                priority = 0,
                dueDate = null,
                createdAt = now,
                updatedAt = now
            )
            syncMutationNotifier.reset()
            // The very first DAO call fails — checklist replace must never be reached.
            coEvery { taskDao.upsertTask(any()) } throws
                RuntimeException("Simulated DB write failure in upsertTask")

            try {
                repository.saveTask(task)
            } catch (_: RuntimeException) { /* expected */ }

            // replaceChecklistItems must not have been called at all.
            coVerify(exactly = 0) { taskDao.replaceChecklistItems(any(), any()) }
            assertEquals(
                "Sync mutation must NOT fire when upsertTask fails",
                0,
                syncMutationNotifier.callCount
            )
        }

    private class RecordingSyncMutationNotifier : SyncMutationNotifier {
        var callCount = 0
            private set

        override fun notifyLocalMutation() {
            callCount += 1
        }

        fun reset() {
            callCount = 0
        }
    }
}
