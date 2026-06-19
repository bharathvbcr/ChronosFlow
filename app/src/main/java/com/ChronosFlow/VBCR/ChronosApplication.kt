package com.ChronosFlow.VBCR

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appfunctions.service.AppFunctionConfiguration
import androidx.work.Configuration
import com.ChronosFlow.VBCR.appfunctions.ChronosAppFunctions
import com.ChronosFlow.VBCR.assist.ProactiveAssistForegroundRefresher
import com.ChronosFlow.VBCR.core.data.backup.ChronosAutoBackupManager
import com.ChronosFlow.VBCR.core.data.backup.ChronosPortableBackupInitializer
import com.ChronosFlow.VBCR.core.data.health.HealthConnectSleepSyncManager
import com.ChronosFlow.VBCR.core.data.usage.ScreenTimeSyncManager
import com.ChronosFlow.VBCR.core.data.sync.CalendarBackgroundSyncManager
import com.ChronosFlow.VBCR.core.notifications.AlarmCapabilityRefresher
import com.ChronosFlow.VBCR.core.notifications.FocusNotificationManager
import com.ChronosFlow.VBCR.core.notifications.ReminderNotificationChannels
import com.ChronosFlow.VBCR.core.notifications.ReminderReconcileScheduler
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsCache
import com.ChronosFlow.VBCR.widget.WidgetBackgroundSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal const val APPLICATION_STARTUP_WORK_DEFER_MILLIS = 60_000L
internal const val NOTIFICATION_CHANNEL_SETUP_DEFER_MILLIS = 5_000L

@HiltAndroidApp
class ChronosApplication : Application(), AppFunctionConfiguration.Provider, Configuration.Provider {
    @Inject lateinit var alarmCapabilityRefresher: AlarmCapabilityRefresher
    @Inject lateinit var chronosAppFunctions: Provider<ChronosAppFunctions>
    @Inject lateinit var portableBackupInitializer: Provider<ChronosPortableBackupInitializer>
    @Inject lateinit var autoBackupManager: Provider<ChronosAutoBackupManager>
    @Inject lateinit var healthConnectSleepSyncManager: Provider<HealthConnectSleepSyncManager>
    @Inject lateinit var screenTimeSyncManager: Provider<ScreenTimeSyncManager>
    @Inject lateinit var calendarBackgroundSyncManager: Provider<CalendarBackgroundSyncManager>
    @Inject lateinit var proactiveAssistForegroundRefresher: Provider<ProactiveAssistForegroundRefresher>

    private val startupHandler = Handler(Looper.getMainLooper())

    // Long-lived scope for process-wide background work that must outlive any single screen.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(ChronosAppFunctions::class.java) {
                chronosAppFunctions.get()
            }
            .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Seed the UI-settings cache once so Compose reads never block on DataStore (no startup flash).
        applicationScope.launch { ChronosUiSettingsCache.keepFresh(this@ChronosApplication) }
        WidgetBackgroundSync.register(this)
        scheduleDeferredNotificationChannelSetup()
        scheduleDeferredStartupWork()
    }

    private fun scheduleDeferredNotificationChannelSetup() {
        startupHandler.postDelayed(
            ::ensureNotificationChannels,
            NOTIFICATION_CHANNEL_SETUP_DEFER_MILLIS
        )
    }

    private fun ensureNotificationChannels() {
        ReminderNotificationChannels.ensureCreated(this)
        FocusNotificationManager.createFocusNotificationChannel(
            this,
            getSystemService(NotificationManager::class.java)
        )
    }

    private fun scheduleDeferredStartupWork() {
        startupHandler.postDelayed(
            {
                alarmCapabilityRefresher.register()
                ReminderReconcileScheduler.enqueue(this)
                portableBackupInitializer.get().start()
                autoBackupManager.get().ensureScheduled()
                healthConnectSleepSyncManager.get().ensureScheduled()
                screenTimeSyncManager.get().ensureScheduled()
                calendarBackgroundSyncManager.get().ensureScheduled()
                proactiveAssistForegroundRefresher.get().register()
                setupProfiling()
                monitorProcessExitHealth()
            },
            APPLICATION_STARTUP_WORK_DEFER_MILLIS
        )
    }

    private fun setupProfiling() {
        if (Build.VERSION.SDK_INT >= 36) {
            Log.i("ChronosFlow", "Profiling hooks are available on this Android version.")
        }
    }

    private fun monitorProcessExitHealth() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val manager = getSystemService(Application.ACTIVITY_SERVICE) as ActivityManager
                val exits = manager.getHistoricalProcessExitReasons(
                    packageName,
                    0,
                    0
                )
                Log.i("ChronosFlow", "Recovered ${exits.size} historical process exit records.")
            } catch (ex: Exception) {
                Log.w("ChronosFlow", "ApplicationExitInfo probe unavailable: ${ex.message}")
            }
        }
    }
}
