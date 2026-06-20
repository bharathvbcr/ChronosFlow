package com.ChronosFlow.VBCR.security

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ChronosFlow.VBCR.core.data.security.AppLockSessionController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockLifecycleObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appLockSessionController: AppLockSessionController
) : DefaultLifecycleObserver {

    // Incremented each onStart within the same process. Zero only on cold start / after
    // process kill. We also persist a "last backgrounded" timestamp so that a process kill
    // followed by relaunch is still treated as a background-resume for lock evaluation.
    private var startedCount = 0

    // Cache the SharedPreferences instance at construction time so getSharedPreferences() (which
    // parses the XML file on first access) is not called on the main thread during onStart/onStop
    // (STARTUP-012). Hilt constructs this singleton during Application.onCreate() which is
    // already off the critical-path for the first Compose frame.
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val lastBackgroundedAt = prefs.getLong(KEY_LAST_BACKGROUNDED, -1L)
        val fromBackground = when {
            startedCount > 0 -> true                // within same process: definitely from background
            lastBackgroundedAt >= 0L -> true        // fresh process but app was previously running
            else -> false                            // genuine first cold start (new install)
        }
        startedCount++
        appLockSessionController.onAppForegrounded(fromBackground = fromBackground)
    }

    override fun onStop(owner: LifecycleOwner) {
        prefs.edit()
            .putLong(KEY_LAST_BACKGROUNDED, System.currentTimeMillis())
            .apply()
        appLockSessionController.onAppBackgrounded()
    }

    private companion object {
        const val PREFS_NAME = "app_lock_lifecycle"
        const val KEY_LAST_BACKGROUNDED = "last_backgrounded_at"
    }
}
