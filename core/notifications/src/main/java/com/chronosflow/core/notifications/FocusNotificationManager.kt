package com.chronosflow.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val renderer: FocusProgressNotificationRenderer
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createFocusNotificationChannel(context, notificationManager)
    }

    fun showFocusNotification(
        title: String = context.getString(R.string.focus_notification_default_title),
        timeLeftSeconds: Int,
        totalSeconds: Int,
        plannedEndAt: Instant? = null,
        isPaused: Boolean = false,
        redactSensitiveTitles: Boolean = false
    ) {
        val text = if (isPaused) {
            FocusNotificationContent.pausedBody(context, timeLeftSeconds, redactSensitiveTitles)
        } else {
            FocusNotificationContent.runningBody(context, timeLeftSeconds, redactSensitiveTitles)
        }

        notificationManager.notify(
            FOCUS_NOTIFICATION_ID,
            renderer.build(
                channelId = FOCUS_CHANNEL_ID,
                title = title,
                text = text,
                timeLeftSeconds = timeLeftSeconds,
                totalSeconds = totalSeconds,
                plannedEndAt = plannedEndAt,
                isPaused = isPaused,
                redactSensitiveTitles = redactSensitiveTitles,
                contentIntent = buildFocusNotificationContentIntent(context)
            )
        )
    }

    fun clearFocusNotification() {
        notificationManager.cancel(FOCUS_NOTIFICATION_ID)
    }

    companion object {
        const val FOCUS_CHANNEL_ID = "chronos_focus_timer"
        const val FOCUS_NOTIFICATION_ID = 4201

        fun createFocusNotificationChannel(
            context: Context,
            notificationManager: NotificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            NotificationChannelGroups.ensureCreated(notificationManager)
            val channel = NotificationChannel(
                FOCUS_CHANNEL_ID,
                context.getString(R.string.focus_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.focus_notification_channel_description)
                group = NotificationChannelGroups.FOCUS
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
