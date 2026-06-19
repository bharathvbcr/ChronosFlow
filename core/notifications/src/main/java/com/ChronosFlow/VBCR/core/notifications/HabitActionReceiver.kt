package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteHabitByIdUseCase
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HabitActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var completeHabitByIdUseCase: CompleteHabitByIdUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (action == ACTION_COMPLETE) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    completeHabitByIdUseCase(habitId, LocalDate.now())

                    // Cancel the notification
                    if (notificationId != -1) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationId)
                        ReminderNotificationGroups.refreshSummary(context)
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
        const val ACTION_COMPLETE = "com.ChronosFlow.VBCR.core.notifications.ACTION_COMPLETE_HABIT"
        const val EXTRA_HABIT_ID = "EXTRA_HABIT_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }
}
