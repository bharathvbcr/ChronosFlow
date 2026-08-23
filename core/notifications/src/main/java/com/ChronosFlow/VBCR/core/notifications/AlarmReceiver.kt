package com.ChronosFlow.VBCR.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
open class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var alarmDeliveryCoordinator: AlarmDeliveryCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val receiverClass = javaClass
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                alarmDeliveryCoordinator.deliverFromAlarmIntent(intent, receiverClass)
            } catch (e: Exception) {
                // An exception here must never reach the default uncaught handler: this runs in a
                // background process brought up by the alarm, so an uncaught error kills the whole
                // process and turns every recurring reminder into a crash loop. Log and finish.
                Log.e(TAG, "Alarm delivery failed for ${intent.getStringExtra(AlarmDeliveryCoordinator.EXTRA_ID)}", e)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
