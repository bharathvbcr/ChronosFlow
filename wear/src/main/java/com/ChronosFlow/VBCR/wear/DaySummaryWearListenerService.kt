package com.ChronosFlow.VBCR.wear

import androidx.wear.tiles.TileService
import com.ChronosFlow.VBCR.core.domain.wear.WearDaySummaryContract
import com.ChronosFlow.VBCR.wear.model.WearDaySummary
import com.ChronosFlow.VBCR.wear.model.parseBlocks
import com.ChronosFlow.VBCR.wear.model.parseHabits
import com.ChronosFlow.VBCR.wear.model.parseMeds
import com.ChronosFlow.VBCR.wear.model.parseTasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mirrors the phone's day summary (schedule, tasks, habits, medications) into [DaySummaryStore]
 * and nudges the Now and Habits tiles to re-render. Companion to [FocusWearListenerService],
 * which handles the live focus-session ongoing activity.
 */
class DaySummaryWearListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class DaySummarySnapshot(val type: Int, val map: DataMap?)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Snapshot all DataMap values synchronously before the buffer cursor is invalidated
        // when onDataChanged() returns. Any DataMap read after that point is undefined behaviour.
        val snapshots = dataEvents
            .filter { it.dataItem.uri.path == WearDaySummaryContract.DAY_SUMMARY_PATH }
            .map { event ->
                val map = if (event.type == DataEvent.TYPE_CHANGED) {
                    DataMapItem.fromDataItem(event.dataItem).dataMap
                } else null
                DaySummarySnapshot(event.type, map)
            }
            .toList()

        scope.launch {
            var changed = false
            for (snapshot in snapshots) {
                when (snapshot.type) {
                    DataEvent.TYPE_DELETED -> {
                        DaySummaryStore.clear(this@DaySummaryWearListenerService)
                        changed = true
                    }
                    DataEvent.TYPE_CHANGED -> {
                        val map = snapshot.map ?: continue
                        DaySummaryStore.writeSynced(
                            this@DaySummaryWearListenerService,
                            WearDaySummary(
                                nowTitle = map.getString(WearDaySummaryContract.KEY_NOW_TITLE),
                                nowEndMinute = map.getInt(WearDaySummaryContract.KEY_NOW_END_MINUTE, 0),
                                nowBlockId = map.getString(WearDaySummaryContract.KEY_NOW_BLOCK_ID)
                                    ?.takeIf { it.isNotBlank() },
                                nowCategory = map.getString(WearDaySummaryContract.KEY_NOW_CATEGORY).orEmpty(),
                                blocks = parseBlocks(
                                    map.getStringArray(WearDaySummaryContract.KEY_BLOCK_ENTRIES)?.toList().orEmpty()
                                ),
                                nextTitle = map.getString(WearDaySummaryContract.KEY_NEXT_TITLE),
                                nextStartMinute = map.getInt(WearDaySummaryContract.KEY_NEXT_START_MINUTE, 0),
                                nextBreakStartMinute = map.getInt(WearDaySummaryContract.KEY_NEXT_BREAK_START_MINUTE, 0),
                                nextBreakTitle = map.getString(WearDaySummaryContract.KEY_NEXT_BREAK_TITLE)
                                    ?.takeIf { it.isNotBlank() },
                                openTaskCount = map.getInt(WearDaySummaryContract.KEY_OPEN_TASK_COUNT, 0),
                                tasks = parseTasks(
                                    map.getStringArray(WearDaySummaryContract.KEY_TASK_ENTRIES)?.toList().orEmpty()
                                ),
                                habitsDone = map.getInt(WearDaySummaryContract.KEY_HABITS_DONE, 0),
                                habitsTotal = map.getInt(WearDaySummaryContract.KEY_HABITS_TOTAL, 0),
                                habits = parseHabits(
                                    map.getStringArray(WearDaySummaryContract.KEY_HABIT_ENTRIES)?.toList().orEmpty()
                                ),
                                medsDueCount = map.getInt(WearDaySummaryContract.KEY_MEDS_DUE_COUNT, 0),
                                meds = parseMeds(
                                    map.getStringArray(WearDaySummaryContract.KEY_MED_ENTRIES)?.toList().orEmpty()
                                ),
                                digest = map.getString(WearDaySummaryContract.KEY_DIGEST)
                                    ?.takeIf { it.isNotBlank() }
                            )
                        )
                        changed = true
                    }
                }
            }
            if (changed) {
                requestTileUpdates()
                requestChronosComplicationUpdates(this@DaySummaryWearListenerService)
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
