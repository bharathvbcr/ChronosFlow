package com.ChronosFlow.VBCR.core.data.health

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One Health Connect exercise session, flattened to the few fields the journal needs to surface it as
 * a timed "point". [recordId] is the Health Connect record id, used to build a stable journal-entry id
 * so a re-import updates the same point instead of duplicating it.
 */
data class ImportedWorkout(
    val recordId: String,
    val date: LocalDate,
    val startMinuteOfDay: Int?,
    val durationMinutes: Int,
    val label: String
)

/**
 * Read-only wrapper over the Health Connect client for importing exercise sessions. Mirrors
 * [HealthConnectSleepDataSource]: every Health-Connect-typed call stays inside this class so the rest
 * of the app deals only in plain permission strings and the lightweight [ImportedWorkout] model.
 */
@Singleton
class HealthConnectWorkoutDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val requiredPermissions: Set<String> =
        setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class))

    // TODO: HealthConnectWorkoutSyncWorker not yet implemented.
    // PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND is pre-declared here for the planned
    // background import worker. Remove from requestPermissions until the worker is added.
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

    fun permissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    fun managePermissionsIntent(): Intent =
        HealthConnectClient.getHealthConnectManageDataIntent(context, HEALTH_CONNECT_PROVIDER_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun clientOrNull(): HealthConnectClient? =
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    suspend fun grantedPermissions(): Set<String> =
        clientOrNull()?.permissionController?.getGrantedPermissions() ?: emptySet()

    suspend fun hasWorkoutReadPermission(): Boolean =
        grantedPermissions().containsAll(requiredPermissions)

    /** Exercise sessions overlapping [[start], [end]], flattened to [ImportedWorkout]. Empty when unavailable. */
    suspend fun readWorkouts(start: Instant, end: Instant, zoneId: ZoneId = ZoneId.systemDefault()): List<ImportedWorkout> {
        val client = clientOrNull() ?: return emptyList()
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records.map { it.toImportedWorkout(zoneId) }
        } catch (e: SecurityException) {
            android.util.Log.w(TAG, "Health Connect workout read permission revoked mid-call", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "HealthConnectWorkoutDataSource"
        const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}

/** Flatten an exercise session to an [ImportedWorkout], keyed by the local date it started. */
internal fun ExerciseSessionRecord.toImportedWorkout(zoneId: ZoneId): ImportedWorkout {
    val zone = startZoneOffset ?: zoneId
    val localStart = startTime.atZone(zone)
    val durationMinutes = Duration.between(startTime, endTime).toMinutes().toInt().coerceAtLeast(0)
    val name = title?.takeIf { it.isNotBlank() } ?: exerciseTypeLabel(exerciseType)
    val label = if (durationMinutes > 0) "$name · ${durationMinutes}m" else name
    return ImportedWorkout(
        recordId = metadata.id,
        date = localStart.toLocalDate(),
        startMinuteOfDay = localStart.toLocalTime().let { it.hour * 60 + it.minute },
        durationMinutes = durationMinutes,
        label = "🏃 $label"
    )
}

/** Human label for the common Health Connect exercise types; unknown types fall back to "Workout". */
internal fun exerciseTypeLabel(exerciseType: Int): String = when (exerciseType) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Run"
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Cycling"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swim"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Strength training"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
    ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
    else -> "Workout"
}
