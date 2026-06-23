package com.ChronosFlow.VBCR.core.data.reading

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ChronosFlow.VBCR.core.domain.model.ReadingMetadataState
import com.ChronosFlow.VBCR.core.domain.repository.ReadingListRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/**
 * One-shot background fetch of reading-list metadata: page title, favicon, and an estimated read
 * time. Network-bounded (timeouts + a body-size cap) and best-effort — a failure marks the item
 * [ReadingMetadataState.FAILED] but never blocks the user. Enqueued from the reading-list save path
 * only when the user's "auto-fetch metadata" setting is on (gated at the call site).
 */
class ReadingMetadataFetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            ReadingMetadataEntryPoint::class.java
        ).readingListRepository()

        val item = repository.getById(itemId) ?: return Result.success()

        return withContext(Dispatchers.IO) {
            try {
                val html = fetch(item.url) ?: run {
                    repository.save(item.copy(metadataState = ReadingMetadataState.FAILED, updatedAt = Instant.now()))
                    return@withContext Result.success()
                }
                val metadata = ReadingMetadataParser.parse(html, item.url)
                val faviconPath = metadata.faviconUrl?.let { downloadFavicon(it, item.id) }
                // Don't clobber a title the user typed: only fill it when ours still equals the domain.
                val title = if (item.title.isBlank() || item.title == item.domain) {
                    metadata.title ?: item.title
                } else {
                    item.title
                }
                repository.save(
                    item.copy(
                        title = title,
                        faviconPath = faviconPath ?: item.faviconPath,
                        estimatedReadMinutes = metadata.estimatedReadMinutes ?: item.estimatedReadMinutes,
                        wordCount = metadata.wordCount ?: item.wordCount,
                        metadataState = ReadingMetadataState.FETCHED,
                        updatedAt = Instant.now()
                    )
                )
                Result.success()
            } catch (e: IOException) {
                if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    repository.save(item.copy(metadataState = ReadingMetadataState.FAILED, updatedAt = Instant.now()))
                    Result.success(workDataOf(KEY_ERROR to e.message.orEmpty().take(MAX_ERROR_LENGTH)))
                }
            }
        }
    }

    private fun fetch(url: String): String? {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            try {
                when (val code = connection.responseCode) {
                    in 200..299 -> return connection.inputStream.use { readCapped(it) }
                    in 300..399 -> {
                        current = connection.getHeaderField("Location") ?: return null
                        if (!current.startsWith("http")) return null
                    }
                    else -> {
                        // Treat non-OK as a soft failure (not retryable): there's nothing to fetch.
                        if (code >= 500) throw IOException("HTTP $code")
                        return null
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun readCapped(input: java.io.InputStream): String {
        val buffer = ByteArray(8 * 1024)
        val out = StringBuilder()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            out.append(String(buffer, 0, read, Charsets.UTF_8))
            if (total >= MAX_BODY_BYTES) break
        }
        return out.toString()
    }

    private fun downloadFavicon(faviconUrl: String, itemId: String): String? {
        return try {
            val connection = (URL(faviconUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (connection.responseCode !in 200..299) return null
                val dir = File(applicationContext.filesDir, FAVICON_DIR).apply { mkdirs() }
                // Replace any prior favicon for this item.
                dir.listFiles()?.filter { it.name.startsWith(itemId) }?.forEach { it.delete() }
                val file = File(dir, "$itemId-${UUID.randomUUID()}.img")
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        var total = 0
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_FAVICON_BYTES) { file.delete(); return null }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                file.absolutePath
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            null
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReadingMetadataEntryPoint {
        fun readingListRepository(): ReadingListRepository
    }

    companion object {
        const val FAVICON_DIR = "reading_favicons"
        private const val KEY_ITEM_ID = "itemId"
        private const val KEY_ERROR = "error"
        private const val TIMEOUT_MS = 12_000
        private const val MAX_BODY_BYTES = 512 * 1024
        private const val MAX_FAVICON_BYTES = 256 * 1024
        private const val MAX_REDIRECTS = 3
        private const val MAX_ATTEMPTS = 3
        private const val MAX_ERROR_LENGTH = 500
        private const val USER_AGENT = "ChronosFlow/1.0 (+reading-list)"

        fun uniqueName(itemId: String): String = "reading_metadata_$itemId"

        fun request(itemId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ReadingMetadataFetchWorker>()
                .setInputData(workDataOf(KEY_ITEM_ID to itemId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
    }
}
