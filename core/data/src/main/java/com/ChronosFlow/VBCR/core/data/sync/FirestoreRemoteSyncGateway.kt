package com.ChronosFlow.VBCR.core.data.sync

import android.content.Context
import android.provider.Settings
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class FirestoreRemoteSyncGateway @Inject constructor(
    @param:ApplicationContext private val context: Context
) : RemoteSyncGateway {
    override val isConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Derives a stable per-installation document identifier from the Android ANDROID_ID setting,
     * falling back to a random UUID stored in SharedPreferences when ANDROID_ID is unavailable.
     * This scopes all Firestore writes to this installation so multiple devices or users using
     * the same Firebase project cannot read each other's data (PRIV-002 isolation fix).
     */
    private val ownerDocumentId: String by lazy {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            // Use ANDROID_ID as a stable, device-scoped key. It is not a human-readable name
            // and changes on factory reset — acceptable for sync isolation purposes.
            "device_${androidId}"
        } else {
            // Fallback: generate a random UUID persisted in SharedPreferences so it survives
            // process restarts but is scoped to this install.
            val prefs = context.getSharedPreferences(INSTALL_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also { id ->
                prefs.edit().putString(KEY_INSTALL_ID, id).apply()
            }
        }
    }

    override suspend fun push(batch: RemoteSyncBatch) {
        if (!isConfigured) {
            throw RemoteSyncUnavailableException("Firebase is not configured for ChronosFlow sync")
        }

        val firestore = FirebaseFirestore.getInstance()
        // PRIV-002: scope to per-installation document rather than the shared "default" document.
        val syncRoot = firestore.collection(SYNC_COLLECTION).document(ownerDocumentId)
        val writeBatch = firestore.batch()

        batch.tasks.forEach { task ->
            writeBatch.set(
                syncRoot.collection(TASKS_COLLECTION).document(task.id),
                task.toFirestoreMap()
            )
        }
        batch.timeBlocks.forEach { timeBlock ->
            writeBatch.set(
                syncRoot.collection(TIME_BLOCKS_COLLECTION).document(timeBlock.id),
                timeBlock.toFirestoreMap()
            )
        }

        writeBatch.commit().await()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isCanceled) {
                continuation.cancel(CancellationException("Firebase task was canceled"))
            } else if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase task failed without an exception")
                )
            }
        }
    }

    private companion object {
        const val SYNC_COLLECTION = "chronosflow_sync"
        // DEFAULT_OWNER_DOCUMENT is no longer used — document path is now per-installation.
        // Kept here as a migration reference in case old data needs to be migrated.
        @Suppress("unused")
        const val LEGACY_DEFAULT_OWNER_DOCUMENT = "default"
        const val INSTALL_PREFS_NAME = "chronosflow_sync_prefs"
        const val KEY_INSTALL_ID = "sync.installation.id"
        const val TASKS_COLLECTION = "tasks"
        const val TIME_BLOCKS_COLLECTION = "time_blocks"
    }
}
