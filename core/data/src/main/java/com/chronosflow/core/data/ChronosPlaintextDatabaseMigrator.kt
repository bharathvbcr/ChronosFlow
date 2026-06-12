package com.chronosflow.core.data

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
        if (encryptedFile.exists()) return DatabaseMigrationResult.SKIPPED

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
                ChronosDatabase.MIGRATION_17_18
            )
            .build()

        val passphrase = secureProvider.obtainPassphraseBytes()
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
                ChronosDatabase.MIGRATION_17_18
            )
        )
        val encryptedDb = secureProvider.create(encryptedBuilder)

        return try {
            val source = plaintextDb.openHelper.writableDatabase
            val destination = encryptedDb.openHelper.writableDatabase
            destination.beginTransaction()
            try {
                TABLE_NAMES.forEach { table ->
                    copyTable(source, destination, table)
                }
                destination.setTransactionSuccessful()
            } finally {
                destination.endTransaction()
            }

            val verified = TABLE_NAMES.all { table ->
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
            if (encryptedFile.exists()) encryptedFile.delete()
            DatabaseMigrationResult.FAILED
        } finally {
            runCatching { plaintextDb.close() }
            runCatching { encryptedDb.close() }
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

        val TABLE_NAMES = listOf(
            "tasks",
            "time_blocks",
            "day_plans",
            "recurrence_rules",
            "habits",
            "habit_schedules",
            "habit_events",
            "medication_plans",
            "medication_schedules",
            "medication_dose_events",
            "medication_safety_profiles",
            "daily_reviews",
            "review_insights",
            "actual_time_segments",
            "alarm_requests",
            "focus_sessions",
            "calendar_events",
            "mood_energy_check_ins",
            "task_checklist_items",
            "task_contact_snapshots",
            "task_contact_methods",
            "task_actions",
            "task_attachments",
            "task_schedules",
            "task_reminder_rules"
        )
    }
}
