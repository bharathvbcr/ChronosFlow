package com.chronosflow.core.domain.wear

/**
 * Snapshot of the phone ↔ watch Data Layer link, surfaced on the phone's Privacy & Sync settings
 * so the user can confirm a paired watch is actually receiving the day-summary mirror.
 *
 * @property watchPaired whether any Wear node is paired with this phone at all.
 * @property watchConnected whether a paired watch is currently reachable (nearby / on the same
 *   account network). A watch can be paired but momentarily disconnected.
 * @property watchAppInstalled whether a reachable watch actually has the ChronosFlow watch app
 *   installed (advertises [WearLinkContract.WEAR_APP_CAPABILITY]). A watch can be connected without
 *   the app, in which case the mirror has nowhere to land.
 * @property connectedNodeName friendly name of the reachable watch, when one is connected.
 * @property lastPublishedAtMillis epoch-millis of the last successful day-summary push, or 0 if the
 *   phone has never pushed to the watch on this install.
 */
data class WearLinkStatus(
    val watchPaired: Boolean,
    val watchConnected: Boolean,
    val watchAppInstalled: Boolean,
    val connectedNodeName: String?,
    val lastPublishedAtMillis: Long
) {
    companion object {
        /** The "no Wear support / nothing known yet" status; returned when the query can't run. */
        val UNKNOWN = WearLinkStatus(
            watchPaired = false,
            watchConnected = false,
            watchAppInstalled = false,
            connectedNodeName = null,
            lastPublishedAtMillis = 0L
        )
    }
}

/**
 * Reads the live Wear link status and lets the settings UI trigger an on-demand sync. Implemented in
 * the app module (where the Wearable APIs and the publish bridge live) and reached from feature
 * modules via a Hilt entry point, so the settings surface needs no direct Google Play services
 * dependency.
 */
interface WearLinkStatusProvider {

    /** Queries paired/connected nodes and the last publish time. Best-effort; never throws. */
    suspend fun currentStatus(): WearLinkStatus

    /**
     * Re-publishes the day summary to the watch now and returns the status afterwards. Drives the
     * "Sync watch now" action. Best-effort; never throws.
     */
    suspend fun syncNow(): WearLinkStatus
}
