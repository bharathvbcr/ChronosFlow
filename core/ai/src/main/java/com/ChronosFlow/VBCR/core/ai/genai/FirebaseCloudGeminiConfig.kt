package com.ChronosFlow.VBCR.core.ai.genai

import android.content.Context
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud assist (Firebase AI Logic) is "configured" exactly when a default FirebaseApp is
 * initialized — i.e. the app was built with a google-services.json from a Firebase project that has
 * Firebase AI Logic enabled. Mirrors FirestoreRemoteSyncGateway.isConfigured so the build stays
 * green and the cloud path stays dormant until a Firebase project is added.
 */
@Singleton
class FirebaseCloudGeminiConfig @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CloudGeminiConfig {
    override val isCloudConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()
}
