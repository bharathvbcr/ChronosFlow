package com.chronosflow.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import androidx.wear.tiles.TileService
import com.chronosflow.core.domain.wear.WearFocusContract
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Mirrors the phone's active focus session onto the watch as an [OngoingActivity] live update.
 *
 * The phone publishes a single Data Layer item at [WearFocusContract.FOCUS_PATH]; this service
 * reacts to changes by showing/refreshing a countdown ongoing activity and removes it when the
 * item is deleted or the session is no longer active. The same state is cached in
 * [WearFocusStateStore] so [ChronosTodayTileProvider] can render the focus ring from real data.
 */
class FocusWearListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.dataItem.uri.path != WearFocusContract.FOCUS_PATH) continue
            when (event.type) {
                DataEvent.TYPE_DELETED -> clearFocusState()
                DataEvent.TYPE_CHANGED -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    if (!map.getBoolean(WearFocusContract.KEY_ACTIVE, false)) {
                        clearFocusState()
                    } else {
                        val paused = map.getBoolean(WearFocusContract.KEY_PAUSED, false)
                        val title = map.getString(WearFocusContract.KEY_TITLE)
                            ?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE
                        val plannedEndAtMillis = map.getLong(WearFocusContract.KEY_PLANNED_END_AT_MILLIS, 0L)
                        val pausedTimeLeftSeconds = map.getInt(WearFocusContract.KEY_PAUSED_TIME_LEFT_SECONDS, 0)
                        showFocusOngoingActivity(
                            paused = paused,
                            title = title,
                            plannedEndAtMillis = plannedEndAtMillis,
                            pausedTimeLeftSeconds = pausedTimeLeftSeconds
                        )
                        WearFocusStateStore.write(
                            this,
                            WearFocusStateStore.FocusState(
                                active = true,
                                paused = paused,
                                title = title,
                                plannedEndAtMillis = plannedEndAtMillis,
                                pausedTimeLeftSeconds = pausedTimeLeftSeconds,
                                totalSeconds = map.getInt(WearFocusContract.KEY_TOTAL_SECONDS, 0)
                            )
                        )
                        requestFocusTileUpdate()
                    }
                }
            }
        }
    }

    private fun clearFocusState() {
        cancelFocusOngoingActivity()
        WearFocusStateStore.clear(this)
        requestFocusTileUpdate()
    }

    private fun requestFocusTileUpdate() {
        runCatching {
            TileService.getUpdater(this).requestUpdate(ChronosTodayTileProvider::class.java)
        }
        requestChronosComplicationUpdates(this)
    }

    private fun showFocusOngoingActivity(
        paused: Boolean,
        title: String,
        plannedEndAtMillis: Long,
        pausedTimeLeftSeconds: Int
    ) {
        ensureChannel()

        val touchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Convert the phone's wall-clock end time into this device's elapsed-realtime base.
        val runningRemainingMs = (plannedEndAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val remainingSeconds = if (paused) {
            pausedTimeLeftSeconds.coerceAtLeast(0)
        } else {
            (runningRemainingMs / 1000L).toInt()
        }
        val stateColor = focusOngoingActivityColor(paused, remainingSeconds, ChronosTileUi.accent(this))

        val status = if (paused) {
            Status.Builder()
                .addTemplate("Paused #left#")
                .addPart("left", Status.TextPart(formatMmSs(pausedTimeLeftSeconds)))
                .build()
        } else {
            Status.Builder()
                .addTemplate("#focusTimer#")
                .addPart("focusTimer", Status.TimerPart(SystemClock.elapsedRealtime() + runningRemainingMs))
                .build()
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chronosflow_notification)
            .setContentTitle(title)
            .setContentText(if (paused) "Focus paused" else "Focus in progress")
            // Tint the ongoing activity with the phone's mirrored Material You accent (brand-teal
            // fallback), shifting to muted/warm for paused/ending-soon — parity with the phone bar.
            .setColor(stateColor)
            .setColorized(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(touchIntent)

        val ongoingActivity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_chronosflow_notification)
            .setTouchIntent(touchIntent)
            .setStatus(status)
            .build()
        ongoingActivity.apply(applicationContext)

        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun cancelFocusOngoingActivity() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Focus session",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Live focus-session timer mirrored from your phone." }
        )
    }

    private fun formatMmSs(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    private companion object {
        const val CHANNEL_ID = "chronos_focus_wear"
        const val NOTIFICATION_ID = 4201
        const val DEFAULT_TITLE = "Focus session"
    }
}

/** Final stretch where the live update turns warm; mirrors the phone's FOCUS_BAR_ENDING_SOON_THRESHOLD_SECONDS. */
internal const val ENDING_SOON_THRESHOLD_SECONDS = 60

/**
 * Color for the watch focus ongoing-activity, mirroring the phone live bar's state palette: muted
 * while paused, the warm coral in the final stretch, and the (already-resolved, phone-mirrored)
 * [accentColor] otherwise. Pure so it can be unit-tested without a watch theme/Context.
 */
internal fun focusOngoingActivityColor(paused: Boolean, remainingSeconds: Int, accentColor: Int): Int = when {
    paused -> ChronosTileUi.MUTED_COLOR
    remainingSeconds in 1..ENDING_SOON_THRESHOLD_SECONDS -> ChronosTileUi.WARN_COLOR
    else -> accentColor
}
