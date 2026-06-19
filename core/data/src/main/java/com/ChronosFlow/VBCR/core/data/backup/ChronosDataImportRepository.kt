package com.ChronosFlow.VBCR.core.data.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.data.ChronosPlaintextDatabaseMigrator
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

sealed interface ChronosDataImportResult {
    data class Imported(
        val importedTableCount: Int,
        val importedRowCount: Int,
        val skippedRowCount: Int,
        val skippedNonEmptyTables: List<String>
    ) : ChronosDataImportResult

    data object NothingToImport : ChronosDataImportResult

    data class Unreadable(val reason: String) : ChronosDataImportResult
}

/**
 * Restores a full-data export (see [ChronosDataExportRepository]) into the live database.
 *
 * Restore rules keep this safe to run against any database state:
 * - A table is only filled when it is currently empty; non-empty tables are left
 *   untouched and reported, so a restore can never overwrite or mix into live data.
 * - Columns are matched by name against the current schema; columns the export has
 *   that the schema no longer knows are dropped, and columns the schema added since
 *   the export receive their declared defaults. Unknown tables are ignored. This is
 *   what lets exports written by older app versions restore into newer ones.
 * - Rows that still violate a constraint are skipped and counted rather than
 *   aborting the whole restore.
 */
@Singleton
class ChronosDataImportRepository @Inject constructor(
    private val database: ChronosDatabase
) {
    fun importIntoEmptyTables(json: String): ChronosDataImportResult {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return ChronosDataImportResult.Unreadable("The file is not a valid ChronosFlow backup")
        }
        if (root.optString(ChronosDataExportFormat.KEY_EXPORT_KIND) != ChronosDataExportFormat.EXPORT_KIND_FULL_DATA) {
            return ChronosDataImportResult.Unreadable("The file is not a ChronosFlow data export")
        }
        val version = root.optInt(ChronosDataExportFormat.KEY_FORMAT_VERSION, 0)
        if (version !in 1..ChronosDataExportFormat.FORMAT_VERSION) {
            return ChronosDataImportResult.Unreadable(
                "This backup uses format $version, which this app version cannot read"
            )
        }
        val tablesJson = root.optJSONObject(ChronosDataExportFormat.KEY_TABLES)
            ?: return ChronosDataImportResult.Unreadable("The export contains no table data")

        val db = database.openHelper.writableDatabase
        var importedTableCount = 0
        var importedRowCount = 0
        var skippedRowCount = 0
        val skippedNonEmptyTables = mutableListOf<String>()

        // Rows are restored in sqlite_master order, not dependency order, so defer
        // foreign-key enforcement for the duration of the restore.
        db.setForeignKeyConstraintsEnabled(false)
        try {
            database.runInTransaction {
                tablesJson.keys().forEach { tableName ->
                    if (!ChronosPlaintextDatabaseMigrator.isUserTable(tableName)) return@forEach
                    val rows = tablesJson.optJSONArray(tableName) ?: return@forEach
                    if (rows.length() == 0) return@forEach
                    val columns = currentColumns(db, tableName)
                    if (columns.isEmpty()) return@forEach
                    if (hasRows(db, tableName)) {
                        skippedNonEmptyTables.add(tableName)
                        return@forEach
                    }

                    var importedFromTable = 0
                    for (index in 0 until rows.length()) {
                        val row = rows.getJSONObject(index)
                        val usableColumns = row.keys().asSequence().filter { it in columns }.toList()
                        if (usableColumns.isEmpty()) {
                            skippedRowCount++
                            continue
                        }
                        val sql = buildString {
                            append("INSERT INTO ")
                            append(tableName.sqlIdentifier())
                            append(" (")
                            append(usableColumns.joinToString(", ") { it.sqlIdentifier() })
                            append(") VALUES (")
                            append(usableColumns.joinToString(", ") { "?" })
                            append(")")
                        }
                        val args = Array(usableColumns.size) { argIndex ->
                            row.opt(usableColumns[argIndex]).takeUnless { it === JSONObject.NULL }
                        }
                        try {
                            db.execSQL(sql, args)
                            importedFromTable++
                        } catch (_: Exception) {
                            skippedRowCount++
                        }
                    }
                    if (importedFromTable > 0) {
                        importedTableCount++
                        importedRowCount += importedFromTable
                    }
                }
            }
        } finally {
            db.setForeignKeyConstraintsEnabled(true)
        }

        return if (importedRowCount == 0 && skippedNonEmptyTables.isEmpty()) {
            ChronosDataImportResult.NothingToImport
        } else {
            ChronosDataImportResult.Imported(
                importedTableCount = importedTableCount,
                importedRowCount = importedRowCount,
                skippedRowCount = skippedRowCount,
                skippedNonEmptyTables = skippedNonEmptyTables
            )
        }
    }

    private fun currentColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> =
        db.query("PRAGMA table_info(${tableName.sqlIdentifier()})").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) add(cursor.getString(nameIndex))
                }
            }
        }

    private fun hasRows(db: SupportSQLiteDatabase, tableName: String): Boolean =
        db.query("SELECT EXISTS(SELECT 1 FROM ${tableName.sqlIdentifier()})").use { cursor ->
            cursor.moveToFirst() && cursor.getLong(0) > 0L
        }

    private fun String.sqlIdentifier(): String = "\"" + replace("\"", "\"\"") + "\""
}
