package com.chronosflow.widget

import android.content.Context
import com.chronosflow.core.domain.wear.WearLinkContract
import com.chronosflow.core.domain.wear.WearLinkStatus
import com.chronosflow.core.domain.wear.WearLinkStatusProvider
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-module implementation of [WearLinkStatusProvider]. Reads currently-reachable Wear nodes via
 * the [com.google.android.gms.wearable.NodeClient] and the last publish time from
 * [WearLinkStatusStore], and drives an on-demand push through [ChronosWidgetHub].
 *
 * All Wearable calls are best-effort: the APIs are absent on devices without Google Play services,
 * and the connected-nodes task can time out when no watch is around — both resolve to a "not
 * connected" status rather than throwing.
 */
@Singleton
class WearLinkStatusProviderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val linkStatusStore: WearLinkStatusStore
) : WearLinkStatusProvider {

    override suspend fun currentStatus(): WearLinkStatus = withContext(Dispatchers.IO) {
        val lastPublishedAt = linkStatusStore.lastPublishedAtMillis()
        val nodes = runCatching {
            Tasks.await(
                Wearable.getNodeClient(context).connectedNodes,
                NODE_QUERY_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        }.getOrDefault(emptyList())
        // Nodes that actually advertise the ChronosFlow watch app — the only ones that can receive
        // the mirror. A watch can be connected for other apps without ours installed.
        val appNodes = runCatching {
            Tasks.await(
                Wearable.getCapabilityClient(context)
                    .getCapability(WearLinkContract.WEAR_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE),
                NODE_QUERY_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            ).nodes.toList()
        }.getOrDefault(emptyList())
        // Prefer naming a node that has our app; fall back to any nearby/connected node.
        val connectedName = (appNodes + nodes).preferredDisplayName()
        WearLinkStatus(
            // We can only enumerate currently-reachable nodes; a prior successful push proves a
            // watch was paired even if it is offline right now.
            watchPaired = nodes.isNotEmpty() || lastPublishedAt > 0L,
            watchConnected = nodes.isNotEmpty(),
            watchAppInstalled = appNodes.isNotEmpty(),
            connectedNodeName = connectedName?.takeIf { it.isNotBlank() },
            lastPublishedAtMillis = lastPublishedAt
        )
    }

    /** First nearby node's name, else the first node's; null when the list is empty. */
    private fun List<Node>.preferredDisplayName(): String? =
        firstOrNull { it.isNearby }?.displayName ?: firstOrNull()?.displayName

    override suspend fun syncNow(): WearLinkStatus {
        runCatching { ChronosWidgetHub.refreshAll(context) }
        return currentStatus()
    }

    private companion object {
        const val NODE_QUERY_TIMEOUT_SECONDS = 5L
    }
}
