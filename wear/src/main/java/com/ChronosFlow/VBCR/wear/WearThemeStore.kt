package com.ChronosFlow.VBCR.wear

import android.content.Context
import com.ChronosFlow.VBCR.core.domain.wear.WearThemeContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watch-local cache of the phone's mirrored Material You palette: ARGB ints in
 * [WearThemeContract] `IDX_*` order, or null when the phone has published none (then the app
 * falls back to the watch's own dynamic scheme or the static brand palette).
 *
 * [ThemeWearListenerService] writes it; the Compose theme collects [state] and the tiles call
 * [read] on every render — same split as [DaySummaryStore].
 */
object WearThemeStore {

    // @Volatile ensures the write from one thread (e.g. ThemeWearListenerService on Dispatchers.IO)
    // is immediately visible to another (e.g. tile renderer). The synchronized block inside
    // ensureFlow() prevents two threads from each constructing a new MutableStateFlow and one
    // silently discarding the other's writes (TS-001).
    @Volatile private var flow: MutableStateFlow<List<Int>?>? = null

    fun state(context: Context): StateFlow<List<Int>?> = ensureFlow(context).asStateFlow()

    fun read(context: Context): List<Int>? = ensureFlow(context).value

    fun write(context: Context, palette: List<Int>) {
        if (palette.size != WearThemeContract.PALETTE_SIZE) return
        prefs(context).edit()
            .putString(KEY_PALETTE, palette.joinToString(SEP))
            .apply()
        ensureFlow(context).value = palette
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        ensureFlow(context).value = null
    }

    private fun ensureFlow(context: Context): MutableStateFlow<List<Int>?> =
        flow ?: synchronized(this) {
            flow ?: MutableStateFlow(load(context)).also { flow = it }
        }

    private fun load(context: Context): List<Int>? =
        prefs(context).getString(KEY_PALETTE, null)
            ?.split(SEP)
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == WearThemeContract.PALETTE_SIZE }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "chronos_phone_theme"
    private const val KEY_PALETTE = "palette"
    private const val SEP = ","
}
