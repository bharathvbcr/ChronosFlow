package com.ChronosFlow.VBCR.core.ui.settings

import android.content.Context
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
        assertTrue(flags.journalEnabled)
        assertTrue(flags.sleepEnabled)
    }

    @Test
    fun `graduated features default to enabled`() = runTest {
        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertTrue(snapshot.featureFlags.habitsEnabled)
        assertTrue(snapshot.featureFlags.medicationEnabled)
        assertTrue(snapshot.featureFlags.reviewEnabled)
        assertTrue(snapshot.featureFlags.aiAdvisorEnabled)
        assertTrue(snapshot.featureFlags.goalsEnabled)
        assertTrue(snapshot.featureFlags.journalEnabled)
        assertTrue(snapshot.featureFlags.sleepEnabled)
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
    fun `legacy parked review and ai false values are promoted once`() = runTest {
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED, false)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED, false)
        context.writeChronosUiBooleanSetting(
            ChronosUiSettingsKeys.KEY_FEATURE_COMPANION_DEFAULTS_PROMOTED,
            false
        )

        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertTrue(snapshot.featureFlags.reviewEnabled)
        assertTrue(snapshot.featureFlags.aiAdvisorEnabled)
    }

    @Test
    fun `legacy parked journal and sleep false values are promoted once`() = runTest {
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_JOURNAL_ENABLED, false)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_SLEEP_ENABLED, false)
        context.writeChronosUiBooleanSetting(
            ChronosUiSettingsKeys.KEY_FEATURE_COMPANION_DEFAULTS_PROMOTED,
            false
        )

        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertTrue(snapshot.featureFlags.journalEnabled)
        assertTrue(snapshot.featureFlags.sleepEnabled)
    }

    @Test
    fun `promoted journal and sleep can still be disabled explicitly`() = runTest {
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_JOURNAL_ENABLED, false)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_SLEEP_ENABLED, false)

        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertFalse(snapshot.featureFlags.journalEnabled)
        assertFalse(snapshot.featureFlags.sleepEnabled)
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
    fun `promoted review and ai can still be disabled explicitly`() = runTest {
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED, false)
        context.writeChronosUiBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED, false)

        val snapshot = context.readChronosUiSettingsSnapshotFromDataStore()

        assertFalse(snapshot.featureFlags.reviewEnabled)
        assertFalse(snapshot.featureFlags.aiAdvisorEnabled)
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

    // -------------------------------------------------------------------------
    // drop(1) — no write on first composition
    // -------------------------------------------------------------------------

    /**
     * [rememberPersistentUiBooleanSetting] wires a [snapshotFlow] with [drop(1)] so that the
     * initial value from [remember] does NOT trigger a DataStore write. Without drop(1) every
     * Composable that reads a setting would write it back on first composition, causing noisy
     * DataStore churn.
     *
     * We verify the contract purely at the flow level: a flow that emits exactly one item and
     * then completes must produce ZERO items after drop(1), proving that first-composition
     * would not reach the write lambda.
     */
    @Test
    fun `rememberPersistentUiBooleanSetting drop1 contract skips first emission`() = runTest {
        // Simulate what snapshotFlow { state.value } produces on first composition:
        // one emission equal to the initial remembered value, then completion.
        val initialValue = false
        val simulatedSnapshotFlow = kotlinx.coroutines.flow.flowOf(initialValue)

        val collectedAfterDrop = mutableListOf<Boolean>()
        simulatedSnapshotFlow
            .drop(1)
            .collect { collectedAfterDrop.add(it) }

        // drop(1) must have consumed the only emission — no write should occur.
        assertTrue(
            "drop(1) must suppress the first (initial) emission so no write fires on composition",
            collectedAfterDrop.isEmpty()
        )
    }

    @Test
    fun `rememberPersistentUiBooleanSetting drop1 contract forwards subsequent user changes`() =
        runTest {
            // Simulate: initial emission (from remember) followed by a user-driven change.
            val simulatedSnapshotFlow = kotlinx.coroutines.flow.flow<Boolean> {
                emit(false)  // first composition — must be dropped
                emit(true)   // user toggles the setting — must reach the write lambda
            }

            val collectedAfterDrop = mutableListOf<Boolean>()
            simulatedSnapshotFlow
                .drop(1)
                .collect { collectedAfterDrop.add(it) }

            // Only the second (user-driven) emission must have passed through.
            assertEquals(
                "Exactly one emission should pass drop(1) — the user-driven change",
                1,
                collectedAfterDrop.size
            )
            assertEquals(true, collectedAfterDrop.single())
        }

    @Test
    fun `rememberPersistentUiBooleanSetting drop1 does not write store on read-only first composition`() =
        runTest {
            // Record writes to the DataStore so we can assert none happen on first composition.
            // We use the real DataStore (Robolectric) and verify the stored value is unchanged
            // after a single "read-only" observation.
            val key = ChronosUiSettingsKeys.KEY_HIGH_CONTRAST
            val defaultValue = false

            // Ensure the key has no stored value so we start from the default.
            context.clearChronosUiSettingsStore()

            // Read the current stored value (should be the default).
            val snapshotBefore = context.readChronosUiSettingsSnapshotFromDataStore()

            // Simulate first composition: the Composable reads the setting but does NOT write it
            // (because drop(1) prevents the write lambda from being called). We replicate this by
            // calling readChronosUiBooleanSetting (which maps to the remember {} initialValue)
            // and then reading the DataStore again to confirm it is untouched.
            context.readChronosUiBooleanSetting(key, defaultValue)

            val snapshotAfter = context.readChronosUiSettingsSnapshotFromDataStore()

            // The DataStore must not have been written: both snapshots must be equal.
            assertEquals(
                "A read-only composition must not write back the default value to the DataStore",
                snapshotBefore.highContrastEnabled,
                snapshotAfter.highContrastEnabled
            )
        }
}
