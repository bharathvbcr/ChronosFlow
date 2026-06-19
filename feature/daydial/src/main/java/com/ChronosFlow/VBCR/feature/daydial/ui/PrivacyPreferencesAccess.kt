package com.ChronosFlow.VBCR.feature.daydial.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ChronosFlow.VBCR.core.data.privacy.PrivacyPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt access to [PrivacyPreferences] from a Composable that isn't backed by a ViewModel. The
 * privacy toggles read/write the very same `chronos_preferences` store the widget/watch mirror and
 * the focus notifications consult, so a change made here is honoured everywhere redaction is read —
 * unlike [com.ChronosFlow.VBCR.core.ui.settings.rememberPersistentUiBooleanSetting], which targets the
 * separate UI-settings store.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PrivacyPreferencesEntryPoint {
    fun privacyPreferences(): PrivacyPreferences
}

/** Resolves the app-singleton [PrivacyPreferences] and remembers it for the composition. */
@Composable
internal fun rememberPrivacyPreferences(): PrivacyPreferences {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors
            .fromApplication(context, PrivacyPreferencesEntryPoint::class.java)
            .privacyPreferences()
    }
}
