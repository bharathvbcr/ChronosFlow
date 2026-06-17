package com.chronosflow.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chronosflow.core.data.ChronosDatabase
import com.chronosflow.core.data.dao.TaskDao
import com.chronosflow.core.data.dao.TimeBlockDao
import com.chronosflow.core.data.model.TimeBlockEntity
import com.chronosflow.core.data.sync.LocalSyncSnapshot
import com.chronosflow.core.data.sync.LocalSyncSource
import com.chronosflow.core.data.sync.RemoteSyncBatch
import com.chronosflow.core.data.sync.RemoteTaskEntity
import com.chronosflow.core.data.sync.RemoteTimeBlockEntity
import com.chronosflow.core.domain.model.Task
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChronosPortableBackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var transferDir: File
    private lateinit var database: ChronosDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        transferDir = File(context.filesDir, PORTABLE_BACKUP_DIRECTORY)
        transferDir.deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, ChronosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        transferDir.deleteRecursively()
        database.close()
    }

    @Test
    fun `refreshSnapshot writes a full-format snapshot into Android transfer storage`() = runTest {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO tasks (id, title, description, isCompleted, priority, dueDate, createdAt, updatedAt) " +
                "VALUES ('task-1', 'Plan transfer', NULL, 0, 2, NULL, 1, 2)"
        )
        val repository = repository(
            source = FakeLocalSyncSource(LocalSyncSnapshot(tasks = emptyList(), timeBlocks = emptyList()))
        )

        val file = repository.refreshSnapshot()

        assertEquals(File(transferDir, PORTABLE_BACKUP_FILE_NAME).canonicalPath, file.canonicalPath)
        assertTrue(file.exists())
        val text = file.readText()
        assertTrue(text.contains("chronosflow-full-data"))
        assertTrue(text.contains("\"task-1\""))
    }

    @Test
    fun `restoreIfDatabaseEmpty restores a full-format snapshot across all tables`() = runTest {
        val sourceDb = Room.inMemoryDatabaseBuilder(context, ChronosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sourceDb.openHelper.writableDatabase.execSQL(
            "INSERT INTO tasks (id, title, description, isCompleted, priority, dueDate, createdAt, updatedAt) " +
                "VALUES ('task-1', 'Plan transfer', NULL, 0, 2, NULL, 1, 2)"
        )
        sourceDb.openHelper.writableDatabase.execSQL(
            "INSERT INTO goals (id, title, description, category, targetValue, startDate, targetDate, progressValue, isCompleted) " +
                "VALUES ('goal-1', 'Run a 10k', NULL, 'HEALTH', 10, '2026-06-01', NULL, 3, 0)"
        )
        transferDir.mkdirs()
        File(transferDir, PORTABLE_BACKUP_FILE_NAME).writeText(
            ChronosDataExportRepository(context, sourceDb).exportJson(includeLocalState = false).toString()
        )
        sourceDb.close()
        val repository = repository(
            source = FakeLocalSyncSource(LocalSyncSnapshot(tasks = emptyList(), timeBlocks = emptyList()))
        )

        val result = repository.restoreIfDatabaseEmpty()

        val restored = result as PortableBackupRestoreResult.RestoredFull
        assertEquals(2, restored.tableCount)
        assertEquals(2, restored.rowCount)
        assertEquals(0, restored.skippedRowCount)
        database.openHelper.readableDatabase.query("SELECT title FROM goals WHERE id = 'goal-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Run a 10k", cursor.getString(0))
        }
    }

    @Test
    fun `restoreIfDatabaseEmpty imports a transferred portable snapshot`() = runTest {
        val taskDao = mockk<TaskDao>()
        val timeBlockDao = mockk<TimeBlockDao>()
        val codec = ChronosPortableBackupCodec()
        transferDir.mkdirs()
        File(transferDir, PORTABLE_BACKUP_FILE_NAME).writeText(codec.encode(sampleBatch()))
        coEvery { taskDao.upsertTask(any()) } returns Unit
        coEvery { taskDao.replaceChecklistItems(any(), any()) } returns Unit
        coEvery { taskDao.replaceTaskContact(any(), any(), any()) } returns Unit
        coEvery { taskDao.replaceTaskActions(any(), any()) } returns Unit
        coEvery { taskDao.replaceTaskAttachments(any(), any()) } returns Unit
        coEvery { timeBlockDao.insertTimeBlock(any()) } returns Unit

        val repository = repository(
            source = FakeLocalSyncSource(LocalSyncSnapshot(tasks = emptyList(), timeBlocks = emptyList())),
            taskDao = taskDao,
            timeBlockDao = timeBlockDao,
            codec = codec
        )

        val result = repository.restoreIfDatabaseEmpty()

        assertEquals(PortableBackupRestoreResult.Restored(taskCount = 1, timeBlockCount = 1), result)
        coVerify { taskDao.upsertTask(match { it.id == "task-1" && it.title == "Plan transfer" }) }
        coVerify { taskDao.replaceChecklistItems("task-1", emptyList()) }
        coVerify { taskDao.replaceTaskContact("task-1", null, emptyList()) }
        coVerify { taskDao.replaceTaskActions("task-1", emptyList()) }
        coVerify { taskDao.replaceTaskAttachments("task-1", emptyList()) }
        coVerify { timeBlockDao.insertTimeBlock(match { it.id == "block-1" && it.taskId == "task-1" }) }
    }

    @Test
    fun `restoreIfDatabaseEmpty leaves existing local data untouched`() = runTest {
        val taskDao = mockk<TaskDao>(relaxed = true)
        val timeBlockDao = mockk<TimeBlockDao>(relaxed = true)
        transferDir.mkdirs()
        File(transferDir, PORTABLE_BACKUP_FILE_NAME).writeText(ChronosPortableBackupCodec().encode(sampleBatch()))
        val repository = repository(
            source = FakeLocalSyncSource(LocalSyncSnapshot(tasks = listOf(sampleTask()), timeBlocks = emptyList())),
            taskDao = taskDao,
            timeBlockDao = timeBlockDao
        )

        val result = repository.restoreIfDatabaseEmpty()

        assertEquals(PortableBackupRestoreResult.LocalDataAlreadyPresent, result)
        coVerify(exactly = 0) { taskDao.upsertTask(any()) }
        coVerify(exactly = 0) { timeBlockDao.insertTimeBlock(any()) }
    }

    @Test
    fun `restoreIfDatabaseEmpty quarantines an unreadable snapshot for later recovery`() = runTest {
        val taskDao = mockk<TaskDao>(relaxed = true)
        val timeBlockDao = mockk<TimeBlockDao>(relaxed = true)
        transferDir.mkdirs()
        File(transferDir, PORTABLE_BACKUP_FILE_NAME).writeText("{\"formatVersion\":99}")
        val repository = repository(
            source = FakeLocalSyncSource(LocalSyncSnapshot(tasks = emptyList(), timeBlocks = emptyList())),
            taskDao = taskDao,
            timeBlockDao = timeBlockDao
        )

        val result = repository.restoreIfDatabaseEmpty()

        assertEquals(PortableBackupRestoreResult.SnapshotUnreadable, result)
        assertFalse(File(transferDir, PORTABLE_BACKUP_FILE_NAME).exists())
        assertTrue(File(transferDir, "$PORTABLE_BACKUP_FILE_NAME.unreadable").exists())
        coVerify(exactly = 0) { taskDao.upsertTask(any()) }
        coVerify(exactly = 0) { timeBlockDao.insertTimeBlock(any()) }
    }

    private fun repository(
        source: LocalSyncSource,
        taskDao: TaskDao = mockk(relaxed = true),
        timeBlockDao: TimeBlockDao = mockk(relaxed = true),
        codec: ChronosPortableBackupCodec = ChronosPortableBackupCodec()
    ): ChronosPortableBackupRepository = ChronosPortableBackupRepository(
        context = context,
        localSyncSource = source,
        taskDao = taskDao,
        timeBlockDao = timeBlockDao,
        codec = codec,
        dataExportRepository = ChronosDataExportRepository(context, database),
        dataImportRepository = ChronosDataImportRepository(database)
    )

    private class FakeLocalSyncSource(
        private val snapshot: LocalSyncSnapshot
    ) : LocalSyncSource {
        override suspend fun snapshot(): LocalSyncSnapshot = snapshot
    }

    private fun sampleBatch(): RemoteSyncBatch = RemoteSyncBatch(
        tasks = listOf(
            RemoteTaskEntity(
                id = "task-1",
                title = "Plan transfer",
                description = null,
                isCompleted = false,
                priority = 2,
                dueDateEpochMillis = null,
                createdAtEpochMillis = 1_768_176_000_000L,
                updatedAtEpochMillis = 1_768_179_600_000L,
                preferredDurationMinutes = null,
                preferredStartMinuteOfDay = null,
                targetDate = null,
                checklist = emptyList(),
                linkedContact = null,
                actions = emptyList(),
                attachments = emptyList()
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

    private fun sampleTask(): Task = Task(
        id = "task-1",
        title = "Plan transfer",
        description = null,
        isCompleted = false,
        priority = 2,
        dueDate = null,
        createdAt = Instant.parse("2026-01-12T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-12T01:00:00Z")
    )

    private fun sampleTimeBlock(): TimeBlockEntity = TimeBlockEntity(
        id = "block-1",
        date = LocalDate.parse("2026-01-12"),
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
        taskOccurrenceDate = LocalDate.parse("2026-01-12"),
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.parse("2026-01-12T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-12T01:00:00Z")
    )
}
