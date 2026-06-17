package com.chronosflow.core.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the device can talk to a Health Connect provider right now. */
enum class HealthConnectAvailability {
    /** A provider is installed and usable. */
    AVAILABLE,

    /** A provider exists but needs updating before it can be used. */
    PROVIDER_UPDATE_REQUIRED,

    /** No provider on this device (e.g. unsupported OS or not installed). */
    NOT_SUPPORTED
}

/**
 * Result of pulling incremental sleep changes from Health Connect via a differential changes token.
 * Deletions only carry a record id (no date), so the importer treats any deletion as a signal to
 * reconcile the whole window rather than trying to resolve which night was removed.
 */
sealed interface SleepChangesResult {
    /**
     * Changes drained since the token was issued (possibly empty — the common idle case).
     * [upserted] are the inserted/updated sessions, [hasDeletions] flags that at least one session
     * was removed, and [nextToken] is the token to persist for the next pull.
     */
    data class Changes(
        val upserted: List<SleepSessionRecord>,
        val hasDeletions: Boolean,
        val nextToken: String
    ) : SleepChangesResult

    /** The token aged out (provider reset / too long between syncs); the importer must re-seed. */
    data object Expired : SleepChangesResult
}

/**
 * Thin wrapper over the Health Connect client for the read-only sleep importer. Keeps every
 * Health-Connect-typed call inside this class so the rest of the app (and the settings UI) only
 * deals in plain permission strings and domain models.
 */
@Singleton
class HealthConnectSleepDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** Grant required for the feature to function at all. */
    val requiredPermissions: Set<String> =
        setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    /**
     * Full set requested from the user: the sleep read plus background read, so the periodic
     * WorkManager job can pull sessions while the app is not in the foreground.
     */
    val requestPermissions: Set<String> =
        requiredPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED
            else -> HealthConnectAvailability.NOT_SUPPORTED
        }

    fun isAvailable(): Boolean = availability() == HealthConnectAvailability.AVAILABLE

    /** Contract the settings screen launches to request [requestPermissions]. */
    fun permissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    private fun clientOrNull(): HealthConnectClient? =
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    suspend fun grantedPermissions(): Set<String> =
        clientOrNull()?.permissionController?.getGrantedPermissions() ?: emptySet()

    /** True once the sleep read grant is in place; background read degrades gracefully without its own grant. */
    suspend fun hasSleepReadPermission(): Boolean =
        grantedPermissions().containsAll(requiredPermissions)

    /** Reads every sleep session whose time range overlaps [[start], [end]]. Empty when unavailable. */
    suspend fun readSessions(start: Instant, end: Instant): List<SleepSessionRecord> {
        val client = clientOrNull() ?: return emptyList()
        return client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        ).records
    }

    /** A fresh differential-changes token scoped to sleep sessions, or null when unavailable. */
    suspend fun changesToken(): String? =
        clientOrNull()?.getChangesToken(ChangesTokenRequest(setOf(SleepSessionRecord::class)))

    /**
     * Drains every page of sleep-session changes since [token]. Returns [SleepChangesResult.Expired]
     * if the token has aged out (the caller should re-seed), otherwise a [SleepChangesResult.Changes]
     * carrying the upserted sessions, whether anything was deleted, and the token to persist next.
     */
    suspend fun changesSince(token: String): SleepChangesResult {
        val client = clientOrNull() ?: return SleepChangesResult.Expired
        val upserted = mutableListOf<SleepSessionRecord>()
        var hasDeletions = false
        var cursor = token
        while (true) {
            val response = client.getChanges(cursor)
            if (response.changesTokenExpired) return SleepChangesResult.Expired
            for (change in response.changes) {
                when (change) {
                    is UpsertionChange -> (change.record as? SleepSessionRecord)?.let(upserted::add)
                    is DeletionChange -> hasDeletions = true
                }
            }
            cursor = response.nextChangesToken
            if (!response.hasMore) break
        }
        return SleepChangesResult.Changes(upserted, hasDeletions, cursor)
    }
}
