package com.chronosflow.core.data.backup

import android.content.Context
import android.database.MatrixCursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.chronosflow.core.data.ChronosDatabase
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChronosDataExportRepositoryTest {
    private lateinit var context: Context
    private lateinit var exportDir: File
    private lateinit var testSharedPreferencesFile: File
    private lateinit var testDataStoreFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        exportDir = File(context.filesDir, DATA_EXPORT_DIRECTORY)
        exportDir.deleteRecursively()
        testSharedPreferencesFile = File(context.applicationInfo.dataDir, "shared_prefs/chronos_export_test.xml")
        testDataStoreFile = File(context.filesDir, "datastore/chronos_export_test.preferences_pb")
        testSharedPreferencesFile.delete()
        testDataStoreFile.delete()
    }

    @After
    fun tearDown() {
        exportDir.deleteRecursively()
        context.getSharedPreferences("chronos_export_test", Context.MODE_PRIVATE).edit().clear().commit()
        testSharedPreferencesFile.delete()
        testDataStoreFile.delete()
    }

    @Test
    fun `exportSnapshot writes all user database table rows into json file`() {
        val supportDatabase = mockk<SupportSQLiteDatabase>()
        val openHelper = mockk<SupportSQLiteOpenHelper>()
        val database = mockk<ChronosDatabase>()
        every { database.openHelper } returns openHelper
        every { openHelper.readableDatabase } returns supportDatabase
        every { supportDatabase.version } returns 16
        every { supportDatabase.query(match<String> { it.contains("sqlite_master") }) } returns MatrixCursor(
            arrayOf("name")
        ).apply {
            addRow(arrayOf("tasks"))
            addRow(arrayOf("time_blocks"))
        }
        every { supportDatabase.query("PRAGMA table_info(\"tasks\")") } returns MatrixCursor(
            arrayOf("cid", "name", "type", "notnull", "dflt_value", "pk")
        ).apply {
            addRow(arrayOf<Any?>(0, "id", "TEXT", 1, null, 1))
            addRow(arrayOf<Any?>(1, "title", "TEXT", 1, null, 0))
            addRow(arrayOf<Any?>(2, "isCompleted", "INTEGER", 1, "0", 0))
        }
        every { supportDatabase.query("PRAGMA table_info(\"time_blocks\")") } returns MatrixCursor(
            arrayOf("cid", "name", "type", "notnull", "dflt_value", "pk")
        ).apply {
            addRow(arrayOf<Any?>(0, "id", "TEXT", 1, null, 1))
            addRow(arrayOf<Any?>(1, "title", "TEXT", 1, null, 0))
            addRow(arrayOf<Any?>(2, "durationMinutes", "INTEGER", 1, null, 0))
        }
        every { supportDatabase.query("SELECT * FROM \"tasks\"") } returns MatrixCursor(
            arrayOf("id", "title", "isCompleted")
        ).apply {
            addRow(arrayOf<Any?>("task-1", "Draft export", 0))
        }
        every { supportDatabase.query("SELECT * FROM \"time_blocks\"") } returns MatrixCursor(
            arrayOf("id", "title", "durationMinutes")
        ).apply {
            addRow(arrayOf<Any?>("block-1", "Deep work", 90))
        }
        context.getSharedPreferences("chronos_export_test", Context.MODE_PRIVATE)
            .edit()
            .putString("planningStyle", "dense")
            .commit()
        testDataStoreFile.parentFile?.mkdirs()
        testDataStoreFile.writeBytes(byteArrayOf(1, 2, 3, 4))

        val export = ChronosDataExportRepository(context, database).exportSnapshot()
        val root = JSONObject(export.file.readText())

        assertTrue(export.file.exists())
        assertTrue(export.file.name.startsWith("chronosflow-data-export-"))
        assertEquals(2, export.tableCount)
        assertEquals(2, export.rowCount)
        assertTrue(export.localStateFileCount >= 2)
        assertEquals(2, root.getInt("formatVersion"))
        assertEquals(16, root.getInt("databaseVersion"))
        assertEquals("chronosflow-full-data", root.getString("exportKind"))
        assertEquals("id", root.getJSONObject("tableSchemas").getJSONArray("tasks").getJSONObject(0).getString("name"))
        assertTrue(root.getJSONObject("tableSchemas").getJSONArray("tasks").getJSONObject(0).getBoolean("notnull"))
        assertEquals(1, root.getJSONObject("tableSchemas").getJSONArray("tasks").getJSONObject(0).getInt("pk"))
        assertEquals("Draft export", root.getJSONObject("tables").getJSONArray("tasks").getJSONObject(0).getString("title"))
        assertEquals(90, root.getJSONObject("tables").getJSONArray("time_blocks").getJSONObject(0).getInt("durationMinutes"))
        assertTrue(root.getJSONObject("summary").getInt("sharedPreferenceFileCount") >= 1)
        assertTrue(root.getJSONObject("summary").getInt("dataStoreFileCount") >= 1)
        assertTrue(root.getJSONObject("summary").getInt("localStateByteCount") >= 4)
        val sharedPreferences = root.getJSONObject("localState").getJSONArray("sharedPreferences")
        val exportedPreferenceFile = sharedPreferences.findByRelativePath("chronos_export_test.xml")
        val preferenceText = String(Base64.decode(exportedPreferenceFile.getString("contentBase64"), Base64.NO_WRAP))
        assertTrue(preferenceText.contains("planningStyle"))
        val dataStores = root.getJSONObject("localState").getJSONArray("dataStores")
        val exportedDataStoreFile = dataStores.findByRelativePath("chronos_export_test.preferences_pb")
        assertEquals("AQIDBA==", exportedDataStoreFile.getString("contentBase64"))
        assertFalse(root.getJSONObject("tables").has("room_master_table"))
    }

    private fun org.json.JSONArray.findByRelativePath(relativePath: String): JSONObject {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            if (item.getString("relativePath") == relativePath) {
                return item
            }
        }
        throw AssertionError("Missing exported local state file: $relativePath")
    }
}
