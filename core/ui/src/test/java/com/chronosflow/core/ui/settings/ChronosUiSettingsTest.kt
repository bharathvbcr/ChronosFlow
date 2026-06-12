package com.chronosflow.core.ui.settings

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChronosUiSettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(ChronosUiSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runTest {
            context.clearChronosUiSettingsStore()
        }
    }

    @Test
    fun `snapshot reads persisted ui settings`() {
        val prefs = context.getSharedPreferences(ChronosUiSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(ChronosUiSettingsKeys.KEY_DYNAMIC_COLOR, false)
            .putBoolean(ChronosUiSettingsKeys.KEY_REDUCE_MOTION, true)
            .putBoolean(ChronosUiSettingsKeys.KEY_GLASS_SURFACES, false)
            .putBoolean(ChronosUiSettingsKeys.KEY_HIGH_CONTRAST, true)
            .putBoolean(ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED, true)
            .putBoolean(ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED, true)
            .putBoolean(ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED, true)
            .putBoolean(ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED, true)
            .putString(ChronosUiSettingsKeys.KEY_APPEARANCE_MODE, ChronosUiSettingsKeys.APPEARANCE_DARK)
            .putString(ChronosUiSettingsKeys.KEY_BACKDROP_THEME, ChronosBackdropTheme.SMOKE.name)
            .commit()

        val snapshot = readChronosUiSettingsSnapshot(prefs)

        assertFalse(snapshot.dynamicColorEnabled)
        assertTrue(snapshot.reduceMotionEnabled)
        assertFalse(snapshot.glassSurfacesEnabled)
        assertTrue(snapshot.highContrastEnabled)
        assertTrue(snapshot.featureFlags.habitsEnabled)
        assertTrue(snapshot.featureFlags.medicationEnabled)
        assertTrue(snapshot.featureFlags.reviewEnabled)
        assertTrue(snapshot.featureFlags.aiAdvisorEnabled)
        assertEquals(ChronosUiSettingsKeys.APPEARANCE_DARK, snapshot.appearanceMode)
        assertEquals(ChronosBackdropTheme.SMOKE, snapshot.backdropTheme)
    }

    @Test
    fun `feature flag defaults enable all graduated features`() {
        val flags = ChronosFeatureFlags()

        assertTrue(flags.habitsEnabled)
        assertTrue(flags.medicationEnabled)
        assertTrue(flags.reviewEnabled)
        assertTrue(flags.aiAdvisorEnabled)
        assertTrue(flags.goalsEnabled)
    }

    @Test
    fun `graduated features default to enabled`() = runTest {
        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertTrue(snapshot.featureFlags.habitsEnabled)
        assertTrue(snapshot.featureFlags.medicationEnabled)
        assertTrue(snapshot.featureFlags.reviewEnabled)
        assertTrue(snapshot.featureFlags.aiAdvisorEnabled)
        assertTrue(snapshot.featureFlags.goalsEnabled)
        assertEquals(ChronosBackdropTheme.LIQUID, snapshot.backdropTheme)
    }

    @Test
    fun `companion features can still be disabled explicitly`() = runTest {
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED, false)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_GOALS_ENABLED, false)

        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertFalse(snapshot.featureFlags.reviewEnabled)
        assertFalse(snapshot.featureFlags.goalsEnabled)
        // Disabling a companion feature must not silently disable the other graduated wave.
        assertTrue(snapshot.featureFlags.habitsEnabled)
    }

    @Test
    fun `legacy parked habits and meds false values are promoted once`() = runTest {
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED, false)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED, false)
        context.writeChronosUiBooleanSetting(
            ChronosUiSettingsKeys.KEY_FEATURE_HABITS_MEDICATION_DEFAULTS_PROMOTED,
            false
        )

        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertTrue(snapshot.featureFlags.habitsEnabled)
        assertTrue(snapshot.featureFlags.medicationEnabled)
    }

    @Test
    fun `promoted habits and meds can still be disabled explicitly`() = runTest {
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED, false)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED, false)

        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertFalse(snapshot.featureFlags.habitsEnabled)
        assertFalse(snapshot.featureFlags.medicationEnabled)
    }

    @Test
    fun `ui setting listener reacts immediately to shell visibility flags`() {
        val prefs = context.getSharedPreferences(ChronosUiSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        var changeCount = 0
        val listener = prefs.registerChronosUiSettingsChangeListener {
            changeCount++
        }

        prefs.edit()
            .putBoolean(ChronosUiSettingsKeys.KEY_GLASS_SURFACES, false)
            .commit()
        prefs.edit()
            .putBoolean(ChronosUiSettingsKeys.KEY_HIGH_CONTRAST, true)
            .commit()
        prefs.edit()
            .putBoolean(ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED, true)
            .commit()
        prefs.edit()
            .putBoolean(ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED, true)
            .commit()
        prefs.edit()
            .putBoolean(ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED, true)
            .commit()
        prefs.edit()
            .putBoolean(ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED, true)
            .commit()
        prefs.edit()
            .putString(ChronosUiSettingsKeys.KEY_BACKDROP_THEME, ChronosBackdropTheme.WATER_DROPS.name)
            .commit()
        prefs.edit()
            .putString("unrelated", "ignored")
            .commit()

        prefs.unregisterOnSharedPreferenceChangeListener(listener)

        assertEquals(7, changeCount)
    }

    @Test
    fun `snapshot reads persisted ui settings from DataStore`() = runTest {
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_DYNAMIC_COLOR, false)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_REDUCE_MOTION, true)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_GLASS_SURFACES, false)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_HIGH_CONTRAST, true)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED, true)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED, true)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED, true)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED, true)
        context.writeChronosUiStringSetting(
            ChronosUiSettingsKeys.KEY_APPEARANCE_MODE,
            ChronosUiSettingsKeys.APPEARANCE_DARK
        )
        context.writeChronosUiStringSetting(
            ChronosUiSettingsKeys.KEY_BACKDROP_THEME,
            ChronosBackdropTheme.WATER_DROPS.name
        )

        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertFalse(snapshot.dynamicColorEnabled)
        assertTrue(snapshot.reduceMotionEnabled)
        assertFalse(snapshot.glassSurfacesEnabled)
        assertTrue(snapshot.highContrastEnabled)
        assertTrue(snapshot.featureFlags.habitsEnabled)
        assertTrue(snapshot.featureFlags.medicationEnabled)
        assertTrue(snapshot.featureFlags.reviewEnabled)
        assertTrue(snapshot.featureFlags.aiAdvisorEnabled)
        assertEquals(ChronosUiSettingsKeys.APPEARANCE_DARK, snapshot.appearanceMode)
        assertEquals(ChronosBackdropTheme.WATER_DROPS, snapshot.backdropTheme)
    }

    @Test
    fun `settings flow emits DataStore changes`() = runTest {
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_HIGH_CONTRAST, true)
        context.writeChronosUiStringSetting(ChronosUiSettingsKeys.KEY_BACKDROP_THEME, ChronosBackdropTheme.AURORA.name)

        val snapshot = context.chronosUiSettingsFlow().first()

        assertTrue(snapshot.highContrastEnabled)
        assertEquals(ChronosBackdropTheme.AURORA, snapshot.backdropTheme)
    }
}
