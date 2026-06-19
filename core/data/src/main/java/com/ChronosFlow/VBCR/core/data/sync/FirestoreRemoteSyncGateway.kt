package com.ChronosFlow.VBCR.core.data.sync

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
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

    override suspend fun push(batch: RemoteSyncBatch) {
        if (!isConfigured) {
            throw RemoteSyncUnavailableException("Firebase is not configured for ChronosFlow sync")
        }

        val firestore = FirebaseFirestore.getInstance()
        val syncRoot = firestore.collection(SYNC_COLLECTION).document(DEFAULT_OWNER_DOCUMENT)
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
        const val DEFAULT_OWNER_DOCUMENT = "default"
        const val TASKS_COLLECTION = "tasks"
        const val TIME_BLOCKS_COLLECTION = "time_blocks"
    }
}
