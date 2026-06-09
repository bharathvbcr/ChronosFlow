package com.chronosflow

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appfunctions.service.AppFunctionConfiguration
import androidx.work.Configuration
import com.chronosflow.appfunctions.ChronosAppFunctions
import com.chronosflow.core.data.backup.ChronosPortableBackupInitializer
import com.chronosflow.core.notifications.AlarmCapabilityRefresher
import com.chronosflow.core.notifications.FocusNotificationManager
import com.chronosflow.core.notifications.ReminderNotificationChannels
import com.chronosflow.core.notifications.ReminderReconcileScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

internal const val APPLICATION_STARTUP_WORK_DEFER_MILLIS = 60_000L
internal const val NOTIFICATION_CHANNEL_SETUP_DEFER_MILLIS = 5_000L

@HiltAndroidApp
class ChronosApplication : Application(), AppFunctionConfiguration.Provider, Configuration.Provider {
    @Inject lateinit var alarmCapabilityRefresher: AlarmCapabilityRefresher
    @Inject lateinit var chronosAppFunctions: Provider<ChronosAppFunctions>
    @Inject lateinit var portableBackupInitializer: Provider<ChronosPortableBackupInitializer>

    private val startupHandler = Handler(Looper.getMainLooper())

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
