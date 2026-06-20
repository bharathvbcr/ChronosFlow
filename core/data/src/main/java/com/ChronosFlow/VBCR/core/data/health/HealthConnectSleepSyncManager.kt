package com.ChronosFlow.VBCR.core.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import com.ChronosFlow.VBCR.core.domain.model.SleepSource
import com.ChronosFlow.VBCR.core.domain.model.SleepTrack
import com.ChronosFlow.VBCR.core.domain.repository.SleepTrackRepository
import com.ChronosFlow.VBCR.core.domain.usecase.RecordSleepUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings + last-run status for the Health Connect sleep importer, shaped for the UI. */
data class HealthConnectSleepSyncStatus(
    val availability: HealthConnectAvailability,
    val enabled: Boolean,
    val lastResult: String
) {
    val isAvailable: Boolean get() = availability == HealthConnectAvailability.AVAILABLE
}

sealed interface HealthConnectSleepSyncOutcome {
    data class Success(val importedCount: Int) : HealthConnectSleepSyncOutcome

    /** Nothing to do (unavailable / not permitted); retrying won't help. */
    data class Skipped(val reason: String) : HealthConnectSleepSyncOutcome

    /** Transient read error; the worker should retry. */
    data class Failure(val message: String) : HealthConnectSleepSyncOutcome
}

/**
 * Read-only importer that pulls recent Health Connect sleep sessions into the local sleep log on a
 * periodic WorkManager schedule. Provenance keeps it safe: it creates or refreshes only
 * [SleepSource.HEALTH_CONNECT] rows and never touches a [SleepSource.MANUAL] night the user typed
 * or edited.
 */
