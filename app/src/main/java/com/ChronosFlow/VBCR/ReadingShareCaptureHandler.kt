package com.ChronosFlow.VBCR

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.ChronosFlow.VBCR.core.data.reading.ReadingMetadataFetchWorker
import com.ChronosFlow.VBCR.core.domain.model.CaptureSource
import com.ChronosFlow.VBCR.core.domain.model.InboxItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingMetadataState
import com.ChronosFlow.VBCR.core.domain.model.ReadingUrls
import com.ChronosFlow.VBCR.core.domain.repository.InboxRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReadingListRepository
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.readChronosUiBooleanSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists content shared into ChronosFlow via the "Save to ChronosFlow" share target. A shared
 * link goes straight to the reading list (with background metadata fetch when enabled); any other
 * text lands in the quick-capture inbox for later triage. Called off the main thread.
 */
@Singleton
class ReadingShareCaptureHandler @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val readingListRepository: ReadingListRepository,
    private val inboxRepository: InboxRepository
) {
    suspend fun capture(sharedText: String) {
        val text = sharedText.trim()
        if (text.isBlank()) return
        val url = ReadingUrls.firstUrlIn(text)
        val now = Instant.now()
        if (url != null) {
            // De-dupe: sharing a link already saved just freshens it (no duplicate, no re-fetch).
            val existing = readingListRepository.findByUrl(url)
            if (existing != null) {
                readingListRepository.save(existing.copy(updatedAt = now))
                return
            }
            val domain = ReadingUrls.domainOf(url)
            val autoFetch = appContext.readChronosUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_READING_METADATA_AUTOFETCH, true
            )
            val id = UUID.randomUUID().toString()
            readingListRepository.save(
                ReadingItem(
                    id = id,
                    url = url,
                    title = domain,
                    domain = domain,
                    metadataState = if (autoFetch) ReadingMetadataState.PENDING else ReadingMetadataState.SKIPPED,
                    addedAt = now,
                    updatedAt = now
                )
            )
            if (autoFetch) {
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    ReadingMetadataFetchWorker.uniqueName(id),
                    ExistingWorkPolicy.REPLACE,
                    ReadingMetadataFetchWorker.request(id)
                )
            }
        } else {
            inboxRepository.save(
                InboxItem(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    url = null,
                    source = CaptureSource.SHARE,
                    createdAt = now
                )
            )
        }
    }
}
