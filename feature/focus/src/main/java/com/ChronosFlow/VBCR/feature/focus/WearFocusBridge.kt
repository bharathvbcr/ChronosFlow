package com.ChronosFlow.VBCR.feature.focus

import android.content.Context
import com.ChronosFlow.VBCR.core.domain.wear.WearFocusContract
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of what the phone wants the watch to mirror. Kept as a plain value so the
 * publish contract (which keys/values go on the Data Layer item) is unit-testable without
 * Google Play services on the classpath.
 */
internal data class WearFocusPayload(
    val active: Boolean,
    val paused: Boolean = false,
    val title: String? = null,
    val plannedEndAtMillis: Long? = null,
    val pausedTimeLeftSeconds: Int? = null,
    val totalSeconds: Int? = null
)

internal fun runningFocusPayload(title: String, plannedEndAtMillis: Long, totalSeconds: Int) =
    WearFocusPayload(
        active = true,
        paused = false,
        title = title,
        plannedEndAtMillis = plannedEndAtMillis,
        totalSeconds = totalSeconds
    )

internal fun pausedFocusPayload(title: String, timeLeftSeconds: Int, totalSeconds: Int) =
    WearFocusPayload(
        active = true,
        paused = true,
        title = title,
        pausedTimeLeftSeconds = timeLeftSeconds,
        totalSeconds = totalSeconds
    )

internal fun clearedFocusPayload() = WearFocusPayload(active = false)

/**
 * The Data Layer key/value entries for a payload. Running updates intentionally omit any
 * per-tick value so repeated calls during a session produce identical entries that the Data
 * Layer de-duplicates; the watch renders the live countdown locally from the planned end.
 */
internal fun WearFocusPayload.toDataEntries(): Map<String, Any> {
    val entries = linkedMapOf<String, Any>()
    entries[WearFocusContract.KEY_ACTIVE] = active
    if (!active) return entries
    entries[WearFocusContract.KEY_PAUSED] = paused
    title?.let { entries[WearFocusContract.KEY_TITLE] = it }
    totalSeconds?.let { entries[WearFocusContract.KEY_TOTAL_SECONDS] = it }
    if (paused) {
        pausedTimeLeftSeconds?.let { entries[WearFocusContract.KEY_PAUSED_TIME_LEFT_SECONDS] = it }
    } else {
        plannedEndAtMillis?.let { entries[WearFocusContract.KEY_PLANNED_END_AT_MILLIS] = it }
    }
    return entries
}

/**
 * Publishes the active focus session to the Wearable Data Layer so a paired watch can mirror it
 * as a live ongoing activity (see the :wear module's FocusWearListenerService).
 *
 * All calls are best-effort and never throw — Wearable APIs are absent on devices without
 * Google Play services.
 */
@Singleton
class WearFocusBridge @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    /**
     * @param isPhaseChange Pass [true] when the session is starting for the first time (a phase
     *   change). Pass [false] for periodic mid-session tick refreshes — those produce identical
     *   Data Layer entries anyway (no per-tick value in the payload) so there is no benefit to
     *   waking the watch radio urgently.
     */
    fun publishRunning(
        title: String,
        plannedEndAtMillis: Long,
        totalSeconds: Int,
        isPhaseChange: Boolean = true
    ) = publish(runningFocusPayload(title, plannedEndAtMillis, totalSeconds), urgent = isPhaseChange)

    fun publishPaused(title: String, timeLeftSeconds: Int, totalSeconds: Int) =
        publish(pausedFocusPayload(title, timeLeftSeconds, totalSeconds), urgent = true)

    fun clear() = publish(clearedFocusPayload(), urgent = true)

    private fun publish(payload: WearFocusPayload, urgent: Boolean) {
        runCatching {
            val putRequest = PutDataMapRequest.create(WearFocusContract.FOCUS_PATH).apply {
                payload.toDataEntries().forEach { (key, value) ->
                    when (value) {
                        is Boolean -> dataMap.putBoolean(key, value)
                        is Int -> dataMap.putInt(key, value)
                        is Long -> dataMap.putLong(key, value)
                        is String -> dataMap.putString(key, value)
                    }
                }
            }.asPutDataRequest()
            val request = if (urgent) putRequest.setUrgent() else putRequest
            dataClient.putDataItem(request)
        }
    }
}
