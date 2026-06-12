package com.chronosflow.core.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

object ChronosUiSettingsKeys {
    const val PREFS_NAME = "daydial_ui_settings"
    const val DATASTORE_NAME = "daydial_ui_settings"
    const val KEY_DYNAMIC_COLOR = "dynamic_color"
    const val KEY_REDUCE_MOTION = "reduce_motion"
    const val KEY_GLASS_SURFACES = "glass_surfaces"
    const val KEY_HIGH_CONTRAST = "high_contrast"
    const val KEY_APPEARANCE_MODE = "appearance_mode"
    const val KEY_BACKDROP_THEME = "backdrop_theme"
    const val KEY_FEATURE_HABITS_ENABLED = "feature.habitsEnabled"
    const val KEY_FEATURE_MEDICATION_ENABLED = "feature.medicationEnabled"
    const val KEY_FEATURE_REVIEW_ENABLED = "feature.reviewEnabled"
    const val KEY_FEATURE_AI_ADVISOR_ENABLED = "feature.aiAdvisorEnabled"
    const val KEY_FEATURE_GOALS_ENABLED = "feature.goalsEnabled"
    const val KEY_FEATURE_JOURNAL_ENABLED = "feature.journalEnabled"
    const val KEY_FEATURE_SLEEP_ENABLED = "feature.sleepEnabled"
    const val KEY_FEATURE_HABITS_MEDICATION_DEFAULTS_PROMOTED = "feature.habitsMedicationDefaultsPromoted"
    const val KEY_FEATURE_COMPANION_DEFAULTS_PROMOTED = "feature.companionDefaultsPromoted"
    const val KEY_ASSIST_AUTO_APPLY = "assist.autoApplySuggestions"
    const val KEY_ONBOARDING_COMPLETED = "onboarding.completed"
    const val APPEARANCE_LIGHT = "LIGHT"
    const val APPEARANCE_DARK = "DARK"
    const val APPEARANCE_SYSTEM = "SYSTEM"

    val liveKeys: Set<String> = setOf(
        KEY_DYNAMIC_COLOR,
        KEY_REDUCE_MOTION,
        KEY_GLASS_SURFACES,
        KEY_HIGH_CONTRAST,
        KEY_APPEARANCE_MODE,
        KEY_BACKDROP_THEME,
        KEY_FEATURE_HABITS_ENABLED,
        KEY_FEATURE_MEDICATION_ENABLED,
        KEY_FEATURE_REVIEW_ENABLED,
        KEY_FEATURE_AI_ADVISOR_ENABLED,
        KEY_FEATURE_GOALS_ENABLED,
        KEY_FEATURE_JOURNAL_ENABLED,
        KEY_FEATURE_SLEEP_ENABLED
    )
}

enum class ChronosBackdropTheme(val label: String) {
    LIQUID("Liquid"),
    SMOKE("Smoke"),
    WATER_DROPS("Water drops"),
    AURORA("Aurora"),
    SUNSET_GLOW("Sunset Glow"),
    NEBULA("Cosmic Nebula"),
    MINIMAL("Minimal");

    companion object {
        val Default = LIQUID

        fun fromStorage(value: String?): ChronosBackdropTheme {
            return entries.firstOrNull { it.name == value } ?: Default
        }
    }
}

private val Context.chronosUiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = ChronosUiSettingsKeys.DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, ChronosUiSettingsKeys.PREFS_NAME))
    }
)

/**
 * A set of feature keys that graduate from off-to-on together, guarded by a single "promoted"
 * marker. Until the marker is set, the feature reads as enabled regardless of any stale stored
 * value; once the user makes a deliberate choice (any write to one of the keys sets the marker),
 * the stored value is respected. This is how shipped-off features become on-by-default without
 * stranding installs that persisted a `false` before graduation.
 */
private data class FeatureGraduation(
    val markerKey: String,
    val featureKeys: Set<String>
)

