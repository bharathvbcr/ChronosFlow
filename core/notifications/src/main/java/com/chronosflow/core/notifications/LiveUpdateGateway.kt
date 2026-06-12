package com.chronosflow.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class LiveUpdateStyle {
    METRIC,
    PROGRESS,
    COMPAT_PROGRESS
}

internal enum class LiveMetricTimerMode {
    RUNNING,
    PAUSED
}

internal enum class NotificationPrimaryAction {
    PAUSE,
    RESUME
}

data class LiveUpdateDecision(
    val sdkInt: Int,
    val style: LiveUpdateStyle,
    val canUsePromotedOngoing: Boolean,
    val redactedTitle: String,
    val redactedText: String
)

@Singleton
class LiveUpdateGateway @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun decide(
        sdkInt: Int = Build.VERSION.SDK_INT,
        title: String,
        text: String,
        redactSensitiveTitles: Boolean,
        promotedNotificationsAllowed: Boolean = true
    ): LiveUpdateDecision {
        val canPostPromoted = canPostPromotedNotifications(sdkInt)
        return resolveLiveUpdateDecision(
            sdkInt = sdkInt,
            title = title,
            text = text,
            redactSensitiveTitles = redactSensitiveTitles,
            promotedNotificationsAllowed = promotedNotificationsAllowed,
            canPostPromotedNotifications = canPostPromoted
        )
    }

    fun build(
        channelId: String,
        decision: LiveUpdateDecision,
        timeLeftSeconds: Int,
        totalSeconds: Int,
        plannedEndAt: Instant? = null,
        isPaused: Boolean = false,
        contentIntent: PendingIntent? = null,
        pauseIntent: PendingIntent? = null,
        resumeIntent: PendingIntent? = null,
        stopIntent: PendingIntent? = null,
        extendIntent: PendingIntent? = null
    ): Notification {
        val (max, progress) = focusNotificationProgress(totalSeconds, timeLeftSeconds)
        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_chronosflow_notification)
            .setColor(ContextCompat.getColor(context, R.color.chronosflow_brand_accent))
            .setContentTitle(decision.redactedTitle)
            .setContentText(decision.redactedText)
            .setSubText(context.getString(R.string.focus_notification_subtext))
            .setContentIntent(contentIntent)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setColorized(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)

        if (Build.VERSION.SDK_INT >= 36) {
            // Compact glanceable text shown in the status-bar live pill / chip.
            builder.setShortCriticalText(
                FocusNotificationContent.pillText(context, timeLeftSeconds, isPaused)
            )
        }

        if (decision.canUsePromotedOngoing && Build.VERSION.SDK_INT >= 37) {
            builder.setRequestPromotedOngoing(true)
        }

        if (Build.VERSION.SDK_INT >= 36) {
            // Surfaces remaining time in the status-bar chip for promoted live updates.
            builder.setShortCriticalText(liveUpdateShortCriticalText(timeLeftSeconds))
        }

        when (decision.style) {
            LiveUpdateStyle.METRIC -> applyMetricStyle(
                builder = builder,
                plannedEndAt = plannedEndAt,
                timeLeftSeconds = timeLeftSeconds,
                isPaused = isPaused,
                max = max,
                progress = progress
            )
            LiveUpdateStyle.PROGRESS -> applyProgressStyle(builder, max, progress)
            LiveUpdateStyle.COMPAT_PROGRESS -> builder.setProgress(max, progress, false)
        }

        return builder.apply {
            when (notificationPrimaryAction(isPaused)) {
                NotificationPrimaryAction.PAUSE -> pauseIntent?.let {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(context, R.drawable.ic_notif_pause),
                            context.getString(R.string.focus_notification_action_pause),
                            it
                        ).build()
                    )
                }

                NotificationPrimaryAction.RESUME -> resumeIntent?.let {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(context, R.drawable.ic_notif_resume),
                            context.getString(R.string.focus_notification_action_resume),
                            it
                        ).build()
                    )
                }
            }
            stopIntent?.let {
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_notif_stop),
                        context.getString(R.string.focus_notification_action_stop),
                        it
                    ).build()
                )
            }
            extendIntent?.let {
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_notif_extend),
                        context.getString(R.string.focus_notification_action_extend),
                        it
                    ).build()
                )
            }
        }.build()
    }

    private fun applyMetricStyle(
        builder: Notification.Builder,
        plannedEndAt: Instant?,
        timeLeftSeconds: Int,
        isPaused: Boolean,
        max: Int,
        progress: Int
    ) {
        if (Build.VERSION.SDK_INT < 37) {
            applyProgressStyle(builder, max, progress)
            return
        }

        val metricValue = when (liveMetricTimerMode(isPaused)) {
            LiveMetricTimerMode.RUNNING -> {
                val endAt = plannedEndAt ?: Instant.now().plusSeconds(timeLeftSeconds.coerceAtLeast(0).toLong())
                Notification.Metric.TimeDifference.forTimer(
                    endAt,
                    Notification.Metric.TimeDifference.FORMAT_ADAPTIVE
                )
            }

            LiveMetricTimerMode.PAUSED -> Notification.Metric.TimeDifference.forPausedTimer(
                Duration.ofSeconds(timeLeftSeconds.coerceAtLeast(0).toLong()),
                Notification.Metric.TimeDifference.FORMAT_ADAPTIVE
            )
        }
        builder.setStyle(
            Notification.MetricStyle()
                .addMetric(
                    Notification.Metric(
                        metricValue,
                        context.getString(R.string.focus_notification_metric_label)
                    )
                )
                .setCriticalMetric(0)
        )
    }

    private fun applyProgressStyle(builder: Notification.Builder, max: Int, progress: Int) {
        if (Build.VERSION.SDK_INT >= 36) {
            val accent = ContextCompat.getColor(context, R.color.focus_progress_accent)
            builder.setStyle(
                Notification.ProgressStyle()
                    .setStyledByProgress(true)
                    .setProgress(progress)
                    .setProgressTrackerIcon(
                        Icon.createWithResource(context, R.drawable.ic_focus_session)
                    )
                    .addProgressSegment(
                        Notification.ProgressStyle.Segment(max)
                            .setColor(accent)
                    )
            )
        } else {
            builder.setProgress(max, progress, false)
        }
    }

    private fun canPostPromotedNotifications(sdkInt: Int): Boolean {
        if (sdkInt < 37) return false
        if (Build.VERSION.SDK_INT < 37) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager?.canPostPromotedNotifications() == true
    }
}

