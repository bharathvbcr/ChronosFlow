package com.ChronosFlow.VBCR.wear

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watch-local cache of the mirrored focus session so the focus tile can render real state.
 * [FocusWearListenerService] writes it alongside the ongoing activity; the tile reads it on
 * every render and computes the live countdown locally from the planned end time, and the
 * Compose app collects [state] for the live Focus screen.
 */
object WearFocusStateStore {

    data class FocusState(
        val active: Boolean = false,
        val paused: Boolean = false,
        val title: String? = null,
        /** Wall-clock epoch millis when the running timer reaches zero. */
        val plannedEndAtMillis: Long = 0L,
        /** Frozen seconds remaining; only meaningful while [paused] is true. */
        val pausedTimeLeftSeconds: Int = 0,
        val totalSeconds: Int = 0
    )

    private var flow: MutableStateFlow<FocusState>? = null

    fun state(context: Context): StateFlow<FocusState> = ensureFlow(context).asStateFlow()

    fun write(context: Context, state: FocusState) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, state.active)
            .putBoolean(KEY_PAUSED, state.paused)
            .putString(KEY_TITLE, state.title)
            .putLong(KEY_PLANNED_END_AT_MILLIS, state.plannedEndAtMillis)
            .putInt(KEY_PAUSED_TIME_LEFT_SECONDS, state.pausedTimeLeftSeconds)
            .putInt(KEY_TOTAL_SECONDS, state.totalSeconds)
            .apply()
        ensureFlow(context).value = state
    }

    fun read(context: Context): FocusState = ensureFlow(context).value

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        ensureFlow(context).value = FocusState()
    }

    private fun ensureFlow(context: Context): MutableStateFlow<FocusState> =
        flow ?: MutableStateFlow(load(context)).also { flow = it }

    private fun load(context: Context): FocusState {
        val prefs = prefs(context)
        return FocusState(
            active = prefs.getBoolean(KEY_ACTIVE, false),
            paused = prefs.getBoolean(KEY_PAUSED, false),
            title = prefs.getString(KEY_TITLE, null)?.takeIf { it.isNotBlank() },
            plannedEndAtMillis = prefs.getLong(KEY_PLANNED_END_AT_MILLIS, 0L),
            pausedTimeLeftSeconds = prefs.getInt(KEY_PAUSED_TIME_LEFT_SECONDS, 0),
            totalSeconds = prefs.getInt(KEY_TOTAL_SECONDS, 0)
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "chronos_focus_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PAUSED = "paused"
    private const val KEY_TITLE = "title"
    private const val KEY_PLANNED_END_AT_MILLIS = "planned_end_at_millis"
    private const val KEY_PAUSED_TIME_LEFT_SECONDS = "paused_time_left_seconds"
    private const val KEY_TOTAL_SECONDS = "total_seconds"
}
