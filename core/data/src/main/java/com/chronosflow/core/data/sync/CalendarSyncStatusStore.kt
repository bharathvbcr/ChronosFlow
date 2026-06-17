package com.chronosflow.core.data.sync

import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
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

    fun recordSuccessfulSync() {
        val now = clock()
        preferences.putLong(KEY_LAST_SYNC_AT, now)
        lastSyncAtMillis.value = now
    }

    /** Epoch millis of the last successful sync, or null if the calendar has never been synced. */
    fun lastSuccessfulSyncAtMillis(): Long? = lastSyncAtMillis.value

    /** Live stream of [lastSuccessfulSyncAtMillis], emitting whenever a sync is recorded. */
    fun observeLastSuccessfulSyncAtMillis(): StateFlow<Long?> = lastSyncAtMillis.asStateFlow()

    private companion object {
        const val KEY_LAST_SYNC_AT = "calendar_sync.last_success_at_millis"
        const val NEVER_SYNCED = -1L
    }
}
