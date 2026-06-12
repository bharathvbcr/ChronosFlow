package com.chronosflow.core.ui.bubble

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.chronosflow.MainActivity
import com.chronosflow.core.ui.components.ChronosBackground
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.settings.resolveChronosDarkTheme
import com.chronosflow.core.ui.theme.ChronosTheme
import com.chronosflow.feature.daydial.DayDialScreen
import com.chronosflow.core.notifications.EXTRA_INITIAL_SECTION
import com.chronosflow.core.notifications.SECTION_DAY
import com.chronosflow.core.notifications.SECTION_FOCUS
import com.chronosflow.core.notifications.SECTION_MEDICATION
import com.chronosflow.core.notifications.SECTION_REVIEW
import com.chronosflow.core.notifications.SECTION_TASKS
import com.chronosflow.navigation.SECTION_GOALS
import com.chronosflow.navigation.SECTION_HABITS
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BubbleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiSettings = rememberChronosUiSettings()
            ChronosTheme(
                darkTheme = resolveChronosDarkTheme(uiSettings.appearanceMode),
                dynamicColor = uiSettings.dynamicColorEnabled,
                highContrastEnabled = uiSettings.highContrastEnabled,
                reducedMotion = uiSettings.reduceMotionEnabled
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChronosBackground(modifier = Modifier.fillMaxSize()) {
                        DayDialScreen(
                            onOpenFocusScreen = { openMainSection(SECTION_FOCUS) },
                            onOpenTasks = { openMainSection(SECTION_TASKS) },
                            onOpenHabits = { openMainSection(SECTION_HABITS) },
                            onOpenGoals = { openMainSection(SECTION_GOALS) },
                            onOpenMedication = { openMainSection(SECTION_MEDICATION) },
                            onOpenReview = { openMainSection(SECTION_REVIEW) },
                            onSelectPrimaryTab = {}
                        )
                    }
                }
            }
        }
    }

    private fun openMainSection(section: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_SECTION, section)
            }
        )
    }
}
