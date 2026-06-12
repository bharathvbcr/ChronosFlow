package com.chronosflow.wear

import android.content.Context
import com.chronosflow.core.domain.wear.WearActionContract
import com.google.android.gms.wearable.Wearable

/**
 * Sends a watch-originated action to the paired phone over the
 * [com.google.android.gms.wearable.MessageClient]. The phone's WearActionListenerService decodes
 * it and replays it through the same use cases the home-screen widgets use.
 *
 * Best-effort and never throws: the Wearable APIs are absent on devices without Google Play
 * services, and the phone may simply be unreachable. The day-summary / focus mirrors remain the
 * source of truth, so a dropped message just means the optimistic UI update is reconciled on the
 * next mirror push.
 */
object WearActionSender {

    fun send(context: Context, type: String, arg: String) {
        runCatching {
            val payload = WearActionContract.encode(type, arg)
            val appContext = context.applicationContext
            val messageClient = Wearable.getMessageClient(appContext)
            Wearable.getNodeClient(appContext).connectedNodes
                .addOnSuccessListener { nodes ->
                    for (node in nodes) {
                        messageClient.sendMessage(node.id, WearActionContract.ACTION_PATH, payload)
                    }
                }
        }
    }
}
