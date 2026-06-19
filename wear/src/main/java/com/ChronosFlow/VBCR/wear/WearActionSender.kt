package com.ChronosFlow.VBCR.wear

import android.content.Context
import com.ChronosFlow.VBCR.core.domain.wear.WearActionContract
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sends a watch-originated action to the paired phone over the
 * [com.google.android.gms.wearable.MessageClient]. The phone's WearActionListenerService decodes
 * it and replays it through the same use cases the home-screen widgets use.
 *
 * Best-effort and never throws: the Wearable APIs are absent on devices without Google Play
 * services, and the phone may simply be unreachable. The day-summary / focus mirrors remain the
 * source of truth, so a dropped message just means the optimistic UI update is reconciled on the
 * next mirror push. [onResult] reports whether the message reached at least one node, so callers
 * can tell the wearer when a tap silently won't take effect.
 */
object WearActionSender {

    fun send(context: Context, type: String, arg: String, onResult: (Boolean) -> Unit = {}) {
        val attached = runCatching {
            val payload = WearActionContract.encode(type, arg)
            val appContext = context.applicationContext
            val messageClient = Wearable.getMessageClient(appContext)
            Wearable.getNodeClient(appContext).connectedNodes
                .addOnSuccessListener { nodes ->
                    if (nodes.isEmpty()) {
                        onResult(false)
                        return@addOnSuccessListener
                    }
                    // Report success once every send settles, true if any node accepted it.
                    val remaining = AtomicInteger(nodes.size)
                    val anyDelivered = AtomicBoolean(false)
                    for (node in nodes) {
                        messageClient.sendMessage(node.id, WearActionContract.ACTION_PATH, payload)
                            .addOnSuccessListener { anyDelivered.set(true) }
                            .addOnCompleteListener {
                                if (remaining.decrementAndGet() == 0) onResult(anyDelivered.get())
                            }
                    }
                }
                .addOnFailureListener { onResult(false) }
        }
        // Only reachable if wiring up the listeners threw (e.g. Play services absent); the
        // listeners own the callback otherwise, so this never double-reports.
        if (attached.isFailure) onResult(false)
    }
}