private val FeatureGraduations: List<FeatureGraduation> = listOf(
    FeatureGraduation(
        markerKey = ChronosUiSettingsKeys.KEY_FEATURE_HABITS_MEDICATION_DEFAULTS_PROMOTED,
        featureKeys = setOf(
            ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED,
            ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED
        )
    ),
    FeatureGraduation(
        markerKey = ChronosUiSettingsKeys.KEY_FEATURE_COMPANION_DEFAULTS_PROMOTED,
        featureKeys = setOf(
            ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED,
            ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED,
            ChronosUiSettingsKeys.KEY_FEATURE_GOALS_ENABLED
        )
    )
)

private fun graduationForKey(key: String): FeatureGraduation? =
    FeatureGraduations.firstOrNull { key in it.featureKeys }

data class ChronosFeatureFlags(
    val habitsEnabled: Boolean = true,
    val medicationEnabled: Boolean = true,
    val reviewEnabled: Boolean = true,
    val aiAdvisorEnabled: Boolean = true,
    val goalsEnabled: Boolean = true,
    val journalEnabled: Boolean = true,
    val sleepEnabled: Boolean = true
) {
    companion object {
        val AllEnabled = ChronosFeatureFlags(
            habitsEnabled = true,
            medicationEnabled = true,
            reviewEnabled = true,
            aiAdvisorEnabled = true,
            goalsEnabled = true,
            journalEnabled = true,
            sleepEnabled = true
        )
    }
}

data class ChronosUiSettingsSnapshot(
    val dynamicColorEnabled: Boolean,
    val reduceMotionEnabled: Boolean,
    val glassSurfacesEnabled: Boolean,
    val highContrastEnabled: Boolean,
    val appearanceMode: String,
    val backdropTheme: ChronosBackdropTheme,
    val featureFlags: ChronosFeatureFlags
)

private fun defaultChronosUiSettingsSnapshot(): ChronosUiSettingsSnapshot {
    return ChronosUiSettingsSnapshot(
        dynamicColorEnabled = true,
        reduceMotionEnabled = false,
        glassSurfacesEnabled = true,
        highContrastEnabled = false,
        appearanceMode = ChronosUiSettingsKeys.APPEARANCE_SYSTEM,
        backdropTheme = ChronosBackdropTheme.Default,
        featureFlags = ChronosFeatureFlags.AllEnabled
    )
}

private fun Context.legacyChronosUiPreferences(): SharedPreferences {
    return getSharedPreferences(ChronosUiSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
}

private fun Preferences.markerPromoted(legacyPreferences: SharedPreferences, markerKey: String): Boolean {
    return this[booleanPreferencesKey(markerKey)] ?: legacyPreferences.getBoolean(markerKey, false)
}

private fun SharedPreferences.markerPromoted(markerKey: String): Boolean {
    return getBoolean(markerKey, false)
}

private fun Preferences.readBooleanSetting(
    legacyPreferences: SharedPreferences,
    key: String,
    defaultValue: Boolean
): Boolean {
    val graduation = graduationForKey(key)
    if (graduation != null && !markerPromoted(legacyPreferences, graduation.markerKey)) {
        return true
    }
    return this[booleanPreferencesKey(key)] ?: legacyPreferences.getBoolean(key, defaultValue)
}

private fun SharedPreferences.readBooleanSetting(key: String, defaultValue: Boolean): Boolean {
    val graduation = graduationForKey(key)
    if (graduation != null && !markerPromoted(graduation.markerKey)) {
        return true
    }
    return getBoolean(key, defaultValue)
}

private fun Context.chronosUiPreferencesFlow(): Flow<Preferences> {
    return chronosUiSettingsDataStore.data.catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }
}

