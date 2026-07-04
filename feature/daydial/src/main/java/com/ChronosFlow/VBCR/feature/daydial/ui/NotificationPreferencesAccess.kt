package com.ChronosFlow.VBCR.feature.daydial.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.ChronosFlow.VBCR.core.domain.model.ProactiveDigestKeys
import kotlinx.coroutines.flow.drop

/** iOS-compatible keys in the shared `chronos_preferences` store (see [FoldToggles.fromPreferences]). */
object NotificationPreferenceKeys {
    const val REMINDERS = "notif.reminders"
    const val MEDICATION = "notif.medication"
    const val TASKS = "notif.tasks"
    const val HABITS = "notif.habits"
    /** Cross-platform current-block live surface toggle (iOS `notif.currentBlockLive`). */
    const val CURRENT_BLOCK_LIVE = "notif.currentBlockLive"
    /** Cross-platform fold-into-live toggle (iOS `notif.foldReminders`). */
    const val FOLD_REMINDERS = "notif.foldReminders"
    /** Cross-platform focus live-surface toggle (iOS `notif.focusLiveActivity`). */
    const val FOCUS_LIVE_ACTIVITY = "notif.focusLiveActivity"
}

/**
 * Reads/writes a boolean in `chronos_preferences` — the same store iOS `ChronosSettings` and
 * [com.ChronosFlow.VBCR.core.notifications.FoldedReminderResolver] use for per-kind reminder toggles.
 */
@Composable
internal fun rememberChronosPreferenceBoolean(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) {
        context.getSharedPreferences(ProactiveDigestKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    val state = remember(key, context) {
        mutableStateOf(prefs.getBoolean(key, defaultValue))
    }
    LaunchedEffect(key, prefs) {
        snapshotFlow { state.value }
            .drop(1)
            .collect { newValue -> prefs.edit().putBoolean(key, newValue).apply() }
    }
    return state
}

private const val DAYDIAL_UI_PREFS_NAME = "daydial_ui_settings"

/**
 * Cross-platform notification toggle: persists to `chronos_preferences` under [iosKey] (iOS parity)
 * and mirrors to [legacyUiKey] in `daydial_ui_settings` on write. On first read, migrates a legacy
 * DayDial-only value into the shared store when the iOS key is absent.
 */
@Composable
internal fun rememberNotifCrossPlatformBoolean(
    iosKey: String,
    legacyUiKey: String,
    defaultValue: Boolean
): MutableState<Boolean> {
    val context = LocalContext.current.applicationContext
    val chronosPrefs = remember(context) {
        context.getSharedPreferences(ProactiveDigestKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    val uiPrefs = remember(context) {
        context.getSharedPreferences(DAYDIAL_UI_PREFS_NAME, Context.MODE_PRIVATE)
    }
    val state = remember(iosKey, context) {
        mutableStateOf(
            when {
                chronosPrefs.contains(iosKey) -> chronosPrefs.getBoolean(iosKey, defaultValue)
                uiPrefs.contains(legacyUiKey) -> {
                    val legacy = uiPrefs.getBoolean(legacyUiKey, defaultValue)
                    chronosPrefs.edit().putBoolean(iosKey, legacy).apply()
                    legacy
                }
                else -> defaultValue
            }
        )
    }
    LaunchedEffect(iosKey, chronosPrefs, uiPrefs) {
        snapshotFlow { state.value }
            .drop(1)
            .collect { newValue ->
                chronosPrefs.edit().putBoolean(iosKey, newValue).apply()
                uiPrefs.edit().putBoolean(legacyUiKey, newValue).apply()
            }
    }
    return state
}
