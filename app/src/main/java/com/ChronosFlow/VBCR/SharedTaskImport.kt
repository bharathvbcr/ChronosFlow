package com.ChronosFlow.VBCR

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.ChronosFlow.VBCR.core.notifications.NotificationLaunch
import com.ChronosFlow.VBCR.core.notifications.SHARED_IMPORT_MAX_CHARS
import com.ChronosFlow.VBCR.core.notifications.parseSharedTextLaunch
import com.ChronosFlow.VBCR.core.notifications.sharedTaskLaunchFromLines
import com.ChronosFlow.VBCR.core.notifications.splitSharedTaskLines
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Resolves an inbound share into a Tasks launch, including **file** shares that the pure
 * [parseSharedTextLaunch] cannot read on its own. Text / process-text shares are handled directly;
 * a shared `.txt`/`.ics`/text file (carried as an `EXTRA_STREAM` content URI, single or multiple) is
 * read through [contentResolver] and parsed into task titles for bulk import.
 */
fun parseSharedTaskImport(intent: Intent?, contentResolver: ContentResolver?): NotificationLaunch? {
    if (intent == null) return null
    // Plain text / multiple-text / process-text shares need no file access.
    parseSharedTextLaunch(intent)?.let { return it }
    if (contentResolver == null) return null
    val uris = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.streamUri())
        Intent.ACTION_SEND_MULTIPLE -> intent.streamUris()
        else -> emptyList()
    }
    if (uris.isEmpty()) return null
    val lines = uris.flatMap { uri ->
        readSharedText(contentResolver, uri)?.let { splitSharedTaskLines(it) }.orEmpty()
    }
    return sharedTaskLaunchFromLines(lines)
}

private fun readSharedText(contentResolver: ContentResolver, uri: Uri): String? {
    // Only accept content:// URIs from the system share machinery. file:// and other
    // schemes are rejected to prevent path-traversal / file exfiltration attacks.
    if (uri.scheme != "content") return null
    return runCatching {
        contentResolver.openInputStream(uri)?.use { stream ->
            // Bounded read: only the prefix we'd ever parse, so a huge/binary file can't OOM us.
            String(stream.readBounded(SHARED_IMPORT_MAX_CHARS), Charsets.UTF_8)
        }
    }.getOrNull()
}

/** Reads at most [max] bytes from the stream (minSdk-safe; avoids API 33's readNBytes). */
private fun InputStream.readBounded(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (total < max) {
        val read = read(buffer, 0, minOf(buffer.size, max - total))
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}

@Suppress("DEPRECATION")
private fun Intent.streamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_STREAM)
    }

@Suppress("DEPRECATION")
private fun Intent.streamUris(): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
