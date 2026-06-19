package com.ChronosFlow.VBCR.core.data.usage

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import com.ChronosFlow.VBCR.core.domain.model.AppUsageDay
import com.ChronosFlow.VBCR.core.domain.model.AppUsageSample
import com.ChronosFlow.VBCR.core.domain.diagnostics.AppEventCategory
import com.ChronosFlow.VBCR.core.domain.diagnostics.AppEventLog
import com.ChronosFlow.VBCR.core.domain.model.DistractionNudge
import com.ChronosFlow.VBCR.core.domain.model.UsageCategory
import com.ChronosFlow.VBCR.core.domain.model.distractionNudge
import com.ChronosFlow.VBCR.core.domain.notifications.ScreenTimeNudgePresenter
import com.ChronosFlow.VBCR.core.domain.repository.AppUsageOverrideRepository
import com.ChronosFlow.VBCR.core.domain.repository.AppUsageRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Whether usage access is granted, whether the user opted in, and the last sync result + time. */
data class ScreenTimeSyncStatus(
    val hasAccess: Boolean,
    val enabled: Boolean,
    val lastResult: String,
    /** Epoch millis of the last successful sync, or null if it has never completed one. */
    val lastSyncedAtMillis: Long?
)

sealed interface ScreenTimeSyncOutcome {
    /** Days written to the local store. */
    data class Success(val daysSynced: Int) : ScreenTimeSyncOutcome

    /** Nothing to do (access not granted); retrying won't help until the user grants it. */
    data class Skipped(val reason: String) : ScreenTimeSyncOutcome

    /** Transient read error; a periodic worker should retry. */
    data class Failure(val message: String) : ScreenTimeSyncOutcome
}

/**
 * Read-only importer that pulls recent per-app screen time from [UsageStatsDataSource], classifies
 * each app as productive / distracting / neutral, and stores the daily aggregate so Insights can
 * show "focused vs. wasted time". Mirrors the Health Connect sleep sync: an opt-in toggle, an
 * on-demand [runSync], and a last-result message persisted in preferences.
 */
