package com.ChronosFlow.VBCR.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveUpdateRenderer @Inject constructor(
    private val gateway: LiveUpdateGateway
) {
    fun canRender(): Boolean = canRenderLiveUpdatesForSdk(Build.VERSION.SDK_INT)

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
        val decision = gateway.decide(
            title = title,
            text = text,
            redactSensitiveTitles = redactSensitiveTitles,
            promotedNotificationsAllowed = promotedNotificationsAllowed,
            // A flat (single-phase) focus timer takes the Android 17 MetricStyle big countdown; a
            // split Pomodoro session keeps its segmented ProgressStyle bar.
            metricCountdown = phaseSegments.size <= 1
        )
        return gateway.build(
            channelId = channelId,
            decision = decision,
            timeLeftSeconds = timeLeftSeconds,
            totalSeconds = totalSeconds,
            isPaused = isPaused,
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
}