private fun Preferences.toChronosUiSettingsSnapshot(context: Context): ChronosUiSettingsSnapshot {
    val legacyPreferences = context.legacyChronosUiPreferences()
    return ChronosUiSettingsSnapshot(
        dynamicColorEnabled = this[booleanPreferencesKey(ChronosUiSettingsKeys.KEY_DYNAMIC_COLOR)]
            ?: legacyPreferences.getBoolean(ChronosUiSettingsKeys.KEY_DYNAMIC_COLOR, true),
        reduceMotionEnabled = this[booleanPreferencesKey(ChronosUiSettingsKeys.KEY_REDUCE_MOTION)]
            ?: legacyPreferences.getBoolean(ChronosUiSettingsKeys.KEY_REDUCE_MOTION, false),
        glassSurfacesEnabled = this[booleanPreferencesKey(ChronosUiSettingsKeys.KEY_GLASS_SURFACES)]
            ?: legacyPreferences.getBoolean(ChronosUiSettingsKeys.KEY_GLASS_SURFACES, true),
        highContrastEnabled = this[booleanPreferencesKey(ChronosUiSettingsKeys.KEY_HIGH_CONTRAST)]
            ?: legacyPreferences.getBoolean(ChronosUiSettingsKeys.KEY_HIGH_CONTRAST, false),
        appearanceMode = this[stringPreferencesKey(ChronosUiSettingsKeys.KEY_APPEARANCE_MODE)]
            ?: legacyPreferences.getString(
                ChronosUiSettingsKeys.KEY_APPEARANCE_MODE,
                ChronosUiSettingsKeys.APPEARANCE_SYSTEM
            )
            ?: ChronosUiSettingsKeys.APPEARANCE_SYSTEM,
        backdropTheme = ChronosBackdropTheme.fromStorage(
            this[stringPreferencesKey(ChronosUiSettingsKeys.KEY_BACKDROP_THEME)]
                ?: legacyPreferences.getString(ChronosUiSettingsKeys.KEY_BACKDROP_THEME, null)
        ),
        featureFlags = ChronosFeatureFlags(
            habitsEnabled = readBooleanSetting(
                legacyPreferences,
                ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED,
                true
            ),
            medicationEnabled = readBooleanSetting(
                legacyPreferences,
                ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED,
                true
            ),
            reviewEnabled = readBooleanSetting(
                legacyPreferences,
                ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED,
                true
            ),
            aiAdvisorEnabled = readBooleanSetting(
                legacyPreferences,
                ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED,
                true
            ),
            goalsEnabled = readBooleanSetting(
                legacyPreferences,
                ChronosUiSettingsKeys.KEY_FEATURE_GOALS_ENABLED,
                true
            ),
            journalEnabled = readBooleanSetting(
                legacyPreferences,
                ChronosUiSettingsKeys.KEY_FEATURE_JOURNAL_ENABLED,
                true
            ),
            sleepEnabled = readBooleanSetting(
                legacyPreferences,
                ChronosUiSettingsKeys.KEY_FEATURE_SLEEP_ENABLED,
                true
            )
        )
    )
}

fun Context.readChronosUiSettingsSnapshot(): ChronosUiSettingsSnapshot {
    return runBlocking(Dispatchers.IO) {
        readChronosUiSettingsSnapshotFromDataStore()
    }
}

internal fun readChronosUiSettingsSnapshot(prefs: SharedPreferences): ChronosUiSettingsSnapshot {
    return ChronosUiSettingsSnapshot(
        dynamicColorEnabled = prefs.getBoolean(ChronosUiSettingsKeys.KEY_DYNAMIC_COLOR, true),
        reduceMotionEnabled = prefs.getBoolean(ChronosUiSettingsKeys.KEY_REDUCE_MOTION, false),
        glassSurfacesEnabled = prefs.getBoolean(ChronosUiSettingsKeys.KEY_GLASS_SURFACES, true),
        highContrastEnabled = prefs.getBoolean(ChronosUiSettingsKeys.KEY_HIGH_CONTRAST, false),
        appearanceMode = prefs.getString(
            ChronosUiSettingsKeys.KEY_APPEARANCE_MODE,
            ChronosUiSettingsKeys.APPEARANCE_SYSTEM
        ) ?: ChronosUiSettingsKeys.APPEARANCE_SYSTEM,
        backdropTheme = ChronosBackdropTheme.fromStorage(
            prefs.getString(ChronosUiSettingsKeys.KEY_BACKDROP_THEME, null)
        ),
        featureFlags = ChronosFeatureFlags(
            habitsEnabled = prefs.readBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED, true),
            medicationEnabled = prefs.readBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED, true),
            reviewEnabled = prefs.readBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED, true),
            aiAdvisorEnabled = prefs.readBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED, true),
            goalsEnabled = prefs.readBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_GOALS_ENABLED, true),
            journalEnabled = prefs.readBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_JOURNAL_ENABLED, true),
            sleepEnabled = prefs.readBooleanSetting(ChronosUiSettingsKeys.KEY_FEATURE_SLEEP_ENABLED, true)
        )
    )
}

