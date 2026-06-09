package com.chronosflow.core.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronosflow.core.data.ChronosDatabase
import com.chronosflow.core.data.model.TaskAttachmentEntity
import com.chronosflow.core.data.model.TaskEntity
import com.chronosflow.core.data.model.TaskChecklistItemEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: ChronosDatabase
    private lateinit var taskDao: TaskDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChronosDatabase::class.java).build()
        taskDao = db.taskDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeTaskAndReadInList() = runTest {
        val now = Instant.now()
        val task = TaskEntity(
            id = "1",
            title = "Task 1",
            description = "Desc",
            isCompleted = false,
            priority = 0,
            dueDate = null,
            createdAt = now,
            updatedAt = now,
            preferredDurationMinutes = 45,
            preferredStartMinuteOfDay = 10 * 60,
            targetDate = LocalDate.of(2026, 5, 29)
        )
        taskDao.upsertTask(task)
        taskDao.replaceChecklistItems(
            taskId = "1",
            items = listOf(
                TaskChecklistItemEntity(
                    id = "item-1",
                    taskId = "1",
                    label = "Draft plan",
                    isCompleted = false,
                    sortOrder = 0
                )
            )
        )
        
        val tasks = taskDao.observeAllTasksWithChecklist().first()
        assertEquals(1, tasks.size)
        assertEquals("Task 1", tasks[0].task.title)
        assertEquals(45, tasks[0].task.preferredDurationMinutes)
        assertEquals(10 * 60, tasks[0].task.preferredStartMinuteOfDay)
        assertEquals(LocalDate.of(2026, 5, 29), tasks[0].task.targetDate)
        assertEquals("Draft plan", tasks[0].checklistItems.single().label)
    }

    @Test
    fun getTaskById() = runTest {
        val now = Instant.now()
        val task = TaskEntity(
            id = "1",
            title = "Task 1",
            description = "Desc",
            isCompleted = false,
            priority = 0,
            dueDate = null,
            createdAt = now,
            updatedAt = now
        )
        taskDao.upsertTask(task)
        taskDao.replaceChecklistItems(
            taskId = "1",
            items = listOf(
                TaskChecklistItemEntity(
                    id = "item-1",
                    taskId = "1",
                    label = "Check details",
                    isCompleted = true,
                    sortOrder = 0
                )
            )
        )

        val result = taskDao.getTaskWithChecklistById("1")
        assertEquals("Task 1", result?.task?.title)
        assertTrue(result?.checklistItems?.single()?.isCompleted == true)
    }

    @Test
    fun attachmentsAreReturnedWithTask() = runTest {
        val now = Instant.now()
        val task = TaskEntity(
            id = "1",
            title = "Task 1",
            description = "Desc",
            isCompleted = false,
            priority = 0,
            dueDate = null,
            createdAt = now,
            updatedAt = now
        )
        taskDao.upsertTask(task)
        taskDao.replaceTaskAttachments(
            taskId = "1",
            items = listOf(
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

        val result = taskDao.getTaskWithChecklistById("1")

        assertEquals("brief.pdf", result?.taskAttachments?.single()?.displayName)
    }
}
