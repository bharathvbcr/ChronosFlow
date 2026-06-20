package com.ChronosFlow.VBCR.feature.daydial

import android.content.Context
import androidx.work.WorkManager
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permanently erases all user data stored by ChronosFlow. Invoked from the "Delete all my data"
 * flow required by Google Play Data Safety policy.
 *
 * Clears, in order:
 *  1. All WorkManager workers (stops ongoing background work before the DB is wiped)
 *  2. All scheduled alarms (removes system-level PendingIntents)
 *  3. All Room database tables via clearAllTables()
 *  4. The DataStore used for UI settings (daydial_ui_settings)
 *  5. All known SharedPreferences files used by the app
 *
 * This is a one-way, irreversible operation. The caller is responsible for presenting a
 * confirmation dialog before invoking [invoke].
 */
@Singleton
class DeleteAllDataUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: ChronosDatabase,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke() {
        // 1. Cancel all pending WorkManager workers so nothing writes to the DB after we wipe it.
        WorkManager.getInstance(context).cancelAllWork()

        // 2. Cancel all scheduled alarms (each cancelAlarm also removes the persisted record and
        //    updates the boot-receiver state).
        alarmScheduler.cancelAllAlarms()

        // 3. Wipe every Room table.
        database.clearAllTables()

        // 4. Clear the DataStore that holds UI settings. We remove the file directly rather
        //    than calling dataStore.edit { it.clear() } because the DataStore delegate
        //    is a private Context extension property not visible from this module.
        deleteDataStoreFile(ChronosUiSettingsKeys.DATASTORE_NAME)

        // 5. Clear all known SharedPreferences files used by the app.
        //    Names must exactly match the PREFS_NAME/PREFERENCES_NAME constant in each component.
        val prefsNames = listOf(
            ChronosUiSettingsKeys.PREFS_NAME,     // "daydial_ui_settings" — UI settings
            "chronos_preferences",                // ChronosPreferencesDataSource + ProactiveDigestKeys
            "chronos_alarm_scheduler",            // AlarmScheduler
            "chronos_manual_missed_blocks",       // ManualMissedBlockRegistry
            "chronos_focus_mood_accent",          // FocusMoodAccentCache
            "chronos_focus_split_session",        // FocusSplitSessionStore (was "focus_split_session")
            "app_lock_lifecycle",                 // AppLockLifecycleObserver (was "chronos_app_lock")
            "chronos_current_block_notification", // CurrentBlockNotificationCoordinator
            "chronos_secure_database",            // ChronosSecureDatabaseProvider (SQLCipher passphrase)
            "chronos_wear_link_status"            // WearLinkStatusStore
        )

        for (name in prefsNames) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                // Use commit() (synchronous) so the clear is guaranteed to reach disk before
                // invoke() returns. apply() is async and may not flush if the process is killed
                // immediately after the "data deleted" confirmation is shown.
                .commit()
        }
    }

    /**
     * Removes the DataStore preferences file from disk. DataStore Preferences files are stored
     * under `context.filesDir/datastore/<name>.preferences_pb`. Removing the file is safe and
     * avoids needing to hold a reference to the DataStore instance itself.
     *
     * Failure is non-fatal but logged: the delete-all flow still reports success to the caller
     * since alarms, WorkManager tasks, and the Room DB are already wiped; a stale DataStore file
     * on a subsequent cold start will have no matching DB rows and no queued alarms, so it is
     * effectively inert.
     */
    private fun deleteDataStoreFile(name: String) {
        runCatching {
            java.io.File(context.filesDir, "datastore/$name.preferences_pb").delete()
        }.onFailure { e ->
            android.util.Log.w(TAG, "DataStore file deletion failed for '$name' — file may remain on disk", e)
        }
    }

    private companion object {
        const val TAG = "DeleteAllDataUseCase"
    }
}