internal fun resolveLiveUpdateDecision(
    sdkInt: Int,
    title: String,
    text: String,
    redactSensitiveTitles: Boolean,
    promotedNotificationsAllowed: Boolean,
    canPostPromotedNotifications: Boolean
): LiveUpdateDecision {
    val displayTitle = PrivacyRedaction.focusNotificationTitle(title, redactSensitiveTitles)
    val displayText = if (redactSensitiveTitles) {
        FocusNotificationContent.REDACTED_BODY
    } else {
        text
    }
    val canUsePromotedOngoing = promotedNotificationsAllowed && canPostPromotedNotifications
    val canUseMetricStyle = canUseMetricStyleForSdk(sdkInt) && canUsePromotedOngoing
    val style = when {
        canUseMetricStyle -> LiveUpdateStyle.METRIC
        sdkInt >= 36 -> LiveUpdateStyle.PROGRESS
        else -> LiveUpdateStyle.COMPAT_PROGRESS
    }
    return LiveUpdateDecision(
        sdkInt = sdkInt,
        style = style,
        canUsePromotedOngoing = canUsePromotedOngoing,
        redactedTitle = displayTitle,
        redactedText = displayText
    )
}

internal fun liveUpdateShortCriticalText(timeLeftSeconds: Int): String {
    val minutes = (timeLeftSeconds.coerceAtLeast(0) + 59) / 60
    return if (minutes < 1) "<1m" else "${minutes}m"
}

internal fun canRenderLiveUpdatesForSdk(sdkInt: Int): Boolean = sdkInt >= 36

internal fun canUseMetricStyleForSdk(sdkInt: Int): Boolean = sdkInt >= 37

internal fun usesSelfUpdatingMetricTimer(
    sdkInt: Int,
    isRunning: Boolean,
    canUsePromotedOngoing: Boolean
): Boolean = sdkInt >= 37 && isRunning && canUsePromotedOngoing

internal fun liveMetricTimerMode(isPaused: Boolean): LiveMetricTimerMode = if (isPaused) {
    LiveMetricTimerMode.PAUSED
} else {
    LiveMetricTimerMode.RUNNING
}

internal fun notificationPrimaryAction(isPaused: Boolean): NotificationPrimaryAction = if (isPaused) {
    NotificationPrimaryAction.RESUME
} else {
    NotificationPrimaryAction.PAUSE
}

object FocusNotificationContent {
    const val REDACTED_BODY = "Session in progress"

    fun formatTimeLeft(timeLeftSeconds: Int): String {
        val minutes = timeLeftSeconds.coerceAtLeast(0) / 60
        val seconds = timeLeftSeconds.coerceAtLeast(0) % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /** Ultra-compact remaining-time label for the status-bar live pill (e.g. "12m", "1h 5m"). */
    fun pillText(context: Context, timeLeftSeconds: Int, isPaused: Boolean): String =
        if (isPaused) {
            context.getString(R.string.focus_notification_pill_paused)
        } else {
            pillTimeText(timeLeftSeconds)
        }

    /** Pure compact-time formatter behind [pillText] (e.g. "12m", "1h 5m", "45s"). */
    fun pillTimeText(timeLeftSeconds: Int): String {
        val total = timeLeftSeconds.coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    fun runningBody(context: Context, timeLeftSeconds: Int, redactSensitiveTitles: Boolean): String {
        if (redactSensitiveTitles) return REDACTED_BODY
        return context.getString(
            R.string.focus_notification_running_text,
            formatTimeLeft(timeLeftSeconds)
        )
    }

    fun pausedBody(context: Context, timeLeftSeconds: Int, redactSensitiveTitles: Boolean): String {
        if (redactSensitiveTitles) return REDACTED_BODY
        return context.getString(
            R.string.focus_notification_paused_text,
            formatTimeLeft(timeLeftSeconds)
        )
    }
}
