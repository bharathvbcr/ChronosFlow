package com.ChronosFlow.VBCR.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChronosDataImportRepositoryTest {
    private lateinit var context: Context
    private lateinit var sourceDb: ChronosDatabase
    private lateinit var targetDb: ChronosDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sourceDb = inMemoryDatabase()
        targetDb = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun `import restores every exported table into an empty database`() {
        seedSourceData()
        val json = ChronosDataExportRepository(context, sourceDb)
            .exportJson(includeLocalState = false)
            .toString()

        val result = ChronosDataImportRepository(targetDb).importIntoEmptyTables(json)

        val imported = result as ChronosDataImportResult.Imported
        assertEquals(3, imported.importedTableCount)
        assertEquals(3, imported.importedRowCount)
        assertEquals(0, imported.skippedRowCount)
        assertTrue(imported.skippedNonEmptyTables.isEmpty())
        assertEquals("Plan transfer", targetSingleString("SELECT title FROM tasks WHERE id = 'task-1'"))
        assertEquals("Run a 10k", targetSingleString("SELECT title FROM goals WHERE id = 'goal-1'"))
        assertEquals("MANUAL", targetSingleString("SELECT source FROM sleep_tracks WHERE id = 'sleep-1'"))
    }

    @Test
    fun `import leaves tables with existing data untouched and fills the rest`() {
        seedSourceData()
        targetDb.openHelper.writableDatabase.execSQL(
            "INSERT INTO tasks (id, title, description, isCompleted, priority, dueDate, createdAt, updatedAt) " +
                "VALUES ('task-live', 'Already here', NULL, 0, 1, NULL, 5, 5)"
        )
        val json = ChronosDataExportRepository(context, sourceDb)
            .exportJson(includeLocalState = false)
            .toString()

        val result = ChronosDataImportRepository(targetDb).importIntoEmptyTables(json)

        val imported = result as ChronosDataImportResult.Imported
        assertEquals(listOf("tasks"), imported.skippedNonEmptyTables)
        assertEquals(1L, targetSingleLong("SELECT COUNT(*) FROM tasks"))
        assertEquals("Already here", targetSingleString("SELECT title FROM tasks WHERE id = 'task-live'"))
        assertEquals(1L, targetSingleLong("SELECT COUNT(*) FROM goals"))
        assertEquals(1L, targetSingleLong("SELECT COUNT(*) FROM sleep_tracks"))
    }

    @Test
    fun `import ignores unknown tables and columns from a different schema version`() {
        val json = """
            {
              "formatVersion": 1,
              "exportKind": "chronosflow-full-data",
              "tables": {
                "tasks": [
                  {"id": "task-1", "title": "From old export", "isCompleted": 0, "priority": 1,
                   "createdAt": 1, "updatedAt": 2, "columnRemovedLongAgo": "ignored"}
                ],
                "table_that_no_longer_exists": [
                  {"anything": 1}
                ]
              }
            }
        """.trimIndent()

        val result = ChronosDataImportRepository(targetDb).importIntoEmptyTables(json)

        val imported = result as ChronosDataImportResult.Imported
        assertEquals(1, imported.importedTableCount)
        assertEquals(1, imported.importedRowCount)
        assertEquals("From old export", targetSingleString("SELECT title FROM tasks WHERE id = 'task-1'"))
    }

    @Test
    fun `import rejects files that are not ChronosFlow exports`() {
        val importer = ChronosDataImportRepository(targetDb)

        assertTrue(importer.importIntoEmptyTables("not json at all") is ChronosDataImportResult.Unreadable)
        assertTrue(
            importer.importIntoEmptyTables("""{"exportKind": "some-other-app"}""")
                is ChronosDataImportResult.Unreadable
        )
        assertTrue(
            importer.importIntoEmptyTables(
                """{"formatVersion": 99, "exportKind": "chronosflow-full-data", "tables": {}}"""
            ) is ChronosDataImportResult.Unreadable
        )
        assertEquals(0L, targetSingleLong("SELECT COUNT(*) FROM tasks"))
    }

    @Test
    fun `import reports when the export holds no rows`() {
        val json = """
            {"formatVersion": 2, "exportKind": "chronosflow-full-data", "tables": {"tasks": []}}
        """.trimIndent()

        val result = ChronosDataImportRepository(targetDb).importIntoEmptyTables(json)

        assertEquals(ChronosDataImportResult.NothingToImport, result)
    }

    private fun inMemoryDatabase(): ChronosDatabase =
        Room.inMemoryDatabaseBuilder(context, ChronosDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun seedSourceData() {
        val db = sourceDb.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO tasks (id, title, description, isCompleted, priority, dueDate, createdAt, updatedAt) " +
                "VALUES ('task-1', 'Plan transfer', NULL, 0, 2, NULL, 1, 2)"
        )
        db.execSQL(
            "INSERT INTO goals (id, title, description, category, targetValue, startDate, targetDate, progressValue, isCompleted) " +
                "VALUES ('goal-1', 'Run a 10k', NULL, 'HEALTH', 10, '2026-06-01', NULL, 3, 0)"
        )
        db.execSQL(
            "INSERT INTO sleep_tracks (id, date, plannedStartMinute, plannedEndMinute, actualStartMinute, " +
                "actualEndMinute, sleepQuality, windDownNotes, interruptedCount, source) " +
                "VALUES ('sleep-1', '2026-06-11', NULL, NULL, 1380, 420, 4, NULL, 1, 'MANUAL')"
        )
    }

    private fun targetSingleString(sql: String): String =
        targetDb.openHelper.readableDatabase.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun targetSingleLong(sql: String): Long =
        targetDb.openHelper.readableDatabase.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
