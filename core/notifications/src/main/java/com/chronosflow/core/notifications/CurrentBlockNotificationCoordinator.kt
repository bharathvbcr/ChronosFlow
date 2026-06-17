package com.chronosflow.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.chronosflow.core.domain.model.BlockCategories
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.model.occupiesScheduleTime
import com.chronosflow.core.domain.repository.TimeBlockRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts an ongoing "current time block" live notification (Android 16 ProgressStyle,
 * plain progress below) and keeps it fresh by scheduling one inexact alarm at the
 * next block boundary instead of running a ticking service. Progress is derived
 * from absolute times, so even a late boundary delivery renders correctly.
 */
@Singleton
class CurrentBlockNotificationCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val timeBlockRepository: TimeBlockRepository,
    private val liveUpdateGateway: LiveUpdateGateway
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    // Default ON: this is now the single live "now" surface (it replaces the separate block-start
    // reminder + duplicate progress notification). A user who explicitly turned it off in settings
    // keeps that choice and instead gets the fallback block-start reminder (see AlarmDeliveryCoordinator).
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    // Suppression is stored as an expiry timestamp, not a sticky flag: a running focus session bumps
    // it on every notification update (~5s), so it stays suppressed live, but if the focus process
    // dies before onFocusEnded() runs the suppression self-heals once the cap elapses — otherwise the
    // (now default-on) "now" notification could stay hidden forever.
    private fun isFocusActive(): Boolean =
        prefs.getLong(KEY_FOCUS_SUPPRESSED_UNTIL, 0L) > System.currentTimeMillis()

    /**
     * Whether sensitive titles must be hidden, mirroring [PrivacyPreferences.redactSensitiveNotifications]
     * (default ON). Read straight from the shared prefs store so core:notifications needn't depend on
     * core:data — same approach as the daily-review digest override.
     */
    private fun redactSensitiveTitles(): Boolean =
        context.getSharedPreferences(CHRONOS_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REDACT_NOTIFICATIONS, true)

    suspend fun setEnabled(enabled: Boolean) {
        if (isEnabled() == enabled) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            refresh()
        } else {
            dismiss()
        }
    }

    /**
     * Called by [com.chronosflow.feature.focus.FocusService] when a focus session is running: the
     * focus notification becomes the single live surface, so the block "now" notification stands
     * down (never two live notifications at once). Idempotent and cheap to call every tick.
     */
    fun onFocusStarted() {
        val wasSuppressed = isFocusActive()
        // Roll the expiry forward on every call (cheap) so an active session stays suppressed; only
        // do the cancel work on the first transition into suppression.
        prefs.edit().putLong(KEY_FOCUS_SUPPRESSED_UNTIL, System.currentTimeMillis() + FOCUS_SUPPRESS_CAP_MS).apply()
        if (!wasSuppressed) {
            cancelNotification()
            boundaryPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let { alarmManager?.cancel(it) }
        }
    }

    /** Called when a focus session ends terminally; restores the "now" notification if a block is current. */
    suspend fun onFocusEnded() {
        prefs.edit().putLong(KEY_FOCUS_SUPPRESSED_UNTIL, 0L).apply()
        refresh()
    }

    /**
     * @param alert when true the post may make a sound (one buzz as a block begins, driven by the
     *   exact BLOCK_START alarm); silent refreshes (boundaries, edits, focus-end) pass false.
     */
    suspend fun refresh(now: LocalDateTime = LocalDateTime.now(), alert: Boolean = false) {
        if (!isEnabled()) {
            dismiss()
            return
        }
        // A focus session owns the live surface while it runs; stay dark until it ends.
        if (isFocusActive()) {
            cancelNotification()
            return
        }
        val blocks = try {
            timeBlockRepository.getTimeBlocksByDate(now.toLocalDate()).first()
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to load blocks for current-block notification", ex)
            emptyList()
        }
        val nowMinute = now.hour * 60 + now.minute
        val (active, next) = selectCurrentAndNextBlock(blocks, nowMinute)

        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        when {
            // A block is happening now — the primary "now" view. Surface the next break and the next
            // event alongside the active block's name so the live notification reads as a glanceable
            // schedule, not just "current block".
            active != null && notificationsEnabled ->
                postNotification(active, blocks, nowMinute, alert)
            // A gap with the next block approaching — keep the live surface continuous with a
            // countdown. Bounded by a lookahead so an early-morning / long idle stretch doesn't show
            // an hours-long countdown; it appears only once the next block is near.
            active == null && next != null && notificationsEnabled &&
                shouldShowUpNext(next.startMinuteOfDay, UP_NEXT_LOOKAHEAD_MINUTES, nowMinute) ->
                postUpNext(next, previousBlockEndMinute(blocks, nowMinute) ?: nowMinute, nowMinute, blocks)
            else -> cancelNotification()
        }
        scheduleNextBoundary(active, next, now)
    }

    private fun dismiss() {
        cancelNotification()
        boundaryPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let { alarmManager?.cancel(it) }
    }

    private fun postNotification(
        active: TimeBlock,
        blocks: List<TimeBlock>,
        nowMinute: Int,
        alert: Boolean
    ) {
        ReminderNotificationChannels.ensureCreated(context)
        val redact = redactSensitiveTitles()
        val endMinute = active.startMinuteOfDay + active.durationMinutes
        val upcoming = selectUpcomingGlances(blocks, nowMinute)
        val genericTitle = context.getString(R.string.current_block_default_title)
        // Under redaction, hide the block title and the upcoming items' titles (times are not sensitive).
        val title = if (redact) genericTitle else active.title.ifBlank { genericTitle }
        val text = if (redact) {
            redactedCurrentBlockText(active.startMinuteOfDay, endMinute, upcoming)
        } else {
            currentBlockNotificationText(active.startMinuteOfDay, endMinute, upcoming)
        }
        val decision = liveUpdateGateway.decide(
            title = title,
            text = text,
            // Already applied above with block-appropriate wording; don't re-redact with focus copy.
            redactSensitiveTitles = false
        )
        val notification = liveUpdateGateway.build(
            channelId = ReminderNotificationChannels.CURRENT_BLOCK_CHANNEL_ID,
            decision = decision,
            timeLeftSeconds = (endMinute - nowMinute).coerceAtLeast(0) * 60,
            totalSeconds = active.durationMinutes * 60,
            // Tap opens today's dial; the action jumps straight into a focus session for this block.
            contentIntent = buildNotificationContentIntent(
                context = context,
                launch = NotificationLaunch(section = SECTION_DAY, dayTarget = DAY_TARGET_TODAY),
                requestCode = NOTIFICATION_ID
            ),
            // Only offer "Start focus" on blocks where a focus session makes sense — not on a
            // break / sleep / meal, where starting a Pomodoro timer would be contradictory.
            startFocusIntent = if (blockSupportsFocus(active.category)) {
                buildFocusNotificationContentIntent(
                    context = context,
                    blockId = active.id,
                    requestCode = START_FOCUS_REQUEST_CODE
                )
            } else {
                null
            },
            onlyAlertOnce = !alert,
            // Header shows the "now" label plus how many blocks remain after this one, e.g.
            // "Now · 3 to go" — schedule load at a glance without reading the body.
            subText = currentBlockSubText(upcomingBlockCount(blocks, nowMinute))
        )
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (ex: SecurityException) {
            Log.w(TAG, "Notification permission missing for current-block update", ex)
        }
    }

    /** "Now" alone when this is the last block, else "Now · N to go" with the remaining-block count. */
    private fun currentBlockSubText(remaining: Int): String {
        val nowLabel = context.getString(R.string.current_block_subtext)
        if (remaining <= 0) return nowLabel
        val countLabel = context.resources.getQuantityString(
            R.plurals.current_block_remaining_count,
            remaining,
            remaining
        )
        return "$nowLabel · $countLabel"
    }

    /**
     * Live countdown to the upcoming block during a gap (no block active now). Uses the same id/slot
     * as the current-block view so the surface morphs in place; the bar fills from the previous
     * block's end toward the next start, and the text states the absolute start time (which never
     * goes stale between the alarm-driven refreshes). The body also names what follows that block
     * (next break or event) so the gap view is as glanceable as the active one. Always silent — the
     * buzz is reserved for the block actually starting.
     */
    private fun postUpNext(next: TimeBlock, gapStartMinute: Int, nowMinute: Int, blocks: List<TimeBlock>) {
        ReminderNotificationChannels.ensureCreated(context)
        val nextStartMinute = next.startMinuteOfDay
        val redact = redactSensitiveTitles()
        val genericTitle = context.getString(R.string.current_block_default_title)
        // What comes after the upcoming block (its start excludes itself), so the gap view previews
        // the next break/event the same way the active notification does.
        val following = selectUpcomingGlances(blocks, nextStartMinute)
        val decision = liveUpdateGateway.decide(
            title = if (redact) genericTitle else next.title.ifBlank { genericTitle },
            text = upNextNotificationText(nextStartMinute, following, redact),
            redactSensitiveTitles = false
        )
        val notification = liveUpdateGateway.build(
            channelId = ReminderNotificationChannels.CURRENT_BLOCK_CHANNEL_ID,
            decision = decision,
            timeLeftSeconds = (nextStartMinute - nowMinute).coerceAtLeast(0) * 60,
            totalSeconds = (nextStartMinute - gapStartMinute).coerceAtLeast(1) * 60,
            contentIntent = buildNotificationContentIntent(
                context = context,
                launch = NotificationLaunch(section = SECTION_DAY, dayTarget = DAY_TARGET_TODAY),
                requestCode = NOTIFICATION_ID
            ),
            onlyAlertOnce = true,
            subText = context.getString(R.string.current_block_upnext_subtext)
        )
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (ex: SecurityException) {
            Log.w(TAG, "Notification permission missing for up-next update", ex)
        }
    }

    private fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun scheduleNextBoundary(active: TimeBlock?, next: TimeBlock?, now: LocalDateTime) {
        val nowMinute = now.hour * 60 + now.minute
        val boundaryMinute = when {
            // No active block: wake when the up-next window opens (so a far-off block's countdown
            // appears on time), or at the block's start once we're already inside the window.
            active == null && next != null ->
                upNextRefreshMinute(next.startMinuteOfDay, UP_NEXT_LOOKAHEAD_MINUTES, nowMinute)
            else -> nextBlockBoundaryMinute(
                activeEndMinute = active?.let { it.startMinuteOfDay + it.durationMinutes },
                nextStartMinute = next?.startMinuteOfDay,
                nowMinute = nowMinute
            )
        }
        // While a live progress surface is actually on screen (an active block, or an in-window
        // up-next countdown), wake periodically before the boundary too, so the progress bar
        // advances instead of freezing between boundaries. Gated to "showing" states so a long
        // idle / pre-day stretch doesn't accrue wake-ups.
        val showingLive = active != null ||
            (next != null && shouldShowUpNext(next.startMinuteOfDay, UP_NEXT_LOOKAHEAD_MINUTES, nowMinute))
        val wakeMinute = nextRenderMinute(
            boundaryMinute = boundaryMinute,
            nowMinute = nowMinute,
            showingLive = showingLive,
            refreshIntervalMinutes = PROGRESS_REFRESH_INTERVAL_MINUTES
        )
        if (wakeMinute == null) {
            boundaryPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let { alarmManager?.cancel(it) }
            return
        }
        val triggerAt = now.toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(wakeMinute.toLong())
            .toInstant()
            .toEpochMilli()
        val pendingIntent = boundaryPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        // Inexact on purpose (OS-batched, battery-friendly): a passive status notification tolerates
        // a delivery window, and progress stays correct because it is time-derived at render. The
        // periodic mid-block wake is the same inexact alarm rescheduled — not a ticking service.
        alarmManager?.set(AlarmManager.RTC, triggerAt, pendingIntent)
    }

    private fun boundaryPendingIntent(creationFlag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            BOUNDARY_REQUEST_CODE,
            Intent(context, CurrentBlockBoundaryReceiver::class.java),
            creationFlag or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        const val TAG = "CurrentBlockNotif"
        const val PREFS_NAME = "chronos_current_block_notification"
        const val KEY_ENABLED = "enabled"
        // Expiry (epoch millis) until which the "now" notification yields to the focus live update.
        const val KEY_FOCUS_SUPPRESSED_UNTIL = "focus_suppressed_until"
        // Self-heal window: suppression auto-lifts this long after the last focus tick if the process
        // died before onFocusEnded(). Generously longer than any plausible focus session.
        const val FOCUS_SUPPRESS_CAP_MS = 6L * 60 * 60 * 1000
        // Mirror of core:data's privacy store (ChronosPreferencesDataSource / PrivacyPreferences) so we
        // can honour the redaction setting without depending on core:data.
        const val CHRONOS_PREFERENCES_NAME = "chronos_preferences"
        const val KEY_REDACT_NOTIFICATIONS = "privacy_redact_notifications"
        // Up-next only shows once the next block is within this many minutes (avoids an hours-long
        // pre-day / long-gap countdown).
        const val UP_NEXT_LOOKAHEAD_MINUTES = 120
        // While a block (or in-window up-next) is on screen, re-render at most this often so the
        // progress bar advances visibly between boundaries. Coarse on purpose: inexact, OS-batched
        // wakes — a few per active block, no foreground/ticking service.
        const val PROGRESS_REFRESH_INTERVAL_MINUTES = 10
        const val NOTIFICATION_ID = 4202
        const val BOUNDARY_REQUEST_CODE = 4203
        // Distinct from the focus content (4201) and phase-boundary (4203) request codes so the
        // "Start focus" action's PendingIntent never aliases theirs.
        const val START_FOCUS_REQUEST_CODE = 4250
    }
}

