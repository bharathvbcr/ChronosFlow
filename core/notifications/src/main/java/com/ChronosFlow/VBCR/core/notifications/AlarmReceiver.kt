package com.ChronosFlow.VBCR.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
open class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var alarmDeliveryCoordinator: AlarmDeliveryCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val receiverClass = javaClass
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                alarmDeliveryCoordinator.deliverFromAlarmIntent(intent, receiverClass)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
