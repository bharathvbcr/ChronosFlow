package com.ChronosFlow.VBCR.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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
        isPaused: Boolean = false,
        redactSensitiveTitles: Boolean = false,
        promotedNotificationsAllowed: Boolean = true,
        contentIntent: PendingIntent? = null,
        pauseIntent: PendingIntent? = null,
        resumeIntent: PendingIntent? = null,
        stopIntent: PendingIntent? = null,
        extendIntent: PendingIntent? = null,
        phaseSegments: List<FocusPhaseSegment> = emptyList(),
        currentPhaseIndex: Int = 0,
        subText: String? = null
    ): Notification {
        val (max, progress) = focusNotificationProgress(totalSeconds, timeLeftSeconds)
        if (liveUpdateRenderer.canRender()) {
            return liveUpdateRenderer.build(
                channelId = channelId,
                title = title,
                text = text,
                timeLeftSeconds = timeLeftSeconds,
                totalSeconds = totalSeconds,
                isPaused = isPaused,
                redactSensitiveTitles = redactSensitiveTitles,
                promotedNotificationsAllowed = promotedNotificationsAllowed,
                contentIntent = contentIntent,
                pauseIntent = pauseIntent,
                resumeIntent = resumeIntent,
                stopIntent = stopIntent,
                extendIntent = extendIntent,
                phaseSegments = phaseSegments,
                currentPhaseIndex = currentPhaseIndex,
                subText = subText
            )
        }
        // Compat path (SDK < 36): a split session shows overall-session progress on the determinate
        // bar (segments aren't supported pre-36); a flat session keeps its single-phase progress.
        val barSegments = focusBarSegments(phaseSegments, currentPhaseIndex, totalSeconds)
        val (barMax, barProgress) = if (barSegments.size > 1) {
            barSegments.sumOf { it.lengthSeconds }.coerceAtLeast(1) to
                focusSegmentedProgressPoint(barSegments, timeLeftSeconds)
        } else {
            max to progress
        }

        val displayTitle = PrivacyRedaction.focusNotificationTitle(title, redactSensitiveTitles)
        val displayText = if (redactSensitiveTitles) {
            FocusNotificationContent.REDACTED_BODY
        } else {
            text
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_chronosflow_notification)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setSubText(subText ?: context.getString(R.string.focus_notification_subtext))
            .setContentIntent(contentIntent)
            // Tint icon/app-name (and the determinate bar) with the Material You accent, matching
            // the live-update path: muted while paused, warm in the final stretch. Brand violet
            // fallback below Android 12.
            .setColor(
                ContextCompat.getColor(
                    context,
                    focusProgressBarColorRes(
                        focusBarState(isPaused, timeLeftSeconds),
                        android.os.Build.VERSION.SDK_INT
                    )
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setProgress(barMax, barProgress, false)
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
