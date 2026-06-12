package com.chronosflow.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import com.chronosflow.wear.presentation.WearApp

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalAmbientModeManager provides rememberAmbientModeManager()) {
                WearApp()
            }
        }
    }
}
