package com.chronosflow.feature.tasks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.chronosflow.core.domain.model.TaskAttachment
import com.chronosflow.core.domain.model.TaskAttachmentStorageMode
import java.io.File

internal fun primaryTaskAttachment(attachments: List<TaskAttachment>): TaskAttachment? {
    return attachments.firstOrNull { it.isFeaturedImage } ?: attachments.firstOrNull()
}

internal fun openTaskAttachment(context: Context, attachment: TaskAttachment): Boolean {
    val uri = resolveTaskAttachmentUri(context, attachment) ?: return false
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, attachment.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return runCatching {
        context.startActivity(intent)
        true
    }.recoverCatching {
        if (it is ActivityNotFoundException) {
            false
        } else {
            throw it
        }
    }.getOrDefault(false)
}

internal fun resolveTaskAttachmentUri(context: Context, attachment: TaskAttachment): Uri? {
    return when (attachment.storageMode) {
        TaskAttachmentStorageMode.LINKED -> attachment.reference.takeIf { it.isNotBlank() }?.let(Uri::parse)
        TaskAttachmentStorageMode.IMPORTED -> {
            val file = File(context.filesDir, attachment.reference)
            if (!file.exists()) return null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }
}
