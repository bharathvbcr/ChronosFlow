package com.ChronosFlow.VBCR.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES_NAME = "chronos_alarm_scheduler"
private const val KEY_PENDING_IDS = "pending_alarm_ids"
private const val KEY_ALARM_PREFIX = "alarm_"
// Monotonically-increasing counter used to allocate collision-free PendingIntent request codes.
private const val KEY_REQUEST_CODE_COUNTER = "rc_counter"
// Prefix for persisted id→requestCode mappings so the same alarm always reuses the same code.
private const val KEY_RC_PREFIX = "rc_"
private const val DEGRADED_WINDOW_MILLIS = 10 * 60 * 1000L

/** Android 12+ allows 500 concurrent alarms; stay below that for headroom. */
internal const val MAX_PERSISTED_ALARMS = 450

private data class PersistedReminder(
    val id: String,
    val timeEpochMillis: Long,
    val title: String,
    val message: String,
    val exactRequested: Boolean
)

sealed class AlarmScheduleResult {
    data class Scheduled(val id: String, val exact: Boolean) : AlarmScheduleResult()
    data class ExactDenied(val id: String) : AlarmScheduleResult()
    data class PermissionDenied(val id: String) : AlarmScheduleResult()
    data class Skipped(val id: String, val reason: String) : AlarmScheduleResult()
}

internal enum class AlarmSchedulePath {
    EXACT,
    INEXACT_FALLBACK,
    EXACT_DENIED,
    PERMISSION_DENIED,
    PAST_TIME
}

internal fun resolveAlarmSchedulePath(
    canPostReminders: Boolean,
    isFutureTime: Boolean,
    canScheduleExactAlarms: Boolean,
    allowFallback: Boolean
): AlarmSchedulePath {
    if (!canPostReminders) return AlarmSchedulePath.PERMISSION_DENIED
    if (!isFutureTime) return AlarmSchedulePath.PAST_TIME
    if (canScheduleExactAlarms) return AlarmSchedulePath.EXACT
    return if (allowFallback) AlarmSchedulePath.INEXACT_FALLBACK else AlarmSchedulePath.EXACT_DENIED
}

internal data class ExactAlarmSettingsIntentSpec(
    val action: String,
    val data: String? = null
)

