package com.chronosflow.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusProgressNotificationRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val liveUpdateRenderer: LiveUpdateRenderer
) {
    fun build(
        channelId: String,
        title: String,
        text: String,
        timeLeftSeconds: Int,
        totalSeconds: Int,
        plannedEndAt: Instant? = null,
        isPaused: Boolean = false,
        redactSensitiveTitles: Boolean = false,
        promotedNotificationsAllowed: Boolean = true,
        contentIntent: PendingIntent? = null,
        pauseIntent: PendingIntent? = null,
        resumeIntent: PendingIntent? = null,
        stopIntent: PendingIntent? = null,
        extendIntent: PendingIntent? = null
    ): Notification {
        val (max, progress) = focusNotificationProgress(totalSeconds, timeLeftSeconds)
        if (liveUpdateRenderer.canRender()) {
            return liveUpdateRenderer.build(
                channelId = channelId,
                title = title,
                text = text,
                timeLeftSeconds = timeLeftSeconds,
                totalSeconds = totalSeconds,
                plannedEndAt = plannedEndAt,
                isPaused = isPaused,
                redactSensitiveTitles = redactSensitiveTitles,
                promotedNotificationsAllowed = promotedNotificationsAllowed,
                contentIntent = contentIntent,
                pauseIntent = pauseIntent,
                resumeIntent = resumeIntent,
                stopIntent = stopIntent,
                extendIntent = extendIntent
            )
        }

        val displayTitle = PrivacyRedaction.focusNotificationTitle(title, redactSensitiveTitles)
        val displayText = if (redactSensitiveTitles) {
            FocusNotificationContent.REDACTED_BODY
        } else {
            text
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_chronosflow_notification)
            .setColor(ContextCompat.getColor(context, R.color.chronosflow_brand_accent))
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setProgress(max, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply {
                when (notificationPrimaryAction(isPaused)) {
                    NotificationPrimaryAction.PAUSE -> if (pauseIntent != null) {
                        addAction(
                            R.drawable.ic_notif_pause,
                            context.getString(R.string.focus_notification_action_pause),
                            pauseIntent
                        )
                    }

                    NotificationPrimaryAction.RESUME -> if (resumeIntent != null) {
                        addAction(
                            R.drawable.ic_notif_resume,
                            context.getString(R.string.focus_notification_action_resume),
                            resumeIntent
                        )
                    }
                }
                if (stopIntent != null) {
                    addAction(
                        R.drawable.ic_notif_stop,
                        context.getString(R.string.focus_notification_action_stop),
                        stopIntent
                    )
                }
                if (extendIntent != null) {
                    addAction(
                        R.drawable.ic_notif_extend,
                        context.getString(R.string.focus_notification_action_extend),
                        extendIntent
                    )
                }
            }
            .build()
    }
}

internal fun focusNotificationProgress(totalSeconds: Int, timeLeftSeconds: Int): Pair<Int, Int> {
    val max = totalSeconds.coerceAtLeast(1)
    return max to (max - timeLeftSeconds).coerceIn(0, max)
}
