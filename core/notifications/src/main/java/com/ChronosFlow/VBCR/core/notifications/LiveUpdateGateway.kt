package com.ChronosFlow.VBCR.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class LiveUpdateStyle {
    /** Android 17 (API 37) [Notification.MetricStyle] — a big OS-driven count-down metric. */
    METRIC,
    PROGRESS,
    COMPAT_PROGRESS
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
        promotedNotificationsAllowed: Boolean = true,
        // True for a flat (single-phase) count-down timer surface — the focus session — which prefers
        // the Android 17 MetricStyle big-countdown presentation. A segmented Pomodoro bar or the
        // block "now" progress surface leaves this false and keeps the ProgressStyle bar.
        metricCountdown: Boolean = false
    ): LiveUpdateDecision {
        val canPostPromoted = canPostPromotedNotifications(sdkInt)
        return resolveLiveUpdateDecision(
            sdkInt = sdkInt,
            title = title,
            text = text,
            redactSensitiveTitles = redactSensitiveTitles,
            promotedNotificationsAllowed = promotedNotificationsAllowed,
            canPostPromotedNotifications = canPostPromoted,
            metricCountdown = metricCountdown
        )
    }

    fun build(
        channelId: String,
        decision: LiveUpdateDecision,
        timeLeftSeconds: Int,
        totalSeconds: Int,
        isPaused: Boolean = false,
        contentIntent: PendingIntent? = null,
        pauseIntent: PendingIntent? = null,
        resumeIntent: PendingIntent? = null,
        stopIntent: PendingIntent? = null,
        extendIntent: PendingIntent? = null,
        // "Start focus" action for the block-view ("now") notification — absent on the focus path.
        startFocusIntent: PendingIntent? = null,
        // false lets a fresh post alert (a single buzz when a new block begins); updates stay silent.
        onlyAlertOnce: Boolean = true,
        // Ordered work/break phases of a split (Pomodoro) session. >1 entry renders a segmented bar.
        phaseSegments: List<FocusPhaseSegment> = emptyList(),
        currentPhaseIndex: Int = 0,
        // Header label under the app name; null keeps the default "Focus" subtext.
        subText: String? = null
    ): Notification {
        val (max, progress) = focusNotificationProgress(totalSeconds, timeLeftSeconds)
        val barState = focusBarState(isPaused, timeLeftSeconds)
        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_chronosflow_notification)
            .setContentTitle(decision.redactedTitle)
            .setContentText(decision.redactedText)
            .setSubText(subText ?: context.getString(R.string.focus_notification_subtext))
            .setContentIntent(contentIntent)
            // Keep the icon/app-name tint on the steady running accent; only the bar itself
            // shifts color with state so the chrome stays calm while the bar carries the signal.
            .setColor(focusBarColor(FocusBarState.RUNNING))
            .setColorized(false)
            .setOngoing(true)
            .setOnlyAlertOnce(onlyAlertOnce)
            .setCategory(Notification.CATEGORY_PROGRESS)

        // A running session gets an OS-driven count-down chronometer in the header: the system ticks
        // it to zero on its own (no re-posting needed), so the live update reads as a true countdown
        // on the lock screen / shade and reinforces the promoted "Live Update" surface — all while
        // composing with, not replacing, the ProgressStyle bar below. A paused or zeroed timer has
        // nothing to count, so it keeps the static "when" off and shows the body text instead. The
        // MetricStyle path carries its own live countdown metric, so the header chronometer is
        // suppressed there to avoid a doubled timer.
        if (decision.style != LiveUpdateStyle.METRIC && shouldUseLiveCountdown(isPaused, timeLeftSeconds)) {
            builder.setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + timeLeftSeconds * 1000L)
        } else {
            builder.setShowWhen(false)
        }

        if (Build.VERSION.SDK_INT >= 36) {
            // Compact glanceable text for the status-bar live pill / chip: shows "Paused", a
            // magnitude-aware remaining time ("1h 5m", "12m"), and counts down by seconds ("45s")
            // in the final minute so the pill itself signals "ending soon".
            builder.setShortCriticalText(
                FocusNotificationContent.pillText(context, timeLeftSeconds, isPaused)
            )
        }

        if (decision.canUsePromotedOngoing && Build.VERSION.SDK_INT >= 37) {
            builder.setRequestPromotedOngoing(true)
        }

        // A split session draws one bar segment per work/break phase; a flat session keeps the
        // single-segment bar. Segment lengths use the live current-phase total (robust to +/-5m).
        val segments = focusBarSegments(phaseSegments, currentPhaseIndex, totalSeconds)
        when (decision.style) {
            // METRIC is only chosen for a flat (segment-free) timer, so there is no segmented bar to
            // preserve here; if the device somehow can't render it, fall back to the single-bar style.
            LiveUpdateStyle.METRIC ->
                if (Build.VERSION.SDK_INT >= 37) {
                    applyMetricStyle(builder, timeLeftSeconds, isPaused)
                } else {
                    applyProgressStyle(builder, max, progress, barState)
                }

            LiveUpdateStyle.PROGRESS ->
                if (segments.size > 1) {
                    applySegmentedProgressStyle(builder, segments, timeLeftSeconds, barState)
                } else {
                    applyProgressStyle(builder, max, progress, barState)
                }

            LiveUpdateStyle.COMPAT_PROGRESS ->
                if (segments.size > 1) {
                    val segMax = segments.sumOf { it.lengthSeconds }.coerceAtLeast(1)
                    builder.setProgress(segMax, focusSegmentedProgressPoint(segments, timeLeftSeconds), false)
                } else {
                    builder.setProgress(max, progress, false)
                }
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
            startFocusIntent?.let {
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_focus_session),
                        context.getString(R.string.current_block_action_start_focus),
                        it
                    ).build()
                )
            }
        }.build()
    }

    /**
     * Android 17 [Notification.MetricStyle]: a single big live count-down metric (the "Remaining"
     * timer). A running timer uses [Notification.Metric.TimeDifference.forTimer] anchored at the
     * finish [Instant] so the OS ticks it down on its own; a paused one freezes at the remaining
     * [Duration]. The metric is also flagged critical so it carries into the collapsed / chip form.
     * Only invoked on SDK 37+ for a flat focus session (the segmented Pomodoro bar stays on
     * ProgressStyle, where the per-phase rhythm matters more than a bare countdown).
     */
    @RequiresApi(37)
    private fun applyMetricStyle(builder: Notification.Builder, timeLeftSeconds: Int, isPaused: Boolean) {
        val remainingSeconds = timeLeftSeconds.coerceAtLeast(0).toLong()
        val format = Notification.Metric.TimeDifference.FORMAT_ADAPTIVE
        val value = if (isPaused) {
            Notification.Metric.TimeDifference.forPausedTimer(Duration.ofSeconds(remainingSeconds), format)
        } else {
            Notification.Metric.TimeDifference.forTimer(Instant.now().plusSeconds(remainingSeconds), format)
        }
        val metric = Notification.Metric(value, context.getString(R.string.focus_metric_remaining_label))
        builder.setStyle(
            Notification.MetricStyle()
                .addMetric(metric)
                .setCriticalMetric(0)
        )
    }

    private fun applyProgressStyle(builder: Notification.Builder, max: Int, progress: Int, state: FocusBarState) {
        if (Build.VERSION.SDK_INT >= 36) {
            val accent = focusBarColor(state)
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

    /**
     * Renders the split (Pomodoro) bar: one [Notification.ProgressStyle.Segment] per work/break
     * phase so the whole session's rhythm is visible at a glance, with the progress point at the
     * cumulative position. Focus segments use the accent (the current one carries the live state
     * color); break segments use the complementary teal. Only invoked on SDK 36+.
     */
    private fun applySegmentedProgressStyle(
        builder: Notification.Builder,
        segments: List<FocusBarSegment>,
        currentPhaseTimeLeftSeconds: Int,
        currentState: FocusBarState
    ) {
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(focusSegmentedProgressPoint(segments, currentPhaseTimeLeftSeconds))
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_focus_session))
        segments.forEach { segment ->
            val colorRes = focusSegmentColorRes(
                isBreak = segment.isBreak,
                isCurrent = segment.isCurrent,
                state = currentState,
                sdkInt = Build.VERSION.SDK_INT
            )
            style.addProgressSegment(
                Notification.ProgressStyle.Segment(segment.lengthSeconds)
                    .setColor(ContextCompat.getColor(context, colorRes))
            )
        }
        builder.setStyle(style)
    }

    /**
     * Color for the thick live progress bar. Running prefers the Material You dynamic system
     * palette (Android 12+ / API 31), falling back to the ChronosFlow brand violet on older
     * releases; a paused timer uses a muted tone and the final stretch a warm "ending soon" tint,
     * so the bar's state reads at a glance alongside the status-bar pill.
     */
    private fun focusBarColor(state: FocusBarState): Int =
        ContextCompat.getColor(context, focusProgressBarColorRes(state, Build.VERSION.SDK_INT))

    private fun canPostPromotedNotifications(sdkInt: Int): Boolean {
        if (sdkInt < 37) return false
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
    canPostPromotedNotifications: Boolean,
    metricCountdown: Boolean = false
): LiveUpdateDecision {
    val displayTitle = PrivacyRedaction.focusNotificationTitle(title, redactSensitiveTitles)
    val displayText = if (redactSensitiveTitles) {
        FocusNotificationContent.REDACTED_BODY
    } else {
        text
    }
    val canUsePromotedOngoing = promotedNotificationsAllowed && canPostPromotedNotifications
    // A flat count-down timer (the focus session) on Android 17 uses the MetricStyle big live
    // countdown — the showcase timer presentation. Everything else (segmented Pomodoro bar, the
    // block "now" progress surface, Android 16) keeps the thick ProgressStyle bar, which carries
    // progress a bar-less metric can't; below 36 falls back to the legacy determinate bar.
    val style = when {
        sdkInt >= 37 && metricCountdown -> LiveUpdateStyle.METRIC
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

/** Visual state of the live focus progress bar, driving its color. */
internal enum class FocusBarState { RUNNING, ENDING_SOON, PAUSED }

/** A running timer enters its "ending soon" emphasis when this many seconds (or fewer) remain. */
internal const val FOCUS_BAR_ENDING_SOON_THRESHOLD_SECONDS = 60

/** Derives the bar state; pause takes precedence over the ending-soon emphasis. */
internal fun focusBarState(isPaused: Boolean, timeLeftSeconds: Int): FocusBarState = when {
    isPaused -> FocusBarState.PAUSED
    timeLeftSeconds in 1..FOCUS_BAR_ENDING_SOON_THRESHOLD_SECONDS -> FocusBarState.ENDING_SOON
    else -> FocusBarState.RUNNING
}

/**
 * Resolves the color resource for the live focus progress bar (and the focus notification's
 * accent tint). Running uses the Material You dynamic system palette on Android 12+ (API 31 / S)
 * with a brand-violet fallback below; a paused timer maps to a muted neutral / grey so the bar
 * looks inactive; the final stretch uses the warm critical tint to signal "ending soon".
 */
internal fun focusProgressBarColorRes(state: FocusBarState, sdkInt: Int): Int = when (state) {
    FocusBarState.ENDING_SOON -> R.color.notification_accent_critical
    FocusBarState.PAUSED ->
        if (sdkInt >= Build.VERSION_CODES.S) android.R.color.system_neutral1_400 else R.color.focus_progress_paused
    FocusBarState.RUNNING ->
        if (sdkInt >= Build.VERSION_CODES.S) android.R.color.system_accent1_500 else R.color.focus_progress_accent
}

/** One work/break phase of a split (Pomodoro) focus session, as the renderer sees it. */
data class FocusPhaseSegment(val isBreak: Boolean, val durationSeconds: Int)

/** A bar segment ready to render: its length, kind, and whether it is the phase counting down. */
internal data class FocusBarSegment(val lengthSeconds: Int, val isBreak: Boolean, val isCurrent: Boolean)

/**
 * Parses the compact phase plan the focus service carries across the process boundary
 * ("F25,B5,F25,B5" — kind initial + minutes), mirroring the in-app split encoding. Returns the
 * segments in seconds; malformed or non-positive tokens are dropped.
 */
fun parseFocusPhasePlan(encoded: String?): List<FocusPhaseSegment> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split(",").mapNotNull { token ->
        val trimmed = token.trim()
        if (trimmed.length < 2) return@mapNotNull null
        val isBreak = when (trimmed.first()) {
            'B', 'b' -> true
            'F', 'f' -> false
            else -> return@mapNotNull null
        }
        val minutes = trimmed.drop(1).toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        FocusPhaseSegment(isBreak = isBreak, durationSeconds = minutes * 60)
    }
}

/**
 * Builds the render-ready segment list. The current phase's length is taken from the live
 * [currentPhaseTotalSeconds] (so a +/-5m adjustment is reflected even though the plan string is
 * only re-sent on a phase change); the other phases keep their planned lengths.
 */
internal fun focusBarSegments(
    plan: List<FocusPhaseSegment>,
    currentPhaseIndex: Int,
    currentPhaseTotalSeconds: Int
): List<FocusBarSegment> {
    if (plan.size <= 1) return emptyList()
    val idx = currentPhaseIndex.coerceIn(0, plan.lastIndex)
    return plan.mapIndexed { i, segment ->
        val length = if (i == idx) currentPhaseTotalSeconds.coerceAtLeast(1) else segment.durationSeconds.coerceAtLeast(1)
        FocusBarSegment(lengthSeconds = length, isBreak = segment.isBreak, isCurrent = i == idx)
    }
}

/** Absolute progress point along the summed segment lengths (completed phases + elapsed-in-current). */
internal fun focusSegmentedProgressPoint(
    segments: List<FocusBarSegment>,
    currentPhaseTimeLeftSeconds: Int
): Int {
    val total = segments.sumOf { it.lengthSeconds }.coerceAtLeast(1)
    val idx = segments.indexOfFirst { it.isCurrent }.takeIf { it >= 0 } ?: 0
    val before = segments.take(idx).sumOf { it.lengthSeconds }
    val currentLength = segments.getOrNull(idx)?.lengthSeconds ?: 1
    val elapsedInCurrent = (currentLength - currentPhaseTimeLeftSeconds).coerceIn(0, currentLength)
    return (before + elapsedInCurrent).coerceIn(0, total)
}

/**
 * Per-segment color: break phases use the complementary teal; the current focus phase carries the
 * live bar state (running / ending-soon / paused) while other focus phases stay on the steady accent.
 */
internal fun focusSegmentColorRes(
    isBreak: Boolean,
    isCurrent: Boolean,
    state: FocusBarState,
    sdkInt: Int
): Int = when {
    isBreak -> R.color.focus_break_segment
    isCurrent -> focusProgressBarColorRes(state, sdkInt)
    else -> focusProgressBarColorRes(FocusBarState.RUNNING, sdkInt)
}

internal fun canRenderLiveUpdatesForSdk(sdkInt: Int): Boolean = sdkInt >= 36

/**
 * Whether the live notification should render the header count-down chronometer: only for a running
 * session that still has time on the clock. A paused timer must freeze (a live countdown would keep
 * ticking past the pause), and a zeroed / over-run timer has nothing left to count down, so both
 * fall back to the static body text. Pure so the decision is unit-testable without an Android build.
 */
internal fun shouldUseLiveCountdown(isPaused: Boolean, timeLeftSeconds: Int): Boolean =
    !isPaused && timeLeftSeconds > 0

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