@Singleton
class ScreenTimeSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: UsageStatsDataSource,
    private val repository: AppUsageRepository,
    private val overrideRepository: AppUsageOverrideRepository,
    private val preferences: ChronosPreferencesDataSource,
    private val appEventLog: AppEventLog
) {
    fun status(): ScreenTimeSyncStatus = ScreenTimeSyncStatus(
        hasAccess = dataSource.hasUsageAccess(),
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        lastResult = preferences.getString(KEY_LAST_RESULT, ""),
        lastSyncedAtMillis = preferences.getLong(KEY_LAST_SYNC_AT, 0L).takeIf { it > 0L }
    )

    fun usageAccessSettingsIntent(): Intent = dataSource.usageAccessSettingsIntent()

    fun setEnabled(enabled: Boolean) {
        preferences.putBoolean(KEY_ENABLED, enabled)
        if (enabled) schedule() else cancel()
    }

    /** Daily focused-minutes target (0 = no goal). Stored as Long since prefs has no Int accessor. */
    fun focusGoalMinutes(): Int = preferences.getLong(KEY_FOCUS_GOAL, DEFAULT_FOCUS_GOAL_MINUTES).toInt()

    fun setFocusGoal(minutes: Int) {
        preferences.putLong(KEY_FOCUS_GOAL, minutes.coerceAtLeast(0).toLong())
    }

    /** Reactive focus goal, so the Insights tab tracks changes made in the settings card. */
    fun observeFocusGoalMinutes(): Flow<Int> =
        preferences.observeLong(KEY_FOCUS_GOAL, DEFAULT_FOCUS_GOAL_MINUTES).map { it.toInt() }

    fun nudgeNotificationsEnabled(): Boolean = preferences.getBoolean(KEY_NUDGE_NOTIFICATIONS, false)

    fun setNudgeNotificationsEnabled(enabled: Boolean) {
        preferences.putBoolean(KEY_NUDGE_NOTIFICATIONS, enabled)
    }

    /**
     * Returns a distraction nudge to post (and marks the day so we nudge at most once daily), or null
     * when nudges are off, already shown today, or today isn't notably above the usual. Called by the
     * background worker after a sync.
     */
    suspend fun consumePendingDistractionNudge(today: LocalDate = LocalDate.now()): DistractionNudge? {
        if (!nudgeNotificationsEnabled()) return null
        if (preferences.getString(KEY_LAST_NUDGE_DATE, "") == today.toString()) return null
        val window = repository.getForDateRange(today.minusDays((NUDGE_WINDOW_DAYS - 1).toLong()), today)
        val nudge = distractionNudge(window, today) ?: return null
        preferences.putString(KEY_LAST_NUDGE_DATE, today.toString())
        return nudge
    }

    /**
     * Read-only check (no side effects, ignores the nudge-notification opt-in) of whether today's
     * distracting time runs notably above the user's usual level. Used to nudge the AI day-planner
     * toward protecting focus. False when access is missing or there's no baseline yet.
     */
    suspend fun isDistractionAboveUsualToday(today: LocalDate = LocalDate.now()): Boolean {
        if (!dataSource.hasUsageAccess()) return false
        val window = repository.getForDateRange(today.minusDays((NUDGE_WINDOW_DAYS - 1).toLong()), today)
        return distractionNudge(window, today) != null
    }

    /**
     * Reactive top distracting apps over the trailing [windowDays], recomputed whenever a re-tag
     * changes which apps count as distracting. Empty when access is missing. Computed live from
     * usage stats (per-app totals aren't persisted).
     */
    fun observeTopDistractingApps(
        windowDays: Int = SYNC_WINDOW_DAYS,
        today: LocalDate = LocalDate.now(),
        limit: Int = TOP_DISTRACTING_LIMIT
    ): Flow<List<AppUsageSample>> =
        overrideRepository.observeOverrides().map { overrides ->
            withContext(Dispatchers.IO) {
                if (!dataSource.hasUsageAccess()) return@withContext emptyList()
                val totals = LinkedHashMap<String, Int>()
                val labels = HashMap<String, String>()
                for (offset in 0 until windowDays) {
                    val date = today.minusDays(offset.toLong())
                    for (sample in dataSource.usageForDay(date)) {
                        if ((overrides[sample.packageName] ?: sample.category) != UsageCategory.DISTRACTING) continue
                        totals[sample.packageName] = (totals[sample.packageName] ?: 0) + sample.minutes
                        labels[sample.packageName] = sample.label
                    }
                }
                totals.entries.sortedByDescending { it.value }.take(limit).map { (pkg, minutes) ->
                    AppUsageSample(pkg, labels[pkg] ?: pkg, UsageCategory.DISTRACTING, minutes)
                }
            }
        }

    /** Re-arm the periodic worker if sync was left enabled (call once on app startup). */
    fun ensureScheduled() {
        if (preferences.getBoolean(KEY_ENABLED, false)) schedule()
    }

    /** Live top-apps breakdown for [date] (not persisted). Empty when access is missing. */
    suspend fun breakdownForDay(date: LocalDate): List<AppUsageSample> =
        withContext(Dispatchers.IO) { dataSource.usageForDay(date) }

    /**
     * Pulls and stores the daily category aggregate for the trailing [days] window ending [today].
     * Days with no system data (older than the device retains) are skipped, but today is always
     * written so the headline metric refreshes even when the device is fresh.
     */
    suspend fun runSync(today: LocalDate = LocalDate.now(), days: Int = SYNC_WINDOW_DAYS): ScreenTimeSyncOutcome {
        if (!dataSource.hasUsageAccess()) {
            return recordSkipped("Usage access not granted yet")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val overrides = overrideRepository.getOverrides()
                var synced = 0
                for (offset in 0 until days) {
                    val date = today.minusDays(offset.toLong())
                    val samples = dataSource.usageForDay(date)
                    if (samples.isEmpty() && offset > 0) continue
                    repository.upsert(aggregate(date, samples, overrides))
                    synced++
                }
                recordSuccess(synced)
            }.getOrElse { error ->
                recordFailure(error.message ?: error::class.java.simpleName)
            }
        }
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<ScreenTimeSyncWorker>(
            SYNC_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun aggregate(
        date: LocalDate,
        samples: List<AppUsageSample>,
        overrides: Map<String, UsageCategory>
    ): AppUsageDay {
        var productive = 0
        var distracting = 0
        var neutral = 0
        for (sample in samples) {
            // A user reclassification wins over the system-derived category.
            when (overrides[sample.packageName] ?: sample.category) {
                UsageCategory.PRODUCTIVE -> productive += sample.minutes
                UsageCategory.DISTRACTING -> distracting += sample.minutes
                UsageCategory.NEUTRAL -> neutral += sample.minutes
            }
        }
        return AppUsageDay(date, productive, distracting, neutral)
    }

    private fun recordSuccess(days: Int): ScreenTimeSyncOutcome {
        val message = if (days == 0) "No screen-time data yet" else "Synced $days day(s)"
        preferences.putString(KEY_LAST_RESULT, message)
        preferences.putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
        appEventLog.record(AppEventCategory.SYNC, "Screen time: $message")
        return ScreenTimeSyncOutcome.Success(days)
    }

    private fun recordSkipped(reason: String): ScreenTimeSyncOutcome {
        preferences.putString(KEY_LAST_RESULT, reason)
        appEventLog.record(AppEventCategory.SYNC, "Screen time skipped: $reason")
        return ScreenTimeSyncOutcome.Skipped(reason)
    }

    private fun recordFailure(message: String): ScreenTimeSyncOutcome {
        preferences.putString(KEY_LAST_RESULT, "Sync failed: $message")
        appEventLog.record(AppEventCategory.ERROR, "Screen time sync failed: $message")
        return ScreenTimeSyncOutcome.Failure(message)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "chronosflow_screen_time_sync"
        private const val SYNC_WINDOW_DAYS = 7
        private const val SYNC_INTERVAL_HOURS = 6L
        private const val KEY_ENABLED = "screen_time.sync.enabled"
        private const val KEY_LAST_RESULT = "screen_time.sync.last_result"
        private const val KEY_LAST_SYNC_AT = "screen_time.sync.last_sync_at"
        private const val KEY_FOCUS_GOAL = "screen_time.focus_goal_minutes"
        private const val DEFAULT_FOCUS_GOAL_MINUTES = 240L
        private const val TOP_DISTRACTING_LIMIT = 5
        private const val KEY_NUDGE_NOTIFICATIONS = "screen_time.nudge_notifications"
        private const val KEY_LAST_NUDGE_DATE = "screen_time.last_nudge_date"
        private const val NUDGE_WINDOW_DAYS = 14
    }
}

/** Periodic background entry point; delegates to the manager via a Hilt entry point. */
class ScreenTimeSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ScreenTimeSyncEntryPoint::class.java
        )
        val manager = entryPoint.screenTimeSyncManager()
        val outcome = manager.runSync()

        // After a fresh sync, optionally surface the gentle distraction nudge (opt-in, once/day).
        runCatching {
            manager.consumePendingDistractionNudge()?.let { nudge ->
                entryPoint.screenTimeNudgePresenter()
                    .notifyDistraction(nudge.todayDistractingMinutes, nudge.averageDistractingMinutes)
            }
        }

        return when (outcome) {
            is ScreenTimeSyncOutcome.Success -> Result.success()
            is ScreenTimeSyncOutcome.Skipped -> Result.success()
            is ScreenTimeSyncOutcome.Failure -> Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScreenTimeSyncEntryPoint {
    fun screenTimeSyncManager(): ScreenTimeSyncManager
    fun screenTimeNudgePresenter(): ScreenTimeNudgePresenter
}
