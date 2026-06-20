package com.ChronosFlow.VBCR.core.data.sync

import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the wall-clock time of the last successful device-calendar sync so the UI can show a
 * "Synced X ago" hint. Written by every real sync path (manual, per-date gate, foreground refresh,
 * periodic background worker), so it reflects freshness regardless of how the pull was triggered.
 *
 * Backed by a [MutableStateFlow] (seeded from persisted prefs at construction) so observers update
 * live across all in-process sync paths, including the background worker.
 */
@Singleton
class CalendarSyncStatusStore @Inject constructor(
    private val preferences: ChronosPreferencesDataSource
) {
    // Overridable so tests can pin the recorded timestamp.
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val lastSyncAtMillis = MutableStateFlow(
        preferences.getLong(KEY_LAST_SYNC_AT, NEVER_SYNCED).takeIf { it != NEVER_SYNCED }
    )

    private val _lastFailureMessage = MutableStateFlow<String?>(
        preferences.getString(KEY_LAST_FAILURE_MSG, "").takeIf { it.isNotEmpty() }
    )

    fun recordSuccessfulSync() {
        val now = clock()
        preferences.putLong(KEY_LAST_SYNC_AT, now)
        lastSyncAtMillis.value = now
        // Clear any previous failure state so the UI shows "Synced X ago" rather than the old error.
        preferences.remove(KEY_LAST_FAILURE_MSG)
        _lastFailureMessage.value = null
    }

    /**
     * Record a calendar sync failure so the UI can distinguish a stale "Synced X ago" hint from
     * an active "Last sync failed" state. The last-success timestamp is intentionally left
     * unchanged so the user can still see when the calendar data was last valid.
     */
    fun recordFailure(message: String) {
        preferences.putString(KEY_LAST_FAILURE_MSG, message)
        _lastFailureMessage.value = message
    }

    /** Epoch millis of the last successful sync, or null if the calendar has never been synced. */
    fun lastSuccessfulSyncAtMillis(): Long? = lastSyncAtMillis.value

    /** Live stream of [lastSuccessfulSyncAtMillis], emitting whenever a sync is recorded. */
    fun observeLastSuccessfulSyncAtMillis(): StateFlow<Long?> = lastSyncAtMillis.asStateFlow()

    /**
     * Human-readable failure message from the most recent failed sync, or null when the last
     * sync succeeded (or the calendar has never been synced). Use this to show "Last sync
     * failed 2h ago" in place of a stale "Synced X ago" hint.
     */
    fun lastFailureMessage(): String? = _lastFailureMessage.value

    /** Live stream of [lastFailureMessage], emitting whenever a failure or success is recorded. */
    fun observeLastFailureMessage(): StateFlow<String?> = _lastFailureMessage.asStateFlow()

    private companion object {
        const val KEY_LAST_SYNC_AT = "calendar_sync.last_success_at_millis"
        const val KEY_LAST_FAILURE_MSG = "calendar_sync.last_failure_message"
        const val NEVER_SYNCED = -1L
    }
}
