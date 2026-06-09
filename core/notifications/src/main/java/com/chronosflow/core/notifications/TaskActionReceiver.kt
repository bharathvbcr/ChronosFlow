package com.chronosflow.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chronosflow.core.domain.usecase.ToggleTaskCompletionUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class TaskActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (action == ACTION_COMPLETE) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    toggleTaskCompletionUseCase(taskId)

                    if (notificationId != -1) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationId)
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.chronosflow.core.notifications.ACTION_COMPLETE_TASK"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }
}
