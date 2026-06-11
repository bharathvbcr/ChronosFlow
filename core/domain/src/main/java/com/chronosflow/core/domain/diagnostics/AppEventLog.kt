package com.chronosflow.core.domain.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Coarse grouping for a recorded app event, used to tag and colour log rows. */
enum class AppEventCategory { SESSION, SYNC, FOCUS, EXPORT, ERROR }

/** A single recorded app event. [timestampMillis] is wall-clock epoch millis. */
data class AppEventLogEntry(
    val timestampMillis: Long,
    val category: AppEventCategory,
    val message: String
)

/**
 * In-memory ring buffer of recent app events surfaced by the developer "View Logs"
 * sheet. Process-scoped only — entries are not persisted and reset on app restart.
 * Newest entries are kept at the head so callers can render the feed directly.
 */
@Singleton
class AppEventLog @Inject constructor() {

    /** Overridable in tests so recorded timestamps are deterministic. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val _entries = MutableStateFlow<List<AppEventLogEntry>>(emptyList())
    val entries: StateFlow<List<AppEventLogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun record(category: AppEventCategory, message: String) {
        val entry = AppEventLogEntry(clock(), category, message)
        _entries.value = (listOf(entry) + _entries.value).take(CAPACITY)
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }

    private companion object {
        const val CAPACITY = 100
    }
}
