package com.ChronosFlow.VBCR.feature.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class TaskAttachmentDraft(
    val id: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val kind: TaskAttachmentKind,
    val storageMode: TaskAttachmentStorageMode,
    val sourceUri: String? = null,
    val importedPath: String? = null,
    val persistedUriPermission: Boolean = false,
    val isFeaturedImage: Boolean = false,
    val modeLocked: Boolean = false
)

internal fun TaskAttachment.toDraft(): TaskAttachmentDraft = TaskAttachmentDraft(
    id = id,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    kind = kind,
    storageMode = storageMode,
    sourceUri = reference.takeIf { storageMode == TaskAttachmentStorageMode.LINKED },
    importedPath = reference.takeIf { storageMode == TaskAttachmentStorageMode.IMPORTED },
    persistedUriPermission = persistedUriPermission,
    isFeaturedImage = isFeaturedImage,
    modeLocked = storageMode == TaskAttachmentStorageMode.IMPORTED
)

internal fun normalizeTaskAttachmentDrafts(
    drafts: List<TaskAttachmentDraft>
): List<TaskAttachmentDraft> {
    val featuredImageId = drafts.firstOrNull { it.kind == TaskAttachmentKind.IMAGE && it.isFeaturedImage }?.id
        ?: drafts.firstOrNull { it.kind == TaskAttachmentKind.IMAGE }?.id
    return drafts.map { draft ->
        draft.copy(
            isFeaturedImage = draft.kind == TaskAttachmentKind.IMAGE && draft.id == featuredImageId
        )
    }
}

internal fun hasInvalidTaskAttachmentDrafts(drafts: List<TaskAttachmentDraft>): Boolean {
    return drafts.any { draft ->
        draft.displayName.isBlank() || when (draft.storageMode) {
            TaskAttachmentStorageMode.LINKED -> draft.sourceUri.isNullOrBlank()
            TaskAttachmentStorageMode.IMPORTED -> draft.importedPath.isNullOrBlank() && draft.sourceUri.isNullOrBlank()
        }
    }
}

internal suspend fun buildTaskAttachmentDrafts(
    context: Context,
    uris: List<Uri>
): List<TaskAttachmentDraft> = withContext(Dispatchers.IO) {
    uris.mapNotNull { uri -> buildTaskAttachmentDraft(context, uri) }
}

internal suspend fun resolveTaskAttachmentDrafts(
    context: Context,
    drafts: List<TaskAttachmentDraft>
): List<TaskAttachment> = withContext(Dispatchers.IO) {
    normalizeTaskAttachmentDrafts(drafts).mapNotNull { draft ->
        resolveTaskAttachmentDraft(context, draft)
    }
}

private fun buildTaskAttachmentDraft(
    context: Context,
    uri: Uri
): TaskAttachmentDraft? {
    val metadata = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (!cursor.moveToFirst()) {
            null
        } else {
            val displayName = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)
            val sizeBytes = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            displayName to sizeBytes
        }
    } ?: return null
    val mimeType = context.contentResolver.getType(uri)
    val displayName = metadata.first?.ifBlank { null } ?: fallbackDisplayName(uri)
    val kind = if (mimeType?.startsWith("image/") == true) {
        TaskAttachmentKind.IMAGE
    } else {
        TaskAttachmentKind.FILE
    }
    return TaskAttachmentDraft(
        id = UUID.randomUUID().toString(),
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = metadata.second,
        kind = kind,
        storageMode = TaskAttachmentStorageMode.LINKED,
        sourceUri = uri.toString(),
        importedPath = null,
        persistedUriPermission = false,
        isFeaturedImage = false,
        modeLocked = false
    )
}

private fun resolveTaskAttachmentDraft(
    context: Context,
    draft: TaskAttachmentDraft
): TaskAttachment? {
    val displayName = draft.displayName.trim().ifBlank { return null }
    return when (draft.storageMode) {
        TaskAttachmentStorageMode.LINKED -> {
            val sourceUri = draft.sourceUri ?: return null
            TaskAttachment(
                id = draft.id,
                displayName = displayName,
                mimeType = draft.mimeType,
                sizeBytes = draft.sizeBytes,
                kind = draft.kind,
                storageMode = TaskAttachmentStorageMode.LINKED,
                reference = sourceUri,
                persistedUriPermission = takeLinkedAttachmentPermission(context, sourceUri, draft.persistedUriPermission),
                isFeaturedImage = draft.kind == TaskAttachmentKind.IMAGE && draft.isFeaturedImage
            )
        }

        TaskAttachmentStorageMode.IMPORTED -> {
            val importedPath = draft.importedPath ?: draft.sourceUri?.let { sourceUri ->
                importAttachmentIntoAppStorage(
                    context = context,
                    sourceUri = sourceUri,
                    displayName = displayName
                )
            } ?: return null
            TaskAttachment(
                id = draft.id,
                displayName = displayName,
                mimeType = draft.mimeType,
                sizeBytes = draft.sizeBytes,
                kind = draft.kind,
                storageMode = TaskAttachmentStorageMode.IMPORTED,
                reference = importedPath,
                persistedUriPermission = false,
                isFeaturedImage = draft.kind == TaskAttachmentKind.IMAGE && draft.isFeaturedImage
            )
        }
    }
}

private fun takeLinkedAttachmentPermission(
    context: Context,
    sourceUri: String,
    alreadyPersisted: Boolean
): Boolean {
    if (alreadyPersisted) return true
    val uri = Uri.parse(sourceUri)
    if (uri.scheme != "content") return false
    return runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        true
    }.getOrDefault(false)
}

private fun importAttachmentIntoAppStorage(
    context: Context,
    sourceUri: String,
    displayName: String
): String? {
    val uri = Uri.parse(sourceUri)
    val sourceStream = context.contentResolver.openInputStream(uri) ?: return null
    val directory = File(context.filesDir, TASK_ATTACHMENT_DIRECTORY).apply { mkdirs() }
    val file = File(directory, buildImportedAttachmentFileName(displayName))
    sourceStream.use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return "$TASK_ATTACHMENT_DIRECTORY/${file.name}"
}

private fun buildImportedAttachmentFileName(displayName: String): String {
    val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        .orEmpty()
    val baseName = displayName.substringBeforeLast('.', displayName)
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
        .trim('_')
        .takeIf { it.isNotBlank() }
        ?: "attachment"
    return "${baseName}_${UUID.randomUUID()}$extension"
}

private fun fallbackDisplayName(uri: Uri): String {
    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "attachment"
}

internal const val TASK_ATTACHMENT_DIRECTORY = "task_attachments"