@Singleton
class HealthConnectSleepSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: HealthConnectSleepDataSource,
    private val sleepTrackRepository: SleepTrackRepository,
    private val recordSleepUseCase: RecordSleepUseCase,
    private val preferences: ChronosPreferencesDataSource
) {
    /** Permission strings the settings screen requests via [permissionRequestContract]. */
    val requestPermissions: Set<String> get() = dataSource.requestPermissions

    fun permissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        dataSource.permissionRequestContract()

    fun managePermissionsIntent() = dataSource.managePermissionsIntent()

    suspend fun hasSleepReadPermission(): Boolean = dataSource.hasSleepReadPermission()

    fun status(): HealthConnectSleepSyncStatus = HealthConnectSleepSyncStatus(
        availability = dataSource.availability(),
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        lastResult = preferences.getString(KEY_LAST_RESULT, "")
    )

    fun setEnabled(enabled: Boolean) {
        preferences.putBoolean(KEY_ENABLED, enabled)
        if (enabled) schedule() else cancel()
    }

    /** Re-arm the periodic worker if importing was left enabled (call once on app startup). */
    fun ensureScheduled() {
        if (preferences.getBoolean(KEY_ENABLED, false)) schedule()
    }

    suspend fun runSync(): HealthConnectSleepSyncOutcome {
        if (!dataSource.isAvailable()) return recordSkipped("Health Connect is not available")
        if (!dataSource.hasSleepReadPermission()) return recordSkipped("Sleep access not granted yet")
        return withContext(Dispatchers.IO) {
            runCatching {
                val zone = ZoneId.systemDefault()
                val token = preferences.getString(KEY_CHANGES_TOKEN, "")
                if (token.isEmpty()) {
                    // First run (or a forced re-seed): no token yet, so reconcile the whole window
                    // and arm a token so every later cycle can pull only what changed.
                    seedFromWindow(zone)
                } else {
                    when (val changes = dataSource.changesSince(token)) {
                        // Token aged out — fall back to a full reconcile and re-arm.
                        is SleepChangesResult.Expired -> seedFromWindow(zone)
                        is SleepChangesResult.Changes -> {
                            // Idle cycles (no inserts, no deletes) touch nothing — the whole point of
                            // going incremental. Any change at all triggers a window reconcile, which
                            // recomputes affected nights and drops imported rows HC no longer has.
                            val applied = if (changes.upserted.isNotEmpty() || changes.hasDeletions) {
                                reconcileWindow(zone)
                            } else {
                                0
                            }
                            preferences.putString(KEY_CHANGES_TOKEN, changes.nextToken)
                            applied
                        }
                    }
                }
            }.fold(
                onSuccess = { count -> recordSuccess(count) },
                onFailure = { error ->
                    // A SecurityException means the read was rejected (e.g. background-read was not
                    // granted, or access was revoked) — retrying won't help, so fail rather than churn.
                    if (error is SecurityException) {
                        recordFailure("Health Connect access was denied")
                    } else {
                        recordFailure(error.message ?: "Sleep sync failed")
                    }
                }
            )
        }
    }

    /** Reconcile the lookback window, then arm a fresh changes token for incremental pulls. */
    private suspend fun seedFromWindow(zone: ZoneId): Int {
        val applied = reconcileWindow(zone)
        // Read first, token second: any change racing the read is replayed on the next pull, and the
        // reconcile is idempotent, so nothing is lost. A null token (provider gone) clears the key so
        // the next run re-seeds instead of pulling against a stale token.
        dataSource.changesToken()?.let { preferences.putString(KEY_CHANGES_TOKEN, it) }
            ?: preferences.remove(KEY_CHANGES_TOKEN)
        return applied
    }

    /**
     * Recompute every night in the lookback window from Health Connect and converge the local log:
     * write the nights whose measured fields actually changed, and delete imported rows the window no
     * longer covers (a session removed upstream). Returns the number of rows written or deleted.
     */
    private suspend fun reconcileWindow(zone: ZoneId): Int {
        val now = Instant.now()
        val windowStart = now.minus(SYNC_LOOKBACK)
        val incoming = dataSource.readSessions(windowStart, now).toSleepTracks(zone)
        var applied = 0
        for (track in incoming) {
            val existing = sleepTrackRepository.getByDate(track.date)
            val merged = mergeImported(track, existing) ?: continue
            // Skip the upsert when nothing measured changed — keeps idle reconciles from churning the
            // DB and re-emitting flows. The use case backfills the planned bed/wake snapshot for a new
            // night, exactly like a hand-logged one.
            if (merged != existing) {
                recordSleepUseCase(merged)
                applied++
            }
        }
        applied += sweepDeletedRows(incoming, windowStart, now, zone)
        return applied
    }

    /** Delete imported rows in the window that no longer map to any Health Connect session. */
    private suspend fun sweepDeletedRows(
        incoming: List<SleepTrack>,
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId
    ): Int {
        val present = incoming.mapTo(mutableSetOf()) { it.date }
        val startDate = windowStart.atZone(zone).toLocalDate()
        val endDate = windowEnd.atZone(zone).toLocalDate()
        var deleted = 0
        for (row in sleepTrackRepository.getForDateRange(startDate, endDate)) {
            // Only ever drop our own imported rows; a MANUAL night is the user's and is left alone.
            if (row.source == SleepSource.HEALTH_CONNECT && row.date !in present) {
                sleepTrackRepository.delete(row.id)
                deleted++
            }
        }
        return deleted
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<HealthConnectSleepSyncWorker>(
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

    private fun recordSuccess(count: Int): HealthConnectSleepSyncOutcome {
        preferences.putString(
            KEY_LAST_RESULT,
            if (count == 0) "Up to date with Health Connect" else "Synced $count night(s) from Health Connect"
        )
        return HealthConnectSleepSyncOutcome.Success(count)
    }

    private fun recordSkipped(reason: String): HealthConnectSleepSyncOutcome {
        preferences.putString(KEY_LAST_RESULT, reason)
        return HealthConnectSleepSyncOutcome.Skipped(reason)
    }

    private fun recordFailure(message: String): HealthConnectSleepSyncOutcome {
        preferences.putString(KEY_LAST_RESULT, "Last sync failed: $message")
        return HealthConnectSleepSyncOutcome.Failure(message)
    }

    companion object {
        /**
         * Provenance-aware merge for one imported night against the existing row, if any:
         * - no row yet → write the imported track as-is;
         * - existing [SleepSource.HEALTH_CONNECT] row → refresh its measured fields but keep its id
         *   and anything already attached (planned bed/wake snapshot, quality, notes);
         * - existing [SleepSource.MANUAL] row → return null; a user-owned night is never overwritten.
         */
        internal fun mergeImported(incoming: SleepTrack, existing: SleepTrack?): SleepTrack? = when {
            existing == null -> incoming
            existing.source == SleepSource.HEALTH_CONNECT -> incoming.copy(
                id = existing.id,
                plannedStartMinute = existing.plannedStartMinute,
                plannedEndMinute = existing.plannedEndMinute,
                sleepQuality = existing.sleepQuality,
                windDownNotes = existing.windDownNotes
            )
            else -> null
        }

        const val UNIQUE_WORK_NAME = "chronosflow_health_connect_sleep_sync"
        private const val KEY_ENABLED = "health_connect.sleep_sync.enabled"
        private const val KEY_LAST_RESULT = "health_connect.sleep_sync.last_result"
        private const val KEY_CHANGES_TOKEN = "health_connect.sleep_sync.changes_token"
        private const val SYNC_INTERVAL_HOURS = 6L

        // Window for full reconciles (first run, token expiry, or any change batch). Wider than the
        // old 3 days because reconciles are now rare — incremental pulls carry the steady state — so
        // the cost buys resilience to multi-day gaps when the device was off or the worker throttled.
        private val SYNC_LOOKBACK: Duration = Duration.ofDays(7)
    }
}

/** Periodic background entry point; delegates to the manager via a Hilt entry point. */
class HealthConnectSleepSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            HealthConnectSleepSyncEntryPoint::class.java
        )
        val manager = entryPoint.healthConnectSleepSyncManager()
        val dataSource = entryPoint.healthConnectSleepDataSource()

        if (!dataSource.hasBackgroundReadPermission()) {
            // Permission not yet granted — return success so the periodic work stays alive and
            // retries at the next scheduled interval. Result.failure() would mark this period
            // as permanently failed, but runAttemptCount is always 0 for periodic workers
            // (it only increments on Result.retry()), so the >5 guard was dead code here.
            android.util.Log.w("HealthConnectSleepSync", "Background read permission not granted — deferring sleep sync")
            return Result.success()
        }

        return when (manager.runSync()) {
            is HealthConnectSleepSyncOutcome.Success -> Result.success()
            is HealthConnectSleepSyncOutcome.Skipped -> Result.success()
            is HealthConnectSleepSyncOutcome.Failure -> Result.failure()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HealthConnectSleepSyncEntryPoint {
    fun healthConnectSleepSyncManager(): HealthConnectSleepSyncManager
    fun healthConnectSleepDataSource(): HealthConnectSleepDataSource
}