/** Receiver invoked at block boundaries to re-render the current-block notification. */
@AndroidEntryPoint
class CurrentBlockBoundaryReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: CurrentBlockNotificationCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                coordinator.refresh()
            } catch (ex: Exception) {
                Log.w("CurrentBlockNotif", "Boundary refresh failed", ex)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Picks the block to show as "current" and the upcoming one, ignoring all-day calendar
 * imports and blocks the user already completed (actual time logged) — a completed block
 * must not keep the ongoing notification alive just because its planned window is still open.
 */
internal fun selectCurrentAndNextBlock(
    blocks: List<TimeBlock>,
    nowMinute: Int
): Pair<TimeBlock?, TimeBlock?> {
    val eligible = blocks
        .filter { it.occupiesScheduleTime() && it.actualEndMinuteOfDay == null }
        .sortedBy { it.startMinuteOfDay }
    val active = eligible.firstOrNull { block ->
        nowMinute >= block.startMinuteOfDay &&
            nowMinute < block.startMinuteOfDay + block.durationMinutes
    }
    val next = eligible.firstOrNull { it.startMinuteOfDay > nowMinute }
    return active to next
}

internal fun nextBlockBoundaryMinute(
    activeEndMinute: Int?,
    nextStartMinute: Int?,
    nowMinute: Int
): Int? = listOfNotNull(activeEndMinute, nextStartMinute)
    .filter { it > nowMinute }
    .minOrNull()

/**
 * The minute to schedule the next render. Normally the [boundaryMinute] (block edge / up-next
 * window). When a live progress surface is on screen ([showingLive]), it's clamped to an earlier
 * periodic refresh ([nowMinute] + [refreshIntervalMinutes]) so the progress bar advances mid-block
 * instead of sitting frozen until the boundary. Null only when there's no boundary to wake for.
 */
internal fun nextRenderMinute(
    boundaryMinute: Int?,
    nowMinute: Int,
    showingLive: Boolean,
    refreshIntervalMinutes: Int
): Int? {
    if (boundaryMinute == null) return null
    if (!showingLive) return boundaryMinute
    return minOf(boundaryMinute, nowMinute + refreshIntervalMinutes)
}

// Break detection and focus eligibility live in the shared [BlockCategories] (core:domain) so the
// "now" notification and the Wear day-summary glance classify blocks identically — one source of
// truth, no drift. These thin aliases keep the call sites (and tests) in this file readable.
private fun TimeBlock.isBreakBlock(): Boolean = BlockCategories.isBreak(category)

/** @see BlockCategories.supportsFocus — break / sleep / meal are excluded; everything else keeps it. */
internal fun blockSupportsFocus(category: String): Boolean = BlockCategories.supportsFocus(category)

/** A break or event coming up later today, as the live "now" notification surfaces it. */
internal data class UpcomingGlance(val title: String, val startMinute: Int, val isBreak: Boolean)

/**
 * The next break and the next non-break ("event") block strictly after now, each as a lightweight
 * glance and returned chronologically. Mirrors [selectCurrentAndNextBlock]'s eligibility (occupies
 * schedule time, not already completed) so the notification and the boundary picker agree on which
 * blocks count. Surfacing the two kinds separately lets the live notification answer both "when's my
 * next break?" and "what's my next thing?" instead of only naming whichever block happens to be next.
 */
internal fun selectUpcomingGlances(blocks: List<TimeBlock>, nowMinute: Int): List<UpcomingGlance> {
    val upcoming = blocks
        .filter { it.occupiesScheduleTime() && it.actualEndMinuteOfDay == null }
        .filter { it.startMinuteOfDay > nowMinute }
        .sortedBy { it.startMinuteOfDay }
    val nextEvent = upcoming.firstOrNull { !it.isBreakBlock() }
    val nextBreak = upcoming.firstOrNull { it.isBreakBlock() }
    return listOfNotNull(
        nextEvent?.let { UpcomingGlance(it.title, it.startMinuteOfDay, isBreak = false) },
        nextBreak?.let { UpcomingGlance(it.title, it.startMinuteOfDay, isBreak = true) }
    ).sortedBy { it.startMinute }
}

/**
 * How many schedule-occupying, not-yet-completed blocks start strictly after now — the "N to go"
 * count for the notification header. Same eligibility as [selectUpcomingGlances] so the count and
 * the previewed items agree.
 */
internal fun upcomingBlockCount(blocks: List<TimeBlock>, nowMinute: Int): Int =
    blocks.count { it.occupiesScheduleTime() && it.actualEndMinuteOfDay == null && it.startMinuteOfDay > nowMinute }

/**
 * The active block's full time window, e.g. "2:00–2:30 PM" (the meridian is collapsed when both
 * ends share it, "11:30 AM–12:15 PM" otherwise). Shows when the block runs at a glance — more than
 * the bare end time — and uses absolute times so it never goes stale between boundary refreshes.
 */
internal fun currentBlockWindowText(startMinute: Int, endMinute: Int): String {
    val start = formatCurrentBlockMinute(startMinute)
    val end = formatCurrentBlockMinute(endMinute)
    // formatCurrentBlockMinute always ends in " AM"/" PM" (3 trailing chars) regardless of locale.
    val startLabel = if (start.takeLast(2) == end.takeLast(2)) start.dropLast(3) else start
    return "$startLabel–$end"
}

/**
 * Body for the live "now" notification: the active block's time window followed by the next event
 * and the next break (each labelled, in chronological order). A break shows just "Break at <time>"
 * unless it carries a custom title; an event shows "Next: <title> at <time>".
 */
internal fun currentBlockNotificationText(
    startMinute: Int,
    endMinute: Int,
    upcoming: List<UpcomingGlance>
): String {
    val window = currentBlockWindowText(startMinute, endMinute)
    if (upcoming.isEmpty()) return "$window · Last block of the day"
    val parts = upcoming.map { glance ->
        val label = if (glance.isBreak) "Break" else "Next"
        val time = formatCurrentBlockMinute(glance.startMinute)
        val name = glance.title.trim()
        // Drop a redundant name (a "Break" titled break, or a blank title) — keep custom labels.
        if (name.isEmpty() || name.equals(label, ignoreCase = true)) {
            "$label at $time"
        } else {
            "$label: $name at $time"
        }
    }
    return (listOf(window) + parts).joinToString(" · ")
}

/**
 * Privacy-redacted current-block body: the block's time window plus the next event's and next
 * break's start times only, with all titles dropped (times are not considered sensitive).
 */
internal fun redactedCurrentBlockText(
    startMinute: Int,
    endMinute: Int,
    upcoming: List<UpcomingGlance>
): String {
    val window = currentBlockWindowText(startMinute, endMinute)
    if (upcoming.isEmpty()) return "$window · Last block of the day"
    val parts = upcoming.map { glance ->
        val label = if (glance.isBreak) "Break" else "Next"
        "$label at ${formatCurrentBlockMinute(glance.startMinute)}"
    }
    return (listOf(window) + parts).joinToString(" · ")
}

/**
 * End minute of the most recent eligible block at/before now — the anchor for the up-next gap bar
 * (so the bar represents "how far through the gap we are"). Null when nothing has ended yet today.
 */
internal fun previousBlockEndMinute(blocks: List<TimeBlock>, nowMinute: Int): Int? =
    blocks
        .filter { it.occupiesScheduleTime() && it.actualEndMinuteOfDay == null }
        .map { it.startMinuteOfDay + it.durationMinutes }
        .filter { it <= nowMinute }
        .maxOrNull()

/**
 * Body for the up-next gap notification — the upcoming block's absolute start time (so it stays
 * accurate without ticking), optionally followed by the next break/event after it ("· Break at …"
 * / "· Then: <title> at …"). Under redaction the following item's title is dropped, times kept.
 */
internal fun upNextNotificationText(
    nextStartMinute: Int,
    following: List<UpcomingGlance>,
    redact: Boolean
): String {
    val base = "Starts at ${formatCurrentBlockMinute(nextStartMinute)}"
    val glance = following.firstOrNull() ?: return base
    val label = if (glance.isBreak) "Break" else "Then"
    val time = formatCurrentBlockMinute(glance.startMinute)
    val name = glance.title.trim()
    val tail = if (redact || name.isEmpty() || name.equals(label, ignoreCase = true)) {
        "$label at $time"
    } else {
        "$label: $name at $time"
    }
    return "$base · $tail"
}

/** The up-next countdown shows only once the next block is within the lookahead window. */
internal fun shouldShowUpNext(nextStartMinute: Int, lookaheadMinutes: Int, nowMinute: Int): Boolean =
    (nextStartMinute - nowMinute) in 0..lookaheadMinutes

/**
 * When no block is active, the minute to wake and re-render: the moment the up-next window opens
 * (`nextStart - lookahead`) if it's still ahead, otherwise the block's start (when it goes active).
 */
internal fun upNextRefreshMinute(nextStartMinute: Int, lookaheadMinutes: Int, nowMinute: Int): Int {
    val windowOpen = nextStartMinute - lookaheadMinutes
    return if (nowMinute < windowOpen) windowOpen else nextStartMinute
}

internal fun formatCurrentBlockMinute(minute: Int): String {
    val normalized = ((minute % 1440) + 1440) % 1440
    val hour = normalized / 60
    val m = normalized % 60
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val value = hour % 12) {
        0 -> 12
        else -> value
    }
    return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, m, suffix)
}
