package com.ChronosFlow.VBCR.core.data.focus

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Block IDs the user marked missed (Today tab + Focus skip), keyed by plan date.
 * Persisted as `yyyy-MM-dd|blockId` entries so other days stay unaffected.
 */
@Singleton
class ManualMissedBlockRegistry @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(loadPersistedEntries())
    val ids: StateFlow<Set<String>> = _entries.asStateFlow()

    private val _externalSkipEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val externalSkipEvents: SharedFlow<String> = _externalSkipEvents.asSharedFlow()

    private val _missedFromFocusMessage = MutableStateFlow<String?>(null)
    val missedFromFocusMessage: StateFlow<String?> = _missedFromFocusMessage.asStateFlow()

    private var lastSkipEmitBlockId: String? = null
    private var lastSkipEmitAtMs: Long = 0L

    fun markMissed(blockId: String, date: LocalDate = LocalDate.now()) {
        updateEntries { it + entry(date, blockId) }
    }

    /** Marks missed, surfaces feedback, and syncs an in-flight Day Dial tab session. */
    fun markMissedFromFocusScreen(
        blockId: String,
        blockTitle: String? = null,
        date: LocalDate = LocalDate.now()
    ) {
        markMissed(blockId, date)
        val title = blockTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "Focus block"
        _missedFromFocusMessage.value = "$title marked missed on Today"
        emitSkipIfNotDuplicate(blockId)
    }

    fun clearMissedFromFocusMessage() {
        _missedFromFocusMessage.value = null
    }

    fun clearMissed(blockId: String, date: LocalDate = LocalDate.now()) {
        updateEntries { it - entry(date, blockId) }
    }

    fun missedIdsForDate(date: LocalDate, entries: Set<String> = _entries.value): Set<String> =
        entries.mapNotNull { raw ->
            parseEntry(raw)?.takeIf { (entryDate, _) -> entryDate == date }?.second
        }.toSet()

    // @Synchronized serialises concurrent callers on this Singleton's intrinsic lock so the
    // debounce check-then-act on lastSkipEmitBlockId / lastSkipEmitAtMs is race-free (TS-007).
    @Synchronized
    private fun emitSkipIfNotDuplicate(blockId: String) {
        val now = System.currentTimeMillis()
        if (lastSkipEmitBlockId == blockId && now - lastSkipEmitAtMs < SKIP_EMIT_DEBOUNCE_MS) {
            return
        }
        lastSkipEmitBlockId = blockId
        lastSkipEmitAtMs = now
        _externalSkipEvents.tryEmit(blockId)
    }

    private fun updateEntries(transform: (Set<String>) -> Set<String>) {
        _entries.update(transform)
        persistEntries(_entries.value)
    }

    private fun loadPersistedEntries(): Set<String> {
        val raw = prefs.getStringSet(KEY_BLOCK_ENTRIES, emptySet())?.toSet() ?: emptySet()
        return raw.map { value ->
            if (value.contains("|")) value else entry(LocalDate.now(), value)
        }.toSet()
    }

    private fun persistEntries(entries: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCK_ENTRIES, entries.toSet()).apply()
    }

    internal fun entry(date: LocalDate, blockId: String): String = "${date}|$blockId"

    internal fun parseEntry(raw: String): Pair<LocalDate, String>? {
        val separator = raw.indexOf('|')
        if (separator <= 0) return null
        val date = runCatching { LocalDate.parse(raw.substring(0, separator)) }.getOrNull() ?: return null
        val blockId = raw.substring(separator + 1)
        if (blockId.isEmpty()) return null
        return date to blockId
    }

    companion object {
        private const val PREFS_NAME = "chronos_manual_missed_blocks"
        private const val KEY_BLOCK_ENTRIES = "block_entries"
        private const val SKIP_EMIT_DEBOUNCE_MS = 750L
    }
}
