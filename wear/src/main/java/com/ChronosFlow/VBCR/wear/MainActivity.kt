package com.ChronosFlow.VBCR.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import com.ChronosFlow.VBCR.wear.presentation.WearApp
import com.ChronosFlow.VBCR.wear.presentation.WearStartPage

/**
 * The watch app surface: a glanceable, action-oriented Wear OS Compose Material3 experience.
 *
 * It mirrors the phone's day as horizontally swipeable pages (Now / Habits / Tasks / Medication)
 * and — unlike a read-only companion — lets the wearer act: start/pause/stop focus, check off a
 * habit, complete a task, or acknowledge a dose, all routed back to the phone over the Data
 * Layer. The single [AppScaffold] lives inside [WearApp] per the Wear Compose contract.
 *
 * The ambient-mode manager is provided here, at the top of the hierarchy, so the Focus screen
 * can keep a dimmed countdown visible while the wrist is down.
 */
class MainActivity : ComponentActivity() {

    // The page a tile (or deep link) asked for, plus a nonce bumped on every intent. The activity
    // is launchMode=singleTask, so a tile tap on the already-running app arrives via onNewIntent
    // rather than a fresh onCreate; holding this as observable state lets WearApp re-route in place
    // (and the nonce makes re-tapping the same tile after manual navigation still re-route).
    private val startPage = mutableStateOf<String?>(null)
    private val routeNonce = mutableIntStateOf(0)

    // First-launch notification opt-in. POST_NOTIFICATIONS is a runtime permission on Wear OS 4+
    // (API 33+); without the grant the focus-session ongoing activity mirrored from the phone is
    // silently dropped by the system. The result needs no handling — once allowed, the next
    // mirrored session simply posts.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyIntent(intent)
        maybeRequestNotificationPermission()
        setContent {
            CompositionLocalProvider(LocalAmbientModeManager provides rememberAmbientModeManager()) {
                WearApp(startPage = startPage.value, routeNonce = routeNonce.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent?) {
        startPage.value = intent?.getStringExtra(WearStartPage.EXTRA)
        routeNonce.intValue++
    }

    /**
     * Ask for notification permission exactly once, on the first launch where it isn't already
     * granted. Pre-33 it's granted at install time (no-op); recording that we've asked means a
     * denial isn't re-prompted on every cold start.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val PREFS_NAME = "chronos_wear_prefs"
        const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "post_notifications_requested"
    }
}
