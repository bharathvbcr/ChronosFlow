package com.ChronosFlow.VBCR.feature.daydial.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.ChronosFlow.VBCR.core.data.reading.ReadingMetadataFetchWorker
import com.ChronosFlow.VBCR.core.domain.model.CaptureSource
import com.ChronosFlow.VBCR.core.domain.model.InboxItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingMetadataState
import com.ChronosFlow.VBCR.core.domain.model.ReadingUrls
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TriageOutcome
import com.ChronosFlow.VBCR.core.domain.repository.InboxRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReadingListRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.readChronosUiBooleanSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Backs the quick-capture Inbox page. Captures any thought/link, then triages each item into a
 * reading-list entry, a task, or discards it. Reuses the same metadata-fetch path the reading list
 * uses when an item is saved as a reading link.
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val inboxRepository: InboxRepository,
    private val readingListRepository: ReadingListRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val items: StateFlow<List<InboxItem>> =
        inboxRepository.observeUntriaged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun capture(text: String, source: CaptureSource = CaptureSource.MANUAL) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val now = Instant.now()
        viewModelScope.launch {
            inboxRepository.save(
                InboxItem(
                    id = UUID.randomUUID().toString(),
                    text = trimmed,
                    url = ReadingUrls.firstUrlIn(trimmed),
                    source = source,
                    createdAt = now
                )
            )
        }
    }

    /** Capture [text] straight into the reading list (used when the captured content is a link). */
    fun captureAsReading(text: String, source: CaptureSource = CaptureSource.MANUAL) {
        val url = ReadingUrls.firstUrlIn(text) ?: return capture(text, source)
        val now = Instant.now()
        val domain = ReadingUrls.domainOf(url)
        val autoFetch = appContext.readChronosUiBooleanSetting(
            ChronosUiSettingsKeys.KEY_READING_METADATA_AUTOFETCH, true
        )
        val readingId = UUID.randomUUID().toString()
        viewModelScope.launch {
            readingListRepository.save(
                ReadingItem(
                    id = readingId,
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
                    ReadingMetadataFetchWorker.uniqueName(readingId),
                    ExistingWorkPolicy.REPLACE,
                    ReadingMetadataFetchWorker.request(readingId)
                )
            }
        }
    }

    fun triageToReading(item: InboxItem) {
        val url = item.url ?: ReadingUrls.firstUrlIn(item.text) ?: return
        val now = Instant.now()
        val domain = ReadingUrls.domainOf(url)
        val autoFetch = appContext.readChronosUiBooleanSetting(
            ChronosUiSettingsKeys.KEY_READING_METADATA_AUTOFETCH, true
        )
        val readingId = UUID.randomUUID().toString()
        viewModelScope.launch {
            readingListRepository.save(
                ReadingItem(
                    id = readingId,
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
                    ReadingMetadataFetchWorker.uniqueName(readingId),
                    ExistingWorkPolicy.REPLACE,
                    ReadingMetadataFetchWorker.request(readingId)
                )
            }
            inboxRepository.markTriaged(item.id, TriageOutcome.READING, readingId)
        }
    }

    fun triageToTask(item: InboxItem) {
        val now = Instant.now()
        val taskId = UUID.randomUUID().toString()
        viewModelScope.launch {
            taskRepository.saveTask(
                Task(
                    id = taskId,
                    title = item.text.take(TASK_TITLE_MAX),
                    description = null,
                    isCompleted = false,
                    priority = DEFAULT_PRIORITY,
                    dueDate = null,
                    createdAt = now,
                    updatedAt = now,
                    origin = "inbox"
                )
            )
            inboxRepository.markTriaged(item.id, TriageOutcome.TASK, taskId)
        }
    }

    fun discard(item: InboxItem) {
        viewModelScope.launch { inboxRepository.delete(item.id) }
    }

    private companion object {
        const val TASK_TITLE_MAX = 200
        const val DEFAULT_PRIORITY = 2
    }
}
