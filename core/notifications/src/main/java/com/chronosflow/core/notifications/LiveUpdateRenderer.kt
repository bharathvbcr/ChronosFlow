package com.chronosflow.core.notifications

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
        plannedEndAt: java.time.Instant? = null,
        isPaused: Boolean = false,
        redactSensitiveTitles: Boolean = false,
        promotedNotificationsAllowed: Boolean = true,
        contentIntent: PendingIntent? = null,
        pauseIntent: PendingIntent? = null,
        resumeIntent: PendingIntent? = null,
        stopIntent: PendingIntent? = null,
        extendIntent: PendingIntent? = null
    ): Notification {
        val decision = gateway.decide(
            title = title,
            text = text,
            redactSensitiveTitles = redactSensitiveTitles,
            promotedNotificationsAllowed = promotedNotificationsAllowed
        )
        return gateway.build(
            channelId = channelId,
            decision = decision,
            timeLeftSeconds = timeLeftSeconds,
            totalSeconds = totalSeconds,
            plannedEndAt = plannedEndAt,
            isPaused = isPaused,
            contentIntent = contentIntent,
            pauseIntent = pauseIntent,
            resumeIntent = resumeIntent,
            stopIntent = stopIntent,
            extendIntent = extendIntent
        )
    }

    fun latestDecision(
        title: String,
        text: String,
        redactSensitiveTitles: Boolean = false,
        promotedNotificationsAllowed: Boolean = true
    ): LiveUpdateDecision = gateway.decide(
        title = title,
        text = text,
        redactSensitiveTitles = redactSensitiveTitles,
        promotedNotificationsAllowed = promotedNotificationsAllowed
    )

    fun usesSelfUpdatingMetricTimer(isRunning: Boolean, decision: LiveUpdateDecision): Boolean =
        usesSelfUpdatingMetricTimer(
            sdkInt = decision.sdkInt,
            isRunning = isRunning,
            canUsePromotedOngoing = decision.canUsePromotedOngoing
        )
}
