package com.chronosflow.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chronosflow.core.domain.planner.PlannerService
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles the "Reflow day" action on a block reminder notification, reorganizing the remaining part
 * of today's plan from the current time without opening the app. Overdue and upcoming flexible
 * blocks are pulled forward; work that is already underway and fixed/locked blocks are left alone.
 */
@AndroidEntryPoint
class ReflowDayActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var plannerService: PlannerService

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFLOW_DAY) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = LocalTime.now()
                plannerService.rebalanceDay(LocalDate.now(), now.hour * 60 + now.minute)

                if (notificationId != -1) {
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    companion object {
        const val ACTION_REFLOW_DAY = "com.chronosflow.core.notifications.ACTION_REFLOW_DAY"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }
}
