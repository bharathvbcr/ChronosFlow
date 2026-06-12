package com.chronosflow.feature.daydial.delegate

import android.content.Context
import com.chronosflow.feature.daydial.FocusExecutionState
import com.chronosflow.feature.daydial.FocusExecutionStatus
import com.chronosflow.feature.daydial.model.FocusPhase
import com.chronosflow.feature.daydial.model.FocusPhaseKind
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Persists the in-app split (Pomodoro) session so a process kill mid-session can
 * resume the full phase plan instead of collapsing to a single flat block.
 *
 * Splits are owned by the in-app layer (the [FocusExecutionState]), so they are
 * persisted here rather than in the domain/Room session — see chronosflow-focus-splits.
 */
interface FocusSplitSessionStore {
    fun save(state: FocusExecutionState)
    fun load(): FocusExecutionState?
    fun clear()

    /** No-op store used as the default for tests that don't exercise persistence. */
    object None : FocusSplitSessionStore {
        override fun save(state: FocusExecutionState) = Unit
        override fun load(): FocusExecutionState? = null
        override fun clear() = Unit
    }
}

internal class SharedPreferencesFocusSplitSessionStore(
    context: Context
) : FocusSplitSessionStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(state: FocusExecutionState) {
        prefs.edit()
            .putString(KEY_PHASES, encodePhases(state.phases))
            .putInt(KEY_INDEX, state.currentPhaseIndex)
            .putString(KEY_BLOCK_ID, state.blockId)
            .putString(KEY_BLOCK_TITLE, state.blockTitle)
            .putInt(KEY_BLOCK_START, state.blockStartMinute)
            .putInt(KEY_PLANNED, state.plannedDurationMinutes)
            .putLong(KEY_STARTED, state.startedEpochMs)
            .putLong(KEY_PAUSED_AT, state.pausedAtEpochMs ?: NO_PAUSE)
            .putLong(KEY_TOTAL_PAUSED, state.totalPausedMs)
            .putString(KEY_STATUS, state.status.name)
            .putBoolean(KEY_AWAITING, state.awaitingPhaseAdvance)
            .apply()
    }

    override fun load(): FocusExecutionState? {
        val phases = prefs.getString(KEY_PHASES, null)?.let(::decodePhases).orEmpty()
        if (phases.size <= 1) return null
        val status = prefs.getString(KEY_STATUS, null)
            ?.let { name -> runCatching { FocusExecutionStatus.valueOf(name) }.getOrNull() }
            ?: return null
        if (status != FocusExecutionStatus.RUNNING && status != FocusExecutionStatus.PAUSED) {
            return null
        }
        val pausedAt = prefs.getLong(KEY_PAUSED_AT, NO_PAUSE).takeIf { it >= 0L }
        return FocusExecutionState(
            blockId = prefs.getString(KEY_BLOCK_ID, null),
            blockTitle = prefs.getString(KEY_BLOCK_TITLE, "").orEmpty(),
            blockStartMinute = prefs.getInt(KEY_BLOCK_START, 0),
            plannedDurationMinutes = prefs.getInt(KEY_PLANNED, 0),
            startedEpochMs = prefs.getLong(KEY_STARTED, 0L),
            status = status,
            pausedAtEpochMs = pausedAt,
            totalPausedMs = prefs.getLong(KEY_TOTAL_PAUSED, 0L),
            phases = phases,
            currentPhaseIndex = prefs.getInt(KEY_INDEX, 0).coerceIn(0, phases.lastIndex),
            awaitingPhaseAdvance = prefs.getBoolean(KEY_AWAITING, false)
        )
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "chronos_focus_split_session"
        const val KEY_PHASES = "phases"
        const val KEY_INDEX = "current_phase_index"
        const val KEY_BLOCK_ID = "block_id"
        const val KEY_BLOCK_TITLE = "block_title"
        const val KEY_BLOCK_START = "block_start_minute"
        const val KEY_PLANNED = "planned_minutes"
        const val KEY_STARTED = "started_epoch_ms"
        const val KEY_PAUSED_AT = "paused_at_epoch_ms"
        const val KEY_TOTAL_PAUSED = "total_paused_ms"
        const val KEY_STATUS = "status"
        const val KEY_AWAITING = "awaiting_phase_advance"
        const val NO_PAUSE = -1L
    }
}

/** "F25,B5,F25,B5" — kind initial + minutes, comma separated. */
internal fun encodePhases(phases: List<FocusPhase>): String =
    phases.joinToString(",") { phase ->
        val kind = if (phase.kind == FocusPhaseKind.BREAK) "B" else "F"
        "$kind${phase.durationMinutes}"
    }

internal fun decodePhases(encoded: String): List<FocusPhase> =
    encoded.split(",")
        .mapNotNull { token ->
            val trimmed = token.trim()
            if (trimmed.length < 2) return@mapNotNull null
            val kind = when (trimmed.first()) {
                'B' -> FocusPhaseKind.BREAK
                'F' -> FocusPhaseKind.FOCUS
                else -> return@mapNotNull null
            }
            val minutes = trimmed.drop(1).toIntOrNull() ?: return@mapNotNull null
            FocusPhase(kind, minutes)
        }

@Module
@InstallIn(SingletonComponent::class)
object FocusSplitSessionStoreModule {
    @Provides
    @Singleton
    fun provideFocusSplitSessionStore(
        @ApplicationContext context: Context
    ): FocusSplitSessionStore = SharedPreferencesFocusSplitSessionStore(context)
}
