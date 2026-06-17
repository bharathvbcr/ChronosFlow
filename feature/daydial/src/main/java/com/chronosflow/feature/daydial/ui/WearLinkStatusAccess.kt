package com.chronosflow.feature.daydial.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.chronosflow.core.domain.wear.WearLinkStatusProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt access to the app-module [WearLinkStatusProvider] from the Privacy & Sync settings, which
 * isn't backed by a ViewModel. Mirrors [PrivacyPreferencesEntryPoint]: the interface lives in
 * `core:domain` (no Google Play services dependency leaks into the feature module) and the binding
 * is provided in `:app`, where the Wearable APIs live.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WearLinkStatusEntryPoint {
    fun wearLinkStatusProvider(): WearLinkStatusProvider
}

@Composable
internal fun rememberWearLinkStatusProvider(): WearLinkStatusProvider {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors
            .fromApplication(context, WearLinkStatusEntryPoint::class.java)
            .wearLinkStatusProvider()
    }
}
