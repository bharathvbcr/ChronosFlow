package com.ChronosFlow.VBCR.core.data.backup

import android.content.Context
import android.database.Cursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

const val DATA_EXPORT_DIRECTORY = "data_exports"

/** Shared contract between the full-data export writer and reader. */
internal object ChronosDataExportFormat {
    const val FORMAT_VERSION = 2
    const val EXPORT_KIND_FULL_DATA = "chronosflow-full-data"
    const val KEY_FORMAT_VERSION = "formatVersion"
    const val KEY_EXPORT_KIND = "exportKind"
    const val KEY_TABLES = "tables"
}

data class ChronosDataExportFile(
    val file: File,
    val tableCount: Int,
    val rowCount: Int,
    val byteCount: Long,
    val localStateFileCount: Int = 0
)

@Singleton
class ChronosDataExportRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: ChronosDatabase
) {
    fun exportSnapshot(): ChronosDataExportFile {
        val exportedAt = Instant.now()
        val root = exportJson(includeLocalState = true, exportedAt = exportedAt)
        val summary = root.getJSONObject(KEY_SUMMARY)

        val exportDir = File(context.filesDir, DATA_EXPORT_DIRECTORY)
        exportDir.mkdirs()
        val exportFile = File(exportDir, "chronosflow-data-export-${exportedAt.fileStamp()}.json")
        exportFile.writeText(root.toString(2))

        return ChronosDataExportFile(
            file = exportFile,
            tableCount = summary.getInt(KEY_TABLE_COUNT),
            rowCount = summary.getInt(KEY_ROW_COUNT),
            byteCount = exportFile.length(),
            localStateFileCount = summary.getInt(KEY_LOCAL_STATE_FILE_COUNT)
        )
    }

    /**
     * Builds the full-data export document. With [includeLocalState] false only the
     * database tables travel — used for the device-transfer snapshot, where Android's
     * backup rules already carry the preference and DataStore files separately.
     */
    fun exportJson(includeLocalState: Boolean, exportedAt: Instant = Instant.now()): JSONObject {
        val readableDatabase = database.openHelper.readableDatabase
        val tables = readableDatabase.exportableTableNames()
        val tableSchemasJson = JSONObject()
        val tablesJson = JSONObject()
        var rowCount = 0

        tables.forEach { tableName ->
            tableSchemasJson.put(tableName, readableDatabase.exportTableSchema(tableName))
            val rows = readableDatabase.exportTableRows(tableName)
            tablesJson.put(tableName, rows)
            rowCount += rows.length()
        }

        val sharedPreferenceFiles = if (includeLocalState) {
            context.sharedPreferenceDirectory().exportLocalStateFiles(kind = "shared-preferences-xml")
        } else {
            emptyList()
        }
        val dataStoreFiles = if (includeLocalState) {
            File(context.filesDir, DATASTORE_DIRECTORY).exportLocalStateFiles(kind = "datastore-file")
        } else {
            emptyList()
        }
        val localStateFileCount = sharedPreferenceFiles.size + dataStoreFiles.size
        val localStateByteCount = sharedPreferenceFiles.totalByteCount() + dataStoreFiles.totalByteCount()

        val root = JSONObject()
            .put(KEY_FORMAT_VERSION, FORMAT_VERSION)
            .put(KEY_EXPORT_KIND, ChronosDataExportFormat.EXPORT_KIND_FULL_DATA)
            .put(KEY_EXPORTED_AT, exportedAt.toString())
            .put(KEY_DATABASE_VERSION, readableDatabase.version)
            .put(
                KEY_SUMMARY,
                JSONObject()
                    .put(KEY_TABLE_COUNT, tables.size)
                    .put(KEY_ROW_COUNT, rowCount)
                    .put(KEY_SHARED_PREFERENCE_FILE_COUNT, sharedPreferenceFiles.size)
                    .put(KEY_DATASTORE_FILE_COUNT, dataStoreFiles.size)
                    .put(KEY_LOCAL_STATE_FILE_COUNT, localStateFileCount)
                    .put(KEY_LOCAL_STATE_BYTE_COUNT, localStateByteCount)
            )
            .put(KEY_TABLE_SCHEMAS, tableSchemasJson)
            .put(KEY_TABLES, tablesJson)
        if (includeLocalState) {
            root.put(
                KEY_LOCAL_STATE,
                JSONObject()
                    .put(KEY_SHARED_PREFERENCES, sharedPreferenceFiles.toJsonArray())
                    .put(KEY_DATASTORES, dataStoreFiles.toJsonArray())
            )
        }
        return root
    }

    private fun SupportSQLiteDatabase.exportableTableNames(): List<String> =
        query(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
                AND name NOT LIKE 'sqlite_%'
                AND name != 'android_metadata'
                AND name != 'room_master_table'
            ORDER BY name
            """.trimIndent()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }

    private fun SupportSQLiteDatabase.exportTableSchema(tableName: String): JSONArray =
        query("PRAGMA table_info(${tableName.sqlIdentifier()})").use { cursor ->
            JSONArray().also { columns ->
                while (cursor.moveToNext()) {
                    columns.put(
                        JSONObject()
                            .put(KEY_COLUMN_ID, cursor.longValue(KEY_COLUMN_ID))
                            .put(KEY_COLUMN_NAME, cursor.stringValue(KEY_COLUMN_NAME))
                            .put(KEY_COLUMN_TYPE, cursor.stringValue(KEY_COLUMN_TYPE))
                            .put(KEY_COLUMN_NOT_NULL, cursor.longValue(KEY_COLUMN_NOT_NULL) != 0L)
                            .put(KEY_COLUMN_DEFAULT_VALUE, cursor.optionalValue(KEY_COLUMN_DEFAULT_VALUE))
                            .put(KEY_COLUMN_PRIMARY_KEY_POSITION, cursor.longValue(KEY_COLUMN_PRIMARY_KEY_POSITION))
                    )
                }
            }
        }

    private fun SupportSQLiteDatabase.exportTableRows(tableName: String): JSONArray =
        query("SELECT * FROM ${tableName.sqlIdentifier()}").use { cursor ->
            JSONArray().also { rows ->
                while (cursor.moveToNext()) {
                    rows.put(cursor.currentRowJson())
                }
            }
        }

    private fun Cursor.currentRowJson(): JSONObject =
        JSONObject().also { row ->
            for (index in 0 until columnCount) {
                row.put(getColumnName(index), exportValue(index))
            }
        }

    private fun Cursor.longValue(columnName: String): Long {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else 0L
    }

    private fun Cursor.stringValue(columnName: String): String {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else ""
    }

    private fun Cursor.optionalValue(columnName: String): Any {
        val index = getColumnIndex(columnName)
        return if (index >= 0) exportValue(index) else JSONObject.NULL
    }

    private fun Cursor.exportValue(index: Int): Any =
        when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
            Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(getBlob(index), Base64.NO_WRAP)
            else -> getString(index)
        }

    private fun String.sqlIdentifier(): String = "\"" + replace("\"", "\"\"") + "\""

    private fun Instant.fileStamp(): String = EXPORT_FILE_STAMP_FORMATTER.format(this)

    private fun Context.sharedPreferenceDirectory(): File =
        File(applicationInfo.dataDir, SHARED_PREFERENCES_DIRECTORY)

    private fun File.exportLocalStateFiles(kind: String): List<ExportedLocalStateFile> {
        if (!exists() || !isDirectory) return emptyList()
        val root = this
        return walkTopDown()
            .filter { file -> file.isFile && file.canRead() }
            .sortedBy { file -> file.relativeTo(root).invariantSeparatorsPath }
            .mapNotNull { file ->
                runCatching {
                    val bytes = file.readBytes()
                    ExportedLocalStateFile(
                        byteCount = bytes.size.toLong(),
                        json = JSONObject()
                            .put(KEY_KIND, kind)
                            .put(KEY_RELATIVE_PATH, file.relativeTo(root).invariantSeparatorsPath)
                            .put(KEY_BYTE_COUNT, bytes.size)
                            .put(KEY_LAST_MODIFIED_AT, Instant.ofEpochMilli(file.lastModified()).toString())
                            .put(KEY_CONTENT_BASE64, Base64.encodeToString(bytes, Base64.NO_WRAP))
                    )
                }.getOrNull()
            }
            .toList()
    }

    private fun List<ExportedLocalStateFile>.toJsonArray(): JSONArray =
        JSONArray().also { array ->
            forEach { file -> array.put(file.json) }
        }

    private fun List<ExportedLocalStateFile>.totalByteCount(): Long =
        sumOf { file -> file.byteCount }

    private data class ExportedLocalStateFile(
        val json: JSONObject,
        val byteCount: Long
    )

    private companion object {
        const val FORMAT_VERSION = ChronosDataExportFormat.FORMAT_VERSION
        const val SHARED_PREFERENCES_DIRECTORY = "shared_prefs"
        const val DATASTORE_DIRECTORY = "datastore"
        const val KEY_FORMAT_VERSION = ChronosDataExportFormat.KEY_FORMAT_VERSION
        const val KEY_EXPORT_KIND = ChronosDataExportFormat.KEY_EXPORT_KIND
        const val KEY_EXPORTED_AT = "exportedAt"
        const val KEY_DATABASE_VERSION = "databaseVersion"
        const val KEY_SUMMARY = "summary"
        const val KEY_TABLE_COUNT = "tableCount"
        const val KEY_ROW_COUNT = "rowCount"
        const val KEY_SHARED_PREFERENCE_FILE_COUNT = "sharedPreferenceFileCount"
        const val KEY_DATASTORE_FILE_COUNT = "dataStoreFileCount"
        const val KEY_LOCAL_STATE_FILE_COUNT = "localStateFileCount"
        const val KEY_LOCAL_STATE_BYTE_COUNT = "localStateByteCount"
        const val KEY_TABLE_SCHEMAS = "tableSchemas"
        const val KEY_TABLES = ChronosDataExportFormat.KEY_TABLES
        const val KEY_LOCAL_STATE = "localState"
        const val KEY_SHARED_PREFERENCES = "sharedPreferences"
        const val KEY_DATASTORES = "dataStores"
        const val KEY_KIND = "kind"
        const val KEY_RELATIVE_PATH = "relativePath"
        const val KEY_BYTE_COUNT = "byteCount"
        const val KEY_LAST_MODIFIED_AT = "lastModifiedAt"
        const val KEY_CONTENT_BASE64 = "contentBase64"
        const val KEY_COLUMN_ID = "cid"
        const val KEY_COLUMN_NAME = "name"
        const val KEY_COLUMN_TYPE = "type"
        const val KEY_COLUMN_NOT_NULL = "notnull"
        const val KEY_COLUMN_DEFAULT_VALUE = "dflt_value"
        const val KEY_COLUMN_PRIMARY_KEY_POSITION = "pk"

        val EXPORT_FILE_STAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'").withZone(ZoneOffset.UTC)
    }
}
