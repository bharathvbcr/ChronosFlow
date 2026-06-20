package com.ChronosFlow.VBCR.wear

import androidx.wear.tiles.TileService
import com.ChronosFlow.VBCR.core.domain.wear.WearThemeContract
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mirrors the phone's Material You palette into [WearThemeStore] and nudges the tiles to
 * re-render with the new accent. Companion to [DaySummaryWearListenerService]; a deleted item
 * means the phone has no dynamic palette, so the watch reverts to its own theming.
 */
class ThemeWearListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private sealed class ThemeEventSnapshot {
        object Deleted : ThemeEventSnapshot()
        data class Changed(val palette: List<Int>?) : ThemeEventSnapshot()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Snapshot all DataMap values synchronously before the buffer cursor is invalidated
        // when onDataChanged() returns. Any DataMap read after that point is undefined behaviour.
        val snapshots = dataEvents
            .filter { it.dataItem.uri.path == WearThemeContract.THEME_PATH }
            .map { event ->
                when (event.type) {
                    DataEvent.TYPE_DELETED -> ThemeEventSnapshot.Deleted
                    else -> {
                        val palette = DataMapItem.fromDataItem(event.dataItem).dataMap
                            .getLongArray(WearThemeContract.KEY_PALETTE)
                            ?.map { it.toInt() }
                        ThemeEventSnapshot.Changed(palette)
                    }
                }
            }
            .toList()

        scope.launch {
            var changed = false
            for (snapshot in snapshots) {
                when (snapshot) {
                    is ThemeEventSnapshot.Deleted -> {
                        WearThemeStore.clear(this@ThemeWearListenerService)
                        changed = true
                    }
                    is ThemeEventSnapshot.Changed -> {
                        val palette = snapshot.palette
                        if (palette != null && palette.size == WearThemeContract.PALETTE_SIZE) {
                            WearThemeStore.write(this@ThemeWearListenerService, palette)
                            changed = true
                        }
                    }
                }
            }
            if (changed) {
                requestTileUpdates()
            }
        }
    }

    private fun requestTileUpdates() {
        runCatching {
            val updater = TileService.getUpdater(this)
            updater.requestUpdate(ChronosTodayTileProvider::class.java)
            updater.requestUpdate(ChronosHabitsTileProvider::class.java)
        }
    }
}
