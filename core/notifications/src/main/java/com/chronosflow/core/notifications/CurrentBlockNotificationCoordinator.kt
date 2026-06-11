package com.chronosflow.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
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

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    suspend fun setEnabled(enabled: Boolean) {
        if (isEnabled() == enabled) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            refresh()
        } else {
            dismiss()
        }
    }

    suspend fun refresh(now: LocalDateTime = LocalDateTime.now()) {
        if (!isEnabled()) {
            dismiss()
            return
        }
        val blocks = try {
            timeBlockRepository.getTimeBlocksByDate(now.toLocalDate()).first()
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to load blocks for current-block notification", ex)
            emptyList()
        }
            .filter { it.occupiesScheduleTime() }
            .sortedBy { it.startMinuteOfDay }
        val nowMinute = now.hour * 60 + now.minute
        val active = blocks.firstOrNull { block ->
            nowMinute >= block.startMinuteOfDay &&
                nowMinute < block.startMinuteOfDay + block.durationMinutes
        }
        val next = blocks.firstOrNull { it.startMinuteOfDay > nowMinute }

        if (active != null && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            postNotification(active, next, nowMinute)
        } else {
            cancelNotification()
        }
        scheduleNextBoundary(active, next, now)
    }

    private fun dismiss() {
        cancelNotification()
        boundaryPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let { alarmManager?.cancel(it) }
    }

    private fun postNotification(active: TimeBlock, next: TimeBlock?, nowMinute: Int) {
        ReminderNotificationChannels.ensureCreated(context)
        val text = currentBlockNotificationText(
            endMinute = active.startMinuteOfDay + active.durationMinutes,
            nextTitle = next?.title,
            nextStartMinute = next?.startMinuteOfDay
        )
        val decision = liveUpdateGateway.decide(
            title = active.title.ifBlank { "Current block" },
            text = text,
            redactSensitiveTitles = false
        )
        val endMinute = active.startMinuteOfDay + active.durationMinutes
        val plannedEndAt = LocalDateTime.now()
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(endMinute.toLong())
            .toInstant()
        val notification = liveUpdateGateway.build(
            channelId = ReminderNotificationChannels.CURRENT_BLOCK_CHANNEL_ID,
            decision = decision,
            timeLeftSeconds = (endMinute - nowMinute).coerceAtLeast(0) * 60,
            totalSeconds = active.durationMinutes * 60,
            plannedEndAt = plannedEndAt
        )
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (ex: SecurityException) {
            Log.w(TAG, "Notification permission missing for current-block update", ex)
        }
    }

    private fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun scheduleNextBoundary(active: TimeBlock?, next: TimeBlock?, now: LocalDateTime) {
        val nowMinute = now.hour * 60 + now.minute
        val boundaryMinute = nextBlockBoundaryMinute(
            activeEndMinute = active?.let { it.startMinuteOfDay + it.durationMinutes },
            nextStartMinute = next?.startMinuteOfDay,
            nowMinute = nowMinute
        )
        if (boundaryMinute == null) {
            boundaryPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let { alarmManager?.cancel(it) }
            return
        }
        val triggerAt = now.toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(boundaryMinute.toLong())
            .toInstant()
            .toEpochMilli()
        val pendingIntent = boundaryPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        // Inexact on purpose: a passive status notification tolerates a delivery
        // window, and the progress bar stays correct because it is time-derived.
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
        const val NOTIFICATION_ID = 4202
        const val BOUNDARY_REQUEST_CODE = 4203
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

internal fun nextBlockBoundaryMinute(
    activeEndMinute: Int?,
    nextStartMinute: Int?,
    nowMinute: Int
): Int? = listOfNotNull(activeEndMinute, nextStartMinute)
    .filter { it > nowMinute }
    .minOrNull()

internal fun currentBlockNotificationText(
    endMinute: Int,
    nextTitle: String?,
    nextStartMinute: Int?
): String {
    val until = "Until ${formatCurrentBlockMinute(endMinute)}"
    return if (nextTitle != null && nextStartMinute != null) {
        "$until · Next: $nextTitle at ${formatCurrentBlockMinute(nextStartMinute)}"
    } else {
        "$until · Last block of the day"
    }
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
