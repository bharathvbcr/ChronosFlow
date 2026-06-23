package com.ChronosFlow.VBCR.feature.daydial.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.ChronosFlow.VBCR.core.data.reading.ReadingMetadataFetchWorker
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.model.ReadingItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingMetadataState
import com.ChronosFlow.VBCR.core.domain.model.ReadingStatus
import com.ChronosFlow.VBCR.core.domain.model.ReadingUrls
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReadingListRepository
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
import com.ChronosFlow.VBCR.core.notifications.ReadingReminderActionReceiver
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.readChronosUiBooleanSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Backs the standalone Reading List page. Observes saved "read later" items, persists adds/edits,
 * enqueues the background metadata fetch (gated by the user's auto-fetch setting), schedules the
 * "remind me to read later" reminder via the shared [AlarmScheduler], and starts a timeboxed focus
 * session for an item via the existing focus service.
 */
@HiltViewModel
class ReadingListViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val readingListRepository: ReadingListRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val items: StateFlow<List<ReadingItem>> =
        readingListRepository.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun metadataAutoFetchEnabled(): Boolean =
        appContext.readChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_READING_METADATA_AUTOFETCH, true)

    /**
     * Save a new reading item from [rawUrl]. The first URL in the text is used; [title] overrides the
     * placeholder domain title. When [remindAt] is set, a reading reminder is scheduled. Metadata is
     * fetched in the background unless the user disabled auto-fetch.
     */
    fun addUrl(rawUrl: String, title: String? = null, remindAt: Instant? = null) {
        val url = ReadingUrls.firstUrlIn(rawUrl) ?: rawUrl.trim()
        if (url.isBlank()) return
        val now = Instant.now()
        val domain = ReadingUrls.domainOf(url)
        val autoFetch = metadataAutoFetchEnabled()
        viewModelScope.launch {
            // De-dupe: re-adding a link already in the list bumps the existing item (back to unread,
            // freshened) and optionally (re)schedules its reminder instead of creating a duplicate.
            val existing = readingListRepository.findByUrl(url)
            if (existing != null) {
                readingListRepository.save(
                    existing.copy(
                        status = if (existing.status == ReadingStatus.ARCHIVED) ReadingStatus.UNREAD else existing.status,
                        reminderAt = remindAt ?: existing.reminderAt,
                        updatedAt = now
                    )
                )
                if (remindAt != null) scheduleReminder(existing.id, existing.title, remindAt)
                return@launch
            }
            val item = ReadingItem(
                id = UUID.randomUUID().toString(),
                url = url,
                title = title?.trim()?.takeIf { it.isNotBlank() } ?: domain,
                domain = domain,
                metadataState = if (autoFetch) ReadingMetadataState.PENDING else ReadingMetadataState.SKIPPED,
                reminderAt = remindAt,
                addedAt = now,
                updatedAt = now
            )
            readingListRepository.save(item)
            if (autoFetch) enqueueMetadata(item.id)
            if (remindAt != null) scheduleReminder(item.id, item.title, remindAt)
        }
    }

    fun setStatus(id: String, status: ReadingStatus) {
        viewModelScope.launch { readingListRepository.updateStatus(id, status) }
    }

    /** Mark an item read and cancel any pending reminder so a finished item never buzzes later. */
    fun markRead(item: ReadingItem) {
        viewModelScope.launch {
            readingListRepository.updateStatus(item.id, ReadingStatus.DONE)
            if (item.reminderAt != null) {
                cancelReminder(item.id)
                readingListRepository.setReminder(item.id, null)
            }
        }
    }

    fun delete(item: ReadingItem) {
        viewModelScope.launch {
            cancelReminder(item.id)
            readingListRepository.delete(item.id)
            // Reclaim the cached favicon so deleting an item also frees its disk.
            item.faviconPath?.let { path ->
                withContext(Dispatchers.IO) { runCatching { java.io.File(path).delete() } }
            }
        }
    }

    /** Re-run metadata fetch on demand (e.g. after a failed fetch), respecting the auto-fetch setting. */
    fun refetchMetadata(item: ReadingItem) {
        if (!metadataAutoFetchEnabled()) return
        viewModelScope.launch {
            readingListRepository.save(item.copy(metadataState = ReadingMetadataState.PENDING, updatedAt = Instant.now()))
            enqueueMetadata(item.id)
        }
    }

    fun remind(item: ReadingItem, at: Instant) {
        viewModelScope.launch { scheduleReminder(item.id, item.title, at) }
    }

    fun clearReminder(item: ReadingItem) {
        viewModelScope.launch {
            cancelReminder(item.id)
            readingListRepository.setReminder(item.id, null)
        }
    }

    /** Mark the item as in-progress (called when the user opens its link). */
    fun markOpened(item: ReadingItem) {
        if (item.status == ReadingStatus.UNREAD) setStatus(item.id, ReadingStatus.READING)
    }

    /** Start a timeboxed focus session sized to the item's estimated read time (default 20 min). */
    fun readWithFocus(item: ReadingItem) {
        val minutes = (item.estimatedReadMinutes ?: DEFAULT_READ_MINUTES).coerceIn(1, MAX_READ_MINUTES)
        val seconds = minutes * 60
        val sessionId = UUID.randomUUID().toString()
        val now = Instant.now()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                focusSessionRepository.saveFocusSession(
                    FocusSessionState.Running(
                        sessionId = sessionId,
                        blockId = null,
                        startedAt = now,
                        plannedEndAt = now.plusSeconds(seconds.toLong())
                    )
                )
            }
            // Explicit FQCN intent avoids a compile-time dependency on :feature:focus.
            val intent = Intent().apply {
                setClassName(appContext.packageName, "com.ChronosFlow.VBCR.feature.focus.FocusService")
                action = FOCUS_ACTION_START
                putExtra(FOCUS_EXTRA_SESSION_ID, sessionId)
                putExtra(FOCUS_EXTRA_TOTAL_SECONDS, seconds)
                putExtra(FOCUS_EXTRA_TIME_LEFT_SECONDS, seconds)
            }
            runCatching { ContextCompat.startForegroundService(appContext, intent) }
            readingListRepository.updateStatus(item.id, ReadingStatus.READING)
        }
    }

    private fun enqueueMetadata(itemId: String) {
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            ReadingMetadataFetchWorker.uniqueName(itemId),
            ExistingWorkPolicy.REPLACE,
            ReadingMetadataFetchWorker.request(itemId)
        )
    }

    private suspend fun scheduleReminder(itemId: String, title: String, at: Instant) {
        val now = Instant.now()
        alarmScheduler.scheduleAlarmRequest(
            AlarmRequest(
                id = ReadingReminderActionReceiver.readingReminderRequestId(itemId),
                type = AlarmRequestType.READING_REMINDER,
                scheduledFor = at,
                title = title,
                message = "Time to read this",
                medicationPlanId = null,
                blockId = null,
                reliability = AlarmReliability.INEXACT,
                deliveryState = AlarmDeliveryState.PENDING,
                createdAt = now,
                updatedAt = now
            )
        )
        readingListRepository.setReminder(itemId, at)
    }

    private fun cancelReminder(itemId: String) {
        alarmScheduler.cancelAlarm(ReadingReminderActionReceiver.readingReminderRequestId(itemId))
    }

    private companion object {
        const val DEFAULT_READ_MINUTES = 20
        const val MAX_READ_MINUTES = 180
        const val FOCUS_ACTION_START = "com.ChronosFlow.VBCR.feature.focus.START"
        const val FOCUS_EXTRA_SESSION_ID = "session_id"
        const val FOCUS_EXTRA_TOTAL_SECONDS = "total_seconds"
        const val FOCUS_EXTRA_TIME_LEFT_SECONDS = "time_left_seconds"
    }
}