internal fun buildExactAlarmSettingsIntentSpecs(
    packageName: String,
    sdkInt: Int
): List<ExactAlarmSettingsIntentSpec> {
    if (sdkInt < 31) return emptyList()

    val packageUri = "package:$packageName"

    return listOf(
        // Android's current guidance uses a plain exact-alarm settings intent.
        // Some OEM settings apps reject package-scoped variants and crash.
        ExactAlarmSettingsIntentSpec(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
        ExactAlarmSettingsIntentSpec(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    )
}

@Singleton
open class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val pending = ConcurrentHashMap<String, AlarmScheduleResult>()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val bootReceiverComponent = ComponentName(context.applicationContext, ReminderBootReceiver::class.java)

    private fun skipIfFoldedIntoLiveActivity(
        id: String,
        request: AlarmRequest? = null
    ): AlarmScheduleResult? {
        if (!ReminderFoldScheduling.skipsSeparateRemindersWhenFolded(context)) return null
        val foldable = request?.let(ReminderFoldScheduling::isSeparateFoldableReminder)
            ?: ReminderFoldScheduling.isSeparateFoldableReminderAlarmId(id)
        return if (foldable) {
            recordSkipped(id, FOLDED_REMINDER_SKIP_REASON)
        } else {
            null
        }
    }

    fun scheduleExactAlarm(
        id: String,
        time: Instant,
        title: String,
        message: String,
        allowFallback: Boolean = true
    ): AlarmScheduleResult {
        if (!hasSchedulingCapacity()) {
            return recordSkipped(id, "Maximum scheduled reminder capacity reached")
        }
        skipIfFoldedIntoLiveActivity(id)?.let { return it }
        val canUseExact = canScheduleExactAlarms()
        val result: AlarmScheduleResult = when (
            resolveAlarmSchedulePath(
                canPostReminders = canPostReminders(),
                isFutureTime = time.toEpochMilli() > System.currentTimeMillis(),
                canScheduleExactAlarms = canUseExact,
                allowFallback = allowFallback
            )
        ) {
            AlarmSchedulePath.PERMISSION_DENIED -> AlarmScheduleResult.PermissionDenied(id)
            AlarmSchedulePath.PAST_TIME -> AlarmScheduleResult.Skipped(id, "Reminder time is not in the future")
            AlarmSchedulePath.EXACT_DENIED -> AlarmScheduleResult.ExactDenied(id)
            AlarmSchedulePath.EXACT -> {
                if (scheduleExact(time, id, title, message)) {
                    AlarmScheduleResult.Scheduled(id, exact = true)
                } else if (allowFallback &&
                    canPostReminders() &&
                    time.toEpochMilli() > System.currentTimeMillis() &&
                    scheduleInexact(time, id, title, message)
                ) {
                    // The permission check passed but setExactAndAllowWhileIdle still threw
                    // SecurityException — the user (or system) revoked SCHEDULE_EXACT_ALARM in
                    // between. With fallback allowed, degrade to an inexact alarm instead of
                    // silently dropping a reminder the caller asked to schedule.
                    AlarmScheduleResult.Scheduled(id, exact = false)
                } else {
                    AlarmScheduleResult.ExactDenied(id)
                }
            }
            AlarmSchedulePath.INEXACT_FALLBACK -> {
                if (scheduleInexact(time, id, title, message)) {
                    AlarmScheduleResult.Scheduled(id, exact = false)
                } else {
                    AlarmScheduleResult.ExactDenied(id)
                }
            }
        }

        pending[id] = result
        when (result) {
            is AlarmScheduleResult.Scheduled -> {
                // Persist what actually happened, not what the pre-flight check predicted: a
                // mid-schedule revocation degrades this reminder to inexact and boot-restore
                // must reconcile it as such.
                val scheduledExact = result.exact
                persistReminder(PersistedReminder(id, time.toEpochMilli(), title, message, scheduledExact))
                setBootReceiverEnabled(true)
            }

            else -> clearReminder(id)
        }
        return result
    }

    fun scheduleInexactAlarm(
        id: String,
        time: Instant,
        title: String,
        message: String
    ): AlarmScheduleResult {
        if (!hasSchedulingCapacity()) {
            return recordSkipped(id, "Maximum scheduled reminder capacity reached")
        }
        skipIfFoldedIntoLiveActivity(id)?.let { return it }
        if (!canPostReminders()) {
            val result = AlarmScheduleResult.PermissionDenied(id)
            pending[id] = result
            return result
        }

        if (time.toEpochMilli() <= System.currentTimeMillis()) {
            val result = AlarmScheduleResult.Skipped(id, "Reminder time is not in the future")
            pending[id] = result
            return result
        }

        val result = if (scheduleInexact(time, id, title, message)) {
            AlarmScheduleResult.Scheduled(id, exact = false)
        } else {
            AlarmScheduleResult.PermissionDenied(id)
        }

        pending[id] = result
        when (result) {
            is AlarmScheduleResult.Scheduled -> {
                persistReminder(PersistedReminder(id, time.toEpochMilli(), title, message, exactRequested = false))
                setBootReceiverEnabled(true)
            }
            else -> clearReminder(id)
        }
        return result
    }

    /**
     * Schedules a reminder for a persisted [AlarmRequest].
     *
     * @param respectFoldSkip when true (default), foldable reminder types are skipped while folded
     *   into the live "now" surface — the normal dedup for imminent reminders. Explicit
     *   user-requested future wakes (a medication snooze) must pass false: skipping would silently
     *   drop an alarm the user just asked for, leaving nothing to fire at the snooze target time.
     */
    fun scheduleAlarmRequest(
        request: AlarmRequest,
        respectFoldSkip: Boolean = true
    ): AlarmScheduleResult {
        if (respectFoldSkip) {
            skipIfFoldedIntoLiveActivity(request.id, request)?.let { return it }
        }
        return scheduleAlarm(
            id = request.id,
            time = request.scheduledFor,
            title = request.title,
            message = request.message,
            receiverClass = receiverClassFor(request.type),
            allowFallback = true
        )
    }

    private fun scheduleAlarm(
        id: String,
        time: Instant,
        title: String,
        message: String,
        receiverClass: Class<out AlarmReceiver>,
        allowFallback: Boolean
    ): AlarmScheduleResult {
        if (!hasSchedulingCapacity()) {
            return recordSkipped(id, "Maximum scheduled reminder capacity reached")
        }
        val canUseExact = canScheduleExactAlarms()
        val result: AlarmScheduleResult = when (
            resolveAlarmSchedulePath(
                canPostReminders = canPostReminders(),
                isFutureTime = time.toEpochMilli() > System.currentTimeMillis(),
                canScheduleExactAlarms = canUseExact,
                allowFallback = allowFallback
            )
        ) {
            AlarmSchedulePath.PERMISSION_DENIED -> AlarmScheduleResult.PermissionDenied(id)
            AlarmSchedulePath.PAST_TIME -> AlarmScheduleResult.Skipped(id, "Reminder time is not in the future")
            AlarmSchedulePath.EXACT_DENIED -> AlarmScheduleResult.ExactDenied(id)
            AlarmSchedulePath.EXACT -> {
                if (scheduleExact(time, id, title, message, receiverClass)) {
                    AlarmScheduleResult.Scheduled(id, exact = true)
                } else if (
                    allowFallback &&
                    canPostReminders() &&
                    time.toEpochMilli() > System.currentTimeMillis() &&
                    scheduleInexact(time, id, title, message, receiverClass)
                ) {
                    // Exact permission was revoked between the check above and the set call;
                    // degrade to inexact instead of dropping a schedulable reminder.
                    AlarmScheduleResult.Scheduled(id, exact = false)
                } else {
                    AlarmScheduleResult.ExactDenied(id)
                }
            }
            AlarmSchedulePath.INEXACT_FALLBACK -> {
                if (scheduleInexact(time, id, title, message, receiverClass)) {
                    AlarmScheduleResult.Scheduled(id, exact = false)
                } else {
                    AlarmScheduleResult.ExactDenied(id)
                }
            }
        }

        pending[id] = result
        when (result) {
            is AlarmScheduleResult.Scheduled -> {
                // Persist what actually happened, not what the pre-flight check predicted.
                persistReminder(
                    PersistedReminder(id, time.toEpochMilli(), title, message, result.exact)
                )
                setBootReceiverEnabled(true)
            }

            else -> clearReminder(id)
        }
        return result
    }

    private fun scheduleExactAlarmFromPersisted(reminder: PersistedReminder): AlarmScheduleResult {
        if (reminder.timeEpochMillis <= System.currentTimeMillis()) {
            clearReminder(reminder.id)
            return AlarmScheduleResult.Skipped(
                reminder.id,
                "Reminder already expired; dropped from restore queue"
            )
        }

        return scheduleExactAlarm(
            id = reminder.id,
            time = Instant.ofEpochMilli(reminder.timeEpochMillis),
            title = reminder.title,
            message = reminder.message,
            allowFallback = true
        )
    }

    fun pruneExpiredPersistedReminders(): Int {
        var pruned = 0
        loadPersistedReminders().forEach { reminder ->
            if (reminder.timeEpochMillis <= System.currentTimeMillis()) {
                clearReminder(reminder.id)
                pending.remove(reminder.id)
                pruned++
            }
        }
        if (!hasPersistedReminders()) {
            setBootReceiverEnabled(false)
        }
        return pruned
    }

    fun hasSchedulingCapacity(): Boolean = activeReminderCount() < MAX_PERSISTED_ALARMS

    fun restoreScheduledAlarmsAfterReboot(skipIds: Set<String> = emptySet()): Map<String, AlarmScheduleResult> {
        pruneExpiredPersistedReminders()
        if (!hasPersistedReminders()) {
            setBootReceiverEnabled(false)
            return emptyMap()
        }

        val results = linkedMapOf<String, AlarmScheduleResult>()
        loadPersistedReminders().filterNot { it.id in skipIds }.forEach { reminder ->
            if (results.containsKey(reminder.id)) return@forEach
            val result = if (reminder.timeEpochMillis <= System.currentTimeMillis()) {
                clearReminder(reminder.id)
                AlarmScheduleResult.Skipped(reminder.id, "Reminder already expired; dropped from restore queue")
            } else {
                scheduleExactAlarmFromPersisted(reminder)
            }
            results[reminder.id] = result
        }

        setBootReceiverEnabled(hasPersistedReminders())
        return results.toMap()
    }

    fun canPostReminders(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 31) alarmManager.canScheduleExactAlarms() else true
        } catch (_: SecurityException) {
            false
        }
    }

    internal fun hasPendingAlarm(id: String): Boolean = pending[id] is AlarmScheduleResult.Scheduled

    internal fun activeReminderCount(): Int = getPersistedIds().size

    internal fun hasPersistedReminders(): Boolean = getPersistedIds().isNotEmpty()

    fun cancelAlarm(id: String) {
        val rc = requestCodeFor(id)
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            rc,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
        val medicationIntent = Intent(context, MedicationAlarmReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            rc,
            medicationIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { alarmManager.cancel(it) }
        clearReminder(id)
        releaseRequestCode(id)
        pending.remove(id)
        setBootReceiverEnabled(hasPersistedReminders())
    }

    /**
     * Cancels every scheduled alarm persisted by this scheduler. Used by the "Delete all my data"
     * flow to remove all pending PendingIntents from the AlarmManager before the database is wiped.
     */
    /** Persisted alarm ids (for fold-mode reconciliation and diagnostics). */
    internal fun getPersistedAlarmIds(): Set<String> = getPersistedIds().toSet()

    fun cancelAllAlarms() {
        getPersistedIds().toSet().forEach { id -> cancelAlarm(id) }
        // Also clear the entire alarm SharedPreferences file so no stale IDs or request-code
        // mappings survive.
        preferences.edit().clear().apply()
        pending.clear()
        setBootReceiverEnabled(false)
    }

    fun routeToExactAlarmSetting() {
        buildExactAlarmSettingsIntentSpecs(
            packageName = context.packageName,
            sdkInt = Build.VERSION.SDK_INT
        ).forEach { spec ->
            val intent = Intent(spec.action).apply {
                spec.data?.let { data = Uri.parse(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (ex: Exception) {
                Log.w(
                    "AlarmScheduler",
                    "Failed to open exact-alarm settings via ${intent.action}",
                    ex
                )
            }
        }
        Log.w("AlarmScheduler", "No activity handles exact-alarm settings intents")
    }

    // internal+open (not private) so tests can simulate the check-vs-set permission race.
    internal open fun scheduleExact(
        time: Instant,
        id: String,
        title: String,
        message: String,
        receiverClass: Class<out AlarmReceiver> = AlarmReceiver::class.java
    ): Boolean {
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                time.toEpochMilli(),
                buildPendingIntent(id, title, message, receiverClass)
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    internal open fun scheduleInexact(
        time: Instant,
        id: String,
        title: String,
        message: String,
        receiverClass: Class<out AlarmReceiver> = AlarmReceiver::class.java
    ): Boolean {
        return try {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                time.toEpochMilli(),
                DEGRADED_WINDOW_MILLIS,
                buildPendingIntent(id, title, message, receiverClass)
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildPendingIntent(
        id: String,
        title: String,
        message: String,
        receiverClass: Class<out AlarmReceiver>
    ): PendingIntent {
        val intent = Intent(context, receiverClass).apply {
            putExtra("EXTRA_ID", id)
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_MESSAGE", message)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Returns the stable PendingIntent request code for [id]. If the id has no code yet, allocates
     * a new one from a persisted monotonic counter so codes are unique and survive process restarts.
     * Using String.hashCode() is avoided because its 32-bit range causes birthday-paradox collisions
     * among the up to [MAX_PERSISTED_ALARMS] concurrent alarms (TS-010).
     */
    @Synchronized
    private fun requestCodeFor(id: String): Int {
        val rcKey = KEY_RC_PREFIX + id
        val existing = preferences.getInt(rcKey, -1)
        if (existing != -1) return existing
        val next = preferences.getInt(KEY_REQUEST_CODE_COUNTER, 1)
        preferences.edit()
            .putInt(rcKey, next)
            .putInt(KEY_REQUEST_CODE_COUNTER, next + 1)
            .apply()
        return next
    }

    /**
     * Frees the persisted request-code mapping for [id] after the alarm is cancelled, reclaiming
     * the slot in the counter registry so it is not allocated again.
     */
    @Synchronized
    private fun releaseRequestCode(id: String) {
        preferences.edit().remove(KEY_RC_PREFIX + id).apply()
    }

    // Serialises all read-modify-write operations on the persisted ID set so two concurrent
    // callers (e.g. AlarmDeliveryCoordinator on one WorkManager thread and
    // SyncRecurringTaskAlarmsUseCase on another) cannot each read the same base set and silently
    // overwrite each other's addition (TS-005). Uses the same JVM monitor as requestCodeFor so
    // the two operations on preferences are coherently ordered.
    @Synchronized
    private fun persistReminder(reminder: PersistedReminder) {
        val ids = getPersistedIds().toMutableSet()
        ids.add(reminder.id)
        val payload = listOf(
            "id=${urlEncode(reminder.id)}",
            "time=${reminder.timeEpochMillis}",
            "title=${urlEncode(reminder.title)}",
            "message=${urlEncode(reminder.message)}",
            "exact=${reminder.exactRequested}"
        ).joinToString("|")

        preferences.edit()
            .putStringSet(KEY_PENDING_IDS, ids)
            .putString(KEY_ALARM_PREFIX + reminder.id, payload)
            .apply()
    }

    @Synchronized
    private fun clearReminder(id: String) {
        val ids = getPersistedIds().toMutableSet()
        ids.remove(id)
        preferences.edit()
            .putStringSet(KEY_PENDING_IDS, ids)
            .remove(KEY_ALARM_PREFIX + id)
            .apply()
    }

    private fun getPersistedIds(): Set<String> {
        return preferences.getStringSet(KEY_PENDING_IDS, emptySet()) ?: emptySet()
    }

    private fun loadPersistedReminders(): List<PersistedReminder> {
        return getPersistedIds().mapNotNull { id ->
            parsePersistedReminder(preferences.getString("$KEY_ALARM_PREFIX$id", null))
        }
    }

    private fun parsePersistedReminder(raw: String?): PersistedReminder? {
        if (raw.isNullOrBlank()) return null
        val tokens = raw.split("|").mapNotNull { token ->
            val idx = token.indexOf("=")
            if (idx <= 0) return@mapNotNull null
            token.substring(0, idx) to token.substring(idx + 1)
        }.toMap()

        val id = tokens["id"]?.let { urlDecode(it) } ?: return null
        val time = tokens["time"]?.toLongOrNull() ?: return null
        val title = tokens["title"]?.let { urlDecode(it) } ?: return null
        val message = tokens["message"]?.let { urlDecode(it) } ?: return null
        val exact = tokens["exact"]?.toBoolean() ?: false

        return PersistedReminder(id, time, title, message, exact)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.toString())

    private fun urlDecode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.toString())

    private fun receiverClassFor(type: AlarmRequestType): Class<out AlarmReceiver> {
        return when (type) {
            AlarmRequestType.MEDICATION -> MedicationAlarmReceiver::class.java
            AlarmRequestType.FOCUS_BLOCK,
            AlarmRequestType.BLOCK_START,
            AlarmRequestType.DAILY_REVIEW,
            AlarmRequestType.LOG_REMINDER,
            AlarmRequestType.URGENT_TASK,
            AlarmRequestType.READING_REMINDER -> AlarmReceiver::class.java
        }
    }

    private fun recordSkipped(id: String, reason: String): AlarmScheduleResult {
        val result = AlarmScheduleResult.Skipped(id, reason)
        pending[id] = result
        clearReminder(id)
        return result
    }

    private fun setBootReceiverEnabled(isEnabled: Boolean) {
        // Never disable once enabled: alarms persisted in the AlarmRequest database
        // (not just the legacy SharedPreferences set) still need BOOT_COMPLETED,
        // TIME_SET, TIMEZONE_CHANGED, and SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        // handling after the legacy reminder set drains.
        if (!isEnabled) return
        try {
            context.packageManager.setComponentEnabledSetting(
                bootReceiverComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (ex: Exception) {
            Log.w("AlarmScheduler", "Failed to update ReminderBootReceiver state", ex)
        }
    }
}
