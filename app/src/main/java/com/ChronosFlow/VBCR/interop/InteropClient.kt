package com.ChronosFlow.VBCR.interop

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads DevTime's [InteropProvider]. Every call degrades to an empty list when DevTime isn't
 * installed, hasn't granted us access, or otherwise fails — ChronosFlow always works standalone.
 */
@Singleton
class InteropClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * True if DevTime is installed on this device (and visible to us via the manifest `<queries>`
     * entry). Use this to gate any "shared with DevTime" UI — when it's false the app simply runs
     * solo. Cheap enough to call on demand.
     */
    fun isPeerInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(InteropContract.DEVTIME, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * True if DevTime is installed AND exposes a trusted interop provider we're allowed to read
     * (installed + correct authority owner + pinned signing cert). This is the precondition every
     * fetch already checks internally; expose it so callers/UI can branch without issuing a query.
     */
    fun isPeerShareAvailable(): Boolean = isPeerInstalled() && peerProviderTrusted()

    data class RemoteTask(
        val externalId: String,
        val title: String,
        val dueAt: Long?,
        val isCompleted: Boolean,
        val priority: Int?,
    )

    fun fetchPeerTasks(): List<RemoteTask> = query(InteropContract.PATH_TASKS) { c ->
        RemoteTask(
            externalId = c.string("external_id") ?: return@query null,
            title = c.string("title").orEmpty(),
            dueAt = c.longOrNull("due_at"),
            isCompleted = (c.longOrNull("is_completed") ?: 0L) != 0L,
            priority = c.longOrNull("priority")?.toInt(),
        )
    }

    private fun <T> query(path: String, map: (Cursor) -> T?): List<T> {
        if (!peerProviderTrusted()) return emptyList()
        return try {
            context.contentResolver.query(InteropContract.peerUri(path), null, null, null, null)
                ?.use { c ->
                    buildList {
                        while (c.moveToNext()) map(c)?.let(::add)
                    }
                } ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(TAG, "Interop: peer denied access to /$path: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            // Peer not installed / provider missing / transient failure — stay standalone.
            Log.d(TAG, "Interop: /$path unavailable: ${e.message}")
            emptyList()
        }
    }

    /**
     * Confirms the app that actually owns DevTime's authority is the real, pinned DevTime before we
     * query it — so an app squatting `com.firebase.meridian.wxqlpz.share` can't feed us forged data.
     */
    private fun peerProviderTrusted(): Boolean {
        val info = try {
            context.packageManager.resolveContentProvider(InteropContract.PEER_AUTHORITY, 0)
        } catch (e: Exception) {
            null
        } ?: return false
        if (info.packageName != InteropContract.DEVTIME) {
            Log.w(TAG, "Interop: ${InteropContract.PEER_AUTHORITY} is owned by ${info.packageName}, not DevTime — ignoring.")
            return false
        }
        return PeerVerifier.isTrusted(context, info.packageName)
    }

    private fun Cursor.string(name: String): String? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.longOrNull(name: String): Long? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getLong(i)
    }

    private companion object {
        const val TAG = "InteropClient"
    }
}
