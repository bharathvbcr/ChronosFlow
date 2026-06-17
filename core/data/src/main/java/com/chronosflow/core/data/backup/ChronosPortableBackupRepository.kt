package com.chronosflow.core.data.backup

import android.content.Context
import com.chronosflow.core.data.dao.TaskDao
import com.chronosflow.core.data.dao.TimeBlockDao
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.data.model.TimeBlockEntity
import com.chronosflow.core.data.sync.LocalSyncSnapshot
import com.chronosflow.core.data.sync.LocalSyncSource
import com.chronosflow.core.data.sync.RemoteTaskActionEntity
import com.chronosflow.core.data.sync.RemoteTaskAttachmentEntity
import com.chronosflow.core.data.sync.RemoteTaskChecklistItemEntity
import com.chronosflow.core.data.sync.RemoteTaskContactEntity
import com.chronosflow.core.data.sync.RemoteTaskContactMethodEntity
import com.chronosflow.core.data.sync.RemoteTaskEntity
import com.chronosflow.core.data.sync.RemoteTimeBlockEntity
import com.chronosflow.core.domain.model.ContactMethodKind
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskAction
import com.chronosflow.core.domain.model.TaskActionType
import com.chronosflow.core.domain.model.TaskAttachment
import com.chronosflow.core.domain.model.TaskAttachmentKind
import com.chronosflow.core.domain.model.TaskAttachmentStorageMode
import com.chronosflow.core.domain.model.TaskChecklistItem
import com.chronosflow.core.domain.model.TaskContactMethod
import com.chronosflow.core.domain.model.TaskContactSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

const val PORTABLE_BACKUP_DIRECTORY = "android_transfer"
const val PORTABLE_BACKUP_FILE_NAME = "chronosflow_portable_backup.json"

@Singleton
class ChronosPortableBackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localSyncSource: LocalSyncSource,
    private val taskDao: TaskDao,
    private val timeBlockDao: TimeBlockDao,
    private val codec: ChronosPortableBackupCodec,
    private val dataExportRepository: ChronosDataExportRepository,
    private val dataImportRepository: ChronosDataImportRepository
) {
    suspend fun refreshSnapshot(): File {
        val backupFile = portableBackupFile()
        backupFile.parentFile?.mkdirs()
        val tempFile = File(backupFile.parentFile, "${backupFile.name}.tmp")
        // Local state (prefs, DataStore) travels through the Android backup rules
        // directly, so the snapshot only needs to carry the database tables.
        tempFile.writeText(dataExportRepository.exportJson(includeLocalState = false).toString())
        if (backupFile.exists() && !backupFile.delete()) {
            tempFile.delete()
            error("Unable to replace existing ChronosFlow portable backup")
        }
        if (!tempFile.renameTo(backupFile)) {
            tempFile.copyTo(backupFile, overwrite = true)
            tempFile.delete()
        }
        return backupFile
    }

    suspend fun restoreIfDatabaseEmpty(): PortableBackupRestoreResult {
        val backupFile = portableBackupFile()
        if (!backupFile.exists()) {
            return PortableBackupRestoreResult.NoSnapshot
        }
        if (!localSyncSource.snapshot().isEmpty()) {
            return PortableBackupRestoreResult.LocalDataAlreadyPresent
        }

        val text = backupFile.readText()
        restoreFullFormat(text)?.let { return it }

        // Legacy two-table snapshot written by builds that predate the full-export format.
        val batch = try {
            codec.decode(text)
        } catch (_: Exception) {
            return quarantineSnapshot(backupFile)
        }
        batch.tasks.forEach { remoteTask ->
            importTask(remoteTask.toDomainTask())
        }
        batch.timeBlocks.forEach { remoteTimeBlock ->
            timeBlockDao.insertTimeBlock(remoteTimeBlock.toEntity())
        }
        return PortableBackupRestoreResult.Restored(
            taskCount = batch.tasks.size,
            timeBlockCount = batch.timeBlocks.size
        )
    }

    /** Returns null when [text] is not a full-data export, so legacy decoding can run. */
    private fun restoreFullFormat(text: String): PortableBackupRestoreResult? {
        val isFullExport = runCatching {
            JSONObject(text).optString(ChronosDataExportFormat.KEY_EXPORT_KIND) ==
                ChronosDataExportFormat.EXPORT_KIND_FULL_DATA
        }.getOrDefault(false)
        if (!isFullExport) return null
        return when (val result = dataImportRepository.importIntoEmptyTables(text)) {
            is ChronosDataImportResult.Imported -> PortableBackupRestoreResult.RestoredFull(
                tableCount = result.importedTableCount,
                rowCount = result.importedRowCount,
                skippedRowCount = result.skippedRowCount
            )
            ChronosDataImportResult.NothingToImport -> PortableBackupRestoreResult.RestoredFull(
                tableCount = 0,
                rowCount = 0,
                skippedRowCount = 0
            )
            is ChronosDataImportResult.Unreadable -> quarantineSnapshot(portableBackupFile())
        }
    }

    /**
     * Parks an undecodable snapshot (corrupt, or written by a newer format) instead of
     * throwing: the startup refresh that follows would otherwise overwrite the only
     * copy of the transferred data with an empty export.
     */
    private fun quarantineSnapshot(backupFile: File): PortableBackupRestoreResult {
        val quarantined = File(backupFile.parentFile, "$PORTABLE_BACKUP_FILE_NAME.unreadable")
        quarantined.delete()
        backupFile.renameTo(quarantined)
        return PortableBackupRestoreResult.SnapshotUnreadable
    }

    private suspend fun importTask(task: Task) {
        taskDao.upsertTask(task.toEntity())
        taskDao.replaceChecklistItems(
            task.id,
            task.checklist.mapIndexed { index, item -> item.toEntity(task.id, index) }
        )
        taskDao.replaceTaskContact(
            task.id,
            task.linkedContact?.toEntity(task.id),
            task.linkedContact?.methods
                ?.mapIndexed { index, method -> method.toEntity(task.id, index) }
                .orEmpty()
        )
        taskDao.replaceTaskActions(
            task.id,
            task.actions.mapIndexed { index, action -> action.toEntity(task.id, index) }
        )
        taskDao.replaceTaskAttachments(
            task.id,
            task.attachments.mapIndexed { index, attachment -> attachment.toEntity(task.id, index) }
        )
    }

    private fun portableBackupFile(): File =
        File(File(context.filesDir, PORTABLE_BACKUP_DIRECTORY), PORTABLE_BACKUP_FILE_NAME)
}

