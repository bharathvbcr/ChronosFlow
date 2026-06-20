package com.ChronosFlow.VBCR.core.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class DatabaseMigrationResult {
    SKIPPED,
    MIGRATED,
    FAILED
}

/**
 * One-time copy from legacy plaintext `chronos_db` to SQLCipher `chronos_db_encrypted`.
 */
@Singleton
class ChronosPlaintextDatabaseMigrator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureProvider: ChronosSecureDatabaseProvider
) {
    fun migrateIfNeeded(): DatabaseMigrationResult {
        if (!ChronosSecureDatabaseProvider.ENCRYPTION_ENABLED) return DatabaseMigrationResult.SKIPPED

        val plaintextFile = context.getDatabasePath(ChronosSecureDatabaseProvider.DATABASE_NAME)
        val encryptedFile = context.getDatabasePath(secureProvider.resolvedName(ChronosSecureDatabaseProvider.DATABASE_NAME))
        if (!plaintextFile.exists()) return DatabaseMigrationResult.SKIPPED

        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_MIGRATION_COMPLETE, false)) {
            return DatabaseMigrationResult.SKIPPED
        }

        val plaintextDb = Room.databaseBuilder(
            context,
            ChronosDatabase::class.java,
            ChronosSecureDatabaseProvider.DATABASE_NAME
        )
            .addMigrations(
                ChronosDatabase.MIGRATION_2_3,
                ChronosDatabase.MIGRATION_3_4,
                ChronosDatabase.MIGRATION_4_5,
                ChronosDatabase.MIGRATION_5_6,
                ChronosDatabase.MIGRATION_6_7,
                ChronosDatabase.MIGRATION_7_8,
                ChronosDatabase.MIGRATION_8_9,
                ChronosDatabase.MIGRATION_9_10,
                ChronosDatabase.MIGRATION_10_11,
                ChronosDatabase.MIGRATION_11_12,
                ChronosDatabase.MIGRATION_12_13,
                ChronosDatabase.MIGRATION_13_14,
                ChronosDatabase.MIGRATION_14_15,
                ChronosDatabase.MIGRATION_15_16,
                ChronosDatabase.MIGRATION_16_17,
                ChronosDatabase.MIGRATION_17_18,
                ChronosDatabase.MIGRATION_18_19,
                ChronosDatabase.MIGRATION_19_20,
                ChronosDatabase.MIGRATION_20_21,
                ChronosDatabase.MIGRATION_21_22,
                ChronosDatabase.MIGRATION_22_23,
                ChronosDatabase.MIGRATION_23_24,
                ChronosDatabase.MIGRATION_24_25,
                ChronosDatabase.MIGRATION_25_26
            )
            .build()

        val encryptedBuilder = secureProvider.databaseBuilder(
            migrations = arrayOf(
                ChronosDatabase.MIGRATION_2_3,
                ChronosDatabase.MIGRATION_3_4,
                ChronosDatabase.MIGRATION_4_5,
                ChronosDatabase.MIGRATION_5_6,
                ChronosDatabase.MIGRATION_6_7,
                ChronosDatabase.MIGRATION_7_8,
                ChronosDatabase.MIGRATION_8_9,
                ChronosDatabase.MIGRATION_9_10,
                ChronosDatabase.MIGRATION_10_11,
                ChronosDatabase.MIGRATION_11_12,
                ChronosDatabase.MIGRATION_12_13,
                ChronosDatabase.MIGRATION_13_14,
                ChronosDatabase.MIGRATION_14_15,
                ChronosDatabase.MIGRATION_15_16,
                ChronosDatabase.MIGRATION_16_17,
                ChronosDatabase.MIGRATION_17_18,
                ChronosDatabase.MIGRATION_18_19,
                ChronosDatabase.MIGRATION_19_20,
                ChronosDatabase.MIGRATION_20_21,
                ChronosDatabase.MIGRATION_21_22,
                ChronosDatabase.MIGRATION_22_23,
                ChronosDatabase.MIGRATION_23_24,
                ChronosDatabase.MIGRATION_24_25,
                ChronosDatabase.MIGRATION_25_26
            )
        )
        val encryptedDb = secureProvider.create(encryptedBuilder)

        var destinationVerifiedEmpty = false
        return try {
            val source = plaintextDb.openHelper.writableDatabase
            val destination = encryptedDb.openHelper.writableDatabase
            val tables = userTableNames(source)

            // A previous attempt may have failed after the encrypted database was
            // created, after which the user kept writing into it. Never overwrite
            // a destination that already holds data.
            if (tables.any { table -> tableCount(destination, table) > 0 }) {
                prefs.edit()
                    .putBoolean(PREF_MIGRATION_COMPLETE, true)
                    .putLong(PREF_MIGRATION_AT, System.currentTimeMillis())
                    .apply()
                return DatabaseMigrationResult.SKIPPED
            }
            destinationVerifiedEmpty = true

            destination.beginTransaction()
            try {
                tables.forEach { table ->
                    copyTable(source, destination, table)
                }
                destination.setTransactionSuccessful()
            } finally {
                destination.endTransaction()
            }

            val verified = tables.all { table ->
                tableCount(source, table) == tableCount(destination, table)
            }
            if (!verified) {
                encryptedFile.delete()
                return DatabaseMigrationResult.FAILED
            }

            plaintextDb.close()
            encryptedDb.close()
            val backup = context.getDatabasePath("${ChronosSecureDatabaseProvider.DATABASE_NAME}.pre_encryption_backup")
            if (backup.exists()) backup.delete()
            plaintextFile.renameTo(backup)

            prefs.edit()
                .putBoolean(PREF_MIGRATION_COMPLETE, true)
                .putLong(PREF_MIGRATION_AT, System.currentTimeMillis())
                .apply()
            DatabaseMigrationResult.MIGRATED
        } catch (_: Exception) {
            // Only remove the destination when this run created or partially
            // filled it; a destination with pre-existing data is never touched.
            if (destinationVerifiedEmpty && encryptedFile.exists()) encryptedFile.delete()
            DatabaseMigrationResult.FAILED
        } finally {
            runCatching { plaintextDb.close() }
            runCatching { encryptedDb.close() }
        }
    }

    private fun userTableNames(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (isUserTable(name)) add(name)
                }
            }
        }

    private fun copyTable(
        source: SupportSQLiteDatabase,
        destination: SupportSQLiteDatabase,
        table: String
    ) {
        destination.execSQL("DELETE FROM `$table`")
        source.query("SELECT * FROM `$table`").use { cursor ->
            if (cursor.count == 0) return
            val columns = cursor.columnNames
            val columnList = columns.joinToString(", ") { "`$it`" }
            val placeholders = columns.joinToString(", ") { "?" }
            val insertSql = "INSERT OR REPLACE INTO `$table` ($columnList) VALUES ($placeholders)"
            while (cursor.moveToNext()) {
                val args = Array(columns.size) { index -> readCursorValue(cursor, index) }
                destination.execSQL(insertSql, args)
            }
        }
    }

    private fun tableCount(db: SupportSQLiteDatabase, table: String): Int {
        db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun readCursorValue(cursor: android.database.Cursor, index: Int): Any? {
        return when (cursor.getType(index)) {
            android.database.Cursor.FIELD_TYPE_NULL -> null
            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
            android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
            android.database.Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
            else -> cursor.getString(index)
        }
    }

    companion object {
        private const val MIGRATION_PREFS = "chronos_db_migration"
        private const val PREF_MIGRATION_COMPLETE = "plaintext_to_encrypted_complete"
        private const val PREF_MIGRATION_AT = "plaintext_to_encrypted_at"

        /**
         * The copy set is read from sqlite_master at migration time so new entities
         * are always included; only SQLite/Room bookkeeping tables are skipped.
         */
        fun isUserTable(name: String): Boolean =
            !name.startsWith("sqlite_") && name != "android_metadata" && name != "room_master_table"
    }
}
