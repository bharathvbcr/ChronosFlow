package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ChronosFlow.VBCR.core.domain.notifications.ScreenTimeNudgePresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the optional, gentle screen-time distraction nudge. Low-importance channel so it lands
 * quietly in the shade rather than buzzing. Tapping it opens the app.
 */
@Singleton
class ScreenTimeNudgeNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ScreenTimeNudgePresenter {

    override fun notifyDistraction(todayDistractingMinutes: Int, averageDistractingMinutes: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val body = "More distracted than usual today — ${formatMinutes(todayDistractingMinutes)} " +
            "vs ${formatMinutes(averageDistractingMinutes)} typical. A focus block might help."

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chronosflow_notification)
                .setContentTitle("Screen-time check-in")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(launchIntent())
                .build()
        )
    }

    private fun launchIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen-time nudges",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Gentle reminders when you're more distracted than usual."
        }
        manager.createNotificationChannel(channel)
    }

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    internal companion object {
        const val CHANNEL_ID = "chronos_screen_time_nudge"
        const val NOTIFICATION_ID = 4301
    }
}