suspend fun Context.readChronosUiSettingsSnapshotFromDataStore(): ChronosUiSettingsSnapshot {
    return chronosUiPreferencesFlow().first().toChronosUiSettingsSnapshot(this)
}

fun Context.chronosUiSettingsFlow(): Flow<ChronosUiSettingsSnapshot> {
    return chronosUiPreferencesFlow().map { preferences ->
        preferences.toChronosUiSettingsSnapshot(this)
    }
}

fun Context.readChronosUiBooleanSetting(key: String, defaultValue: Boolean): Boolean {
    return runBlocking(Dispatchers.IO) {
        val legacyPreferences = legacyChronosUiPreferences()
        chronosUiPreferencesFlow().first().readBooleanSetting(legacyPreferences, key, defaultValue)
    }
}

fun Context.readChronosUiStringSetting(key: String, defaultValue: String): String {
    return runBlocking(Dispatchers.IO) {
        chronosUiPreferencesFlow().first()[stringPreferencesKey(key)]
            ?: legacyChronosUiPreferences().getString(key, defaultValue)
            ?: defaultValue
    }
}

fun Context.readChronosUiIntSetting(key: String, defaultValue: Int): Int {
    return runBlocking(Dispatchers.IO) {
        chronosUiPreferencesFlow().first()[intPreferencesKey(key)]
            ?: legacyChronosUiPreferences().getInt(key, defaultValue)
    }
}

suspend fun Context.writeChronosUiBooleanSetting(key: String, value: Boolean) {
    // Once the user explicitly sets a graduated feature, stop forcing its on-by-default value.
    val markerKey = graduationForKey(key)?.markerKey
    chronosUiSettingsDataStore.edit { preferences ->
        preferences[booleanPreferencesKey(key)] = value
        if (markerKey != null) {
            preferences[booleanPreferencesKey(markerKey)] = true
        }
    }
    legacyChronosUiPreferences().edit().apply {
        putBoolean(key, value)
        if (markerKey != null) {
            putBoolean(markerKey, true)
        }
    }.apply()
}

suspend fun Context.writeChronosUiStringSetting(key: String, value: String) {
    chronosUiSettingsDataStore.edit { preferences ->
        preferences[stringPreferencesKey(key)] = value
    }
    legacyChronosUiPreferences().edit().putString(key, value).apply()
}

suspend fun Context.writeChronosUiIntSetting(key: String, value: Int) {
    chronosUiSettingsDataStore.edit { preferences ->
        preferences[intPreferencesKey(key)] = value
    }
    legacyChronosUiPreferences().edit().putInt(key, value).apply()
}

suspend fun Context.clearChronosUiSettingsStore() {
    chronosUiSettingsDataStore.edit { preferences ->
        preferences.clear()
    }
    legacyChronosUiPreferences().edit().clear().commit()
}

fun Context.chronosUiBooleanSettingFlow(key: String, defaultValue: Boolean): Flow<Boolean> {
    return chronosUiPreferencesFlow().map { preferences ->
        preferences.readBooleanSetting(legacyChronosUiPreferences(), key, defaultValue)
    }
}

