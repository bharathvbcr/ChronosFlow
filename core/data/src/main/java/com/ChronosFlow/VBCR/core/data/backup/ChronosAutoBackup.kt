package com.ChronosFlow.VBCR.core.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Current settings + last-run status for the scheduled folder backup, shaped for the UI. */
data class AutoBackupStatus(
    val enabled: Boolean,
    val folderUri: String,
    val lastResult: String
) {
    val hasFolder: Boolean get() = folderUri.isNotEmpty()
}

sealed interface AutoBackupOutcome {
    data class Success(val fileName: String) : AutoBackupOutcome
    data class Failure(val message: String) : AutoBackupOutcome
}

/**
 * Writes the full local-data export into a user-chosen folder (a persisted Storage Access Framework
 * tree URI) on a daily WorkManager schedule, keeping the most recent [MAX_RETAINED_BACKUPS] files.
 * This is the local-first stand-in for cloud sync: the user keeps a rolling copy of their data
 * somewhere durable (e.g. a synced Drive/Dropbox folder) without ChronosFlow ever holding an account.
 */
@Singleton
class ChronosAutoBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: ChronosPreferencesDataSource,
    private val dataExportRepository: ChronosDataExportRepository,
    private val dataImportRepository: ChronosDataImportRepository
) {
    fun status(): AutoBackupStatus = AutoBackupStatus(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        folderUri = preferences.getString(KEY_FOLDER_URI, ""),
        lastResult = preferences.getString(KEY_LAST_RESULT, "")
    )

    private fun folderUri(): Uri? =
        preferences.getString(KEY_FOLDER_URI, "").takeIf { it.isNotEmpty() }?.let(Uri::parse)

    /** Persist the chosen folder (caller must already hold a persisted permission) and enable backups. */
    fun configureFolder(treeUri: Uri) {
        preferences.putString(KEY_FOLDER_URI, treeUri.toString())
        setEnabled(true)
    }

    fun setEnabled(enabled: Boolean) {
        preferences.putBoolean(KEY_ENABLED, enabled)
        if (enabled && folderUri() != null) schedule() else cancel()
    }

    /** Re-enqueue the periodic worker if backups were left enabled (call once on app startup). */
    fun ensureScheduled() {
        if (preferences.getBoolean(KEY_ENABLED, false) && folderUri() != null) {
            schedule()
        }
    }

    suspend fun runBackup(): AutoBackupOutcome {
        val treeUri = folderUri()
            ?: return recordFailure("No backup folder selected")
        return withContext(Dispatchers.IO) {
            runCatching {
                val export = dataExportRepository.exportSnapshot()
                val fileName = try {
                    writeToTree(treeUri, export.file)
                } finally {
                    export.file.delete()
                }
                pruneOldBackups(treeUri)
                fileName
            }.fold(
                onSuccess = { fileName -> recordSuccess(fileName) },
                onFailure = { error -> recordFailure(error.message ?: "Backup failed") }
            )
        }
    }

    /**
     * Restores a previously written backup file into the database. Only tables that
     * are currently empty are filled, so running this on a device with existing data
     * tops up what's missing and never overwrites anything.
     */
    suspend fun restoreBackup(documentUri: Uri): AutoBackupOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(documentUri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Could not open the selected backup file")
            when (val result = dataImportRepository.importIntoEmptyTables(text)) {
                is ChronosDataImportResult.Imported -> buildString {
                    append("Restored ${result.importedRowCount} records into ${result.importedTableCount} tables")
                    if (result.skippedNonEmptyTables.isNotEmpty()) {
                        append("; left ${result.skippedNonEmptyTables.size} tables with existing data untouched")
                    }
                    if (result.skippedRowCount > 0) {
                        append("; ${result.skippedRowCount} records could not be restored")
                    }
                }
                ChronosDataImportResult.NothingToImport -> "The backup contains no data to restore"
                is ChronosDataImportResult.Unreadable -> error(result.reason)
            }
        }.fold(
            onSuccess = { message -> AutoBackupOutcome.Success(message) },
            onFailure = { error -> AutoBackupOutcome.Failure(error.message ?: "Restore failed") }
        )
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<ChronosAutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun writeToTree(treeUri: Uri, source: File): String {
        val resolver = context.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val fileName = "$BACKUP_PREFIX${FILE_STAMP_FORMATTER.format(Instant.now())}.json"
        val targetUri = DocumentsContract.createDocument(resolver, parentUri, MIME_JSON, fileName)
            ?: error("Could not create a backup file in the selected folder")
        resolver.openOutputStream(targetUri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Could not open the backup file for writing")
        return fileName
    }

    private fun pruneOldBackups(treeUri: Uri) {
        val resolver = context.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val backups = mutableListOf<Pair<String, String>>() // documentId to displayName
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(BACKUP_PREFIX) && name.endsWith(".json")) {
                    backups.add(documentId to name)
                }
            }
        }
        // Names embed a sortable UTC timestamp, so descending name order is newest-first.
        backups.sortedByDescending { it.second }
            .drop(MAX_RETAINED_BACKUPS)
            .forEach { (documentId, _) ->
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }
            }
    }

    private fun recordSuccess(fileName: String): AutoBackupOutcome {
        preferences.putString(KEY_LAST_RESULT, "Saved $fileName")
        return AutoBackupOutcome.Success(fileName)
    }

    private fun recordFailure(message: String): AutoBackupOutcome {
        preferences.putString(KEY_LAST_RESULT, "Last backup failed: $message")
        return AutoBackupOutcome.Failure(message)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "chronosflow_auto_backup"
        const val MAX_RETAINED_BACKUPS = 7
        private const val BACKUP_PREFIX = "chronosflow-backup-"
        private const val MIME_JSON = "application/json"
        private const val KEY_ENABLED = "auto_backup.enabled"
        private const val KEY_FOLDER_URI = "auto_backup.folder_uri"
        private const val KEY_LAST_RESULT = "auto_backup.last_result"
        private val FILE_STAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'").withZone(ZoneOffset.UTC)
    }
}

class ChronosAutoBackupWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors.fromApplication(
            applicationContext,
            ChronosAutoBackupEntryPoint::class.java
        ).autoBackupManager()

        return when (manager.runBackup()) {
            is AutoBackupOutcome.Success -> Result.success()
            is AutoBackupOutcome.Failure -> Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChronosAutoBackupEntryPoint {
    fun autoBackupManager(): ChronosAutoBackupManager
}
