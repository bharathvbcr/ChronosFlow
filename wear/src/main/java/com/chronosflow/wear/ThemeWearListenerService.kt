package com.chronosflow.wear

import androidx.wear.tiles.TileService
import com.chronosflow.core.domain.wear.WearThemeContract
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Mirrors the phone's Material You palette into [WearThemeStore] and nudges the tiles to
 * re-render with the new accent. Companion to [DaySummaryWearListenerService]; a deleted item
 * means the phone has no dynamic palette, so the watch reverts to its own theming.
 */
class ThemeWearListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var changed = false
        for (event in dataEvents) {
            if (event.dataItem.uri.path != WearThemeContract.THEME_PATH) continue
            when (event.type) {
                DataEvent.TYPE_DELETED -> {
                    WearThemeStore.clear(this)
                    changed = true
                }
                DataEvent.TYPE_CHANGED -> {
                    val palette = DataMapItem.fromDataItem(event.dataItem).dataMap
                        .getLongArray(WearThemeContract.KEY_PALETTE)
                        ?.map { it.toInt() }
                    if (palette != null && palette.size == WearThemeContract.PALETTE_SIZE) {
                        WearThemeStore.write(this, palette)
                        changed = true
                    }
                }
            }
        }
        if (changed) {
            requestTileUpdates()
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