fun Context.chronosUiStringSettingFlow(key: String, defaultValue: String): Flow<String> {
    return chronosUiPreferencesFlow().map { preferences ->
        preferences[stringPreferencesKey(key)]
            ?: legacyChronosUiPreferences().getString(key, defaultValue)
            ?: defaultValue
    }
}

fun Context.chronosUiIntSettingFlow(key: String, defaultValue: Int): Flow<Int> {
    return chronosUiPreferencesFlow().map { preferences ->
        preferences[intPreferencesKey(key)]
            ?: legacyChronosUiPreferences().getInt(key, defaultValue)
    }
}

/** Read-only observer for a persisted boolean setting — never writes the store back. */
@Composable
fun rememberChronosUiBooleanSetting(key: String, defaultValue: Boolean): Boolean {
    val context = LocalContext.current.applicationContext
    val value by produceState(
        initialValue = context.readChronosUiBooleanSetting(key, defaultValue),
        context,
        key
    ) {
        context.chronosUiBooleanSettingFlow(key, defaultValue).collect { storedValue ->
            value = storedValue
        }
    }
    return value
}

@Composable
fun rememberPersistentUiBooleanSetting(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current.applicationContext
    val state = remember(key, context) {
        mutableStateOf(context.readChronosUiBooleanSetting(key, defaultValue))
    }
    LaunchedEffect(context, key, defaultValue) {
        context.chronosUiBooleanSettingFlow(key, defaultValue).collect { storedValue ->
            if (state.value != storedValue) {
                state.value = storedValue
            }
        }
    }
    LaunchedEffect(context, key, state.value) {
        context.writeChronosUiBooleanSetting(key, state.value)
    }
    return state
}

@Composable
fun rememberPersistentUiStringSetting(key: String, defaultValue: String): MutableState<String> {
    val context = LocalContext.current.applicationContext
    val state = remember(key, context) {
        mutableStateOf(context.readChronosUiStringSetting(key, defaultValue))
    }
    LaunchedEffect(context, key, defaultValue) {
        context.chronosUiStringSettingFlow(key, defaultValue).collect { storedValue ->
            if (state.value != storedValue) {
                state.value = storedValue
            }
        }
    }
    LaunchedEffect(context, key, state.value) {
        context.writeChronosUiStringSetting(key, state.value)
    }
    return state
}

@Composable
fun rememberPersistentUiIntSetting(key: String, defaultValue: Int): MutableState<Int> {
    val context = LocalContext.current.applicationContext
    val state = remember(key, context) {
        mutableStateOf(context.readChronosUiIntSetting(key, defaultValue))
    }
    LaunchedEffect(context, key, defaultValue) {
        context.chronosUiIntSettingFlow(key, defaultValue).collect { storedValue ->
            if (state.value != storedValue) {
                state.value = storedValue
            }
        }
    }
    LaunchedEffect(context, key, state.value) {
        context.writeChronosUiIntSetting(key, state.value)
    }
    return state
}

internal fun SharedPreferences.registerChronosUiSettingsChangeListener(
    onSettingsChanged: () -> Unit
): SharedPreferences.OnSharedPreferenceChangeListener {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in ChronosUiSettingsKeys.liveKeys) {
            onSettingsChanged()
        }
    }
    registerOnSharedPreferenceChangeListener(listener)
    return listener
}

@Composable
fun rememberChronosUiSettings(): ChronosUiSettingsSnapshot {
    val context = LocalContext.current.applicationContext
    val snapshot by produceState(initialValue = defaultChronosUiSettingsSnapshot(), context) {
        context.chronosUiSettingsFlow().collect { storedSnapshot ->
            value = storedSnapshot
        }
    }
    return snapshot
}

@Composable
fun resolveChronosDarkTheme(appearanceMode: String): Boolean = when (appearanceMode) {
    ChronosUiSettingsKeys.APPEARANCE_LIGHT -> false
    ChronosUiSettingsKeys.APPEARANCE_DARK -> true
    else -> isSystemInDarkTheme()
}