sealed interface PortableBackupRestoreResult {
    data object NoSnapshot : PortableBackupRestoreResult
    data object LocalDataAlreadyPresent : PortableBackupRestoreResult
    data object SnapshotUnreadable : PortableBackupRestoreResult
    data class Restored(
        val taskCount: Int,
        val timeBlockCount: Int
    ) : PortableBackupRestoreResult
    data class RestoredFull(
        val tableCount: Int,
        val rowCount: Int,
        val skippedRowCount: Int
    ) : PortableBackupRestoreResult
}

private fun LocalSyncSnapshot.isEmpty(): Boolean = tasks.isEmpty() && timeBlocks.isEmpty()

private fun RemoteTaskEntity.toDomainTask(): Task = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    priority = priority,
    dueDate = dueDateEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    preferredDurationMinutes = preferredDurationMinutes,
    preferredStartMinuteOfDay = preferredStartMinuteOfDay,
    targetDate = targetDate?.let(LocalDate::parse),
    checklist = checklist.map { it.toDomainChecklistItem() },
    linkedContact = linkedContact?.toDomainContact(),
    actions = actions.map { it.toDomainAction() },
    attachments = attachments.map { it.toDomainAttachment() }
)

private fun RemoteTaskChecklistItemEntity.toDomainChecklistItem(): TaskChecklistItem =
    TaskChecklistItem(
        id = id,
        label = label,
        isCompleted = isCompleted
    )

private fun RemoteTaskContactEntity.toDomainContact(): TaskContactSnapshot =
    TaskContactSnapshot(
        displayName = displayName,
        lookupKey = lookupKey,
        methods = methods.map { it.toDomainContactMethod() }
    )

private fun RemoteTaskContactMethodEntity.toDomainContactMethod(): TaskContactMethod =
    TaskContactMethod(
        id = id,
        kind = ContactMethodKind.valueOf(kind),
        label = label,
        value = value,
        normalizedValue = normalizedValue,
        isPrimary = isPrimary
    )

private fun RemoteTaskActionEntity.toDomainAction(): TaskAction =
    TaskAction(
        id = id,
        type = TaskActionType.valueOf(type),
        label = label,
        value = value,
        isPrimary = isPrimary
    )

private fun RemoteTaskAttachmentEntity.toDomainAttachment(): TaskAttachment =
    TaskAttachment(
        id = id,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        kind = TaskAttachmentKind.valueOf(kind),
        storageMode = TaskAttachmentStorageMode.valueOf(storageMode),
        reference = reference,
        persistedUriPermission = persistedUriPermission,
        isFeaturedImage = isFeaturedImage
    )

private fun RemoteTimeBlockEntity.toEntity(): TimeBlockEntity = TimeBlockEntity(
    id = id,
    date = LocalDate.parse(date),
    title = title,
    category = category,
    startMinuteOfDay = startMinuteOfDay,
    durationMinutes = durationMinutes,
    timezone = timezone,
    source = source,
    provenance = provenance,
    flexibility = flexibility,
    energyLevel = energyLevel,
    taskId = taskId,
    calendarEventId = calendarEventId,
    medicationPlanId = medicationPlanId,
    habitId = habitId,
    isLocked = isLocked,
    isProtected = isProtected,
    recurrenceRuleId = recurrenceRuleId,
    taskOccurrenceDate = taskOccurrenceDate?.let(LocalDate::parse),
    actualStartMinuteOfDay = actualStartMinuteOfDay,
    actualEndMinuteOfDay = actualEndMinuteOfDay,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
)
