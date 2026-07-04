package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteHabitByIdUseCase
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "HabitActionReceiver"

@AndroidEntryPoint
class HabitActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var completeHabitByIdUseCase: CompleteHabitByIdUseCase

    @Inject
    lateinit var currentBlockNotificationCoordinator: CurrentBlockNotificationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (action == ACTION_COMPLETE) {
            val pendingResult = goAsync()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                try {
                    completeHabitByIdUseCase(habitId, LocalDate.now())

                    if (intent.getBooleanExtra(EXTRA_REFRESH_CURRENT_BLOCK, false)) {
                        currentBlockNotificationCoordinator.refresh()
                    } else if (notificationId != -1) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationId)
                        ReminderNotificationGroups.refreshSummary(context)
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Action failed: ${ex.message}", ex)
                } finally {
                    withContext(Dispatchers.Main) {
                        pendingResult.finish()
                    }
                    scope.cancel()
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
