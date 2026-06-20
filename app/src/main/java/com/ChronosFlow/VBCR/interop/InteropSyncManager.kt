package com.ChronosFlow.VBCR.interop

import android.util.Log
import androidx.room.withTransaction
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.data.dao.TaskDao
import com.ChronosFlow.VBCR.core.data.model.TaskEntity
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls DevTime's tasks and mirrors them into ChronosFlow's task list.
 *
 * Imports are idempotent: each upstream task maps to a deterministic [TaskEntity.id] built from the
 * origin package + the source row id, so repeated syncs REPLACE in place rather than duplicating.
 * Near-identical upstream rows (same title + due time) are also collapsed into one so accidental
 * duplicates in DevTime don't show up twice here. Rows that vanish upstream are pruned. Imported
 * tasks carry [TaskEntity.origin] = DevTime's package, which the provider excludes from sharing, so
 * data never echoes back and forth.
 *
 * Because ChronosFlow is the "sole notifier" when both apps are connected (DevTime suppresses its
 * own reminders), each imported task with a due time gets a ChronosFlow reminder scheduled here.
 *
 * DevTime's zones/people are queryable via [InteropClient] but ChronosFlow has no native table for
 * them, so they aren't imported in this version — that's a clean follow-up using the same pattern.
 */
@Singleton
class InteropSyncManager @Inject constructor(
    private val client: InteropClient,
    private val db: ChronosDatabase,
    private val taskDao: TaskDao,
    private val alarmScheduler: AlarmScheduler,
) {
    private val origin = InteropContract.DEVTIME

    suspend fun syncFromPeer() {
        try {
            // If DevTime isn't installed, do nothing — and deliberately leave any previously
            // imported rows untouched, so a transient uninstall/reinstall doesn't churn the mirror.
            // (When it IS installed but has nothing to share, the prune below removes stale rows.)
            if (!client.isPeerInstalled()) {
                Log.i(TAG, "DevTime not installed — skipping interop sync, keeping existing imports.")
                return
            }

            // Collapse near-identical upstream rows (same trimmed/lowercased title + due time) so an
            // accidental duplicate in DevTime maps to a single ChronosFlow task. Genuinely distinct
            // tasks (different title or time) are kept.
            val rawTasks = client.fetchPeerTasks()
            if (rawTasks.size > MAX_INTEROP_TASKS) {
                Log.w(TAG, "Peer returned ${rawTasks.size} tasks, capping at $MAX_INTEROP_TASKS")
            }
            val tasks = rawTasks.take(MAX_INTEROP_TASKS).distinctBy {
                it.title.trim().lowercase() + "|" + (it.dueAt ?: "none")
            }
            val now = Instant.now()
            val priorImportedIds = taskDao.getImportedTaskIds(origin).toSet()
            val keepIds = mutableListOf<String>()

            // Wrap all upserts + the pruning delete in a single transaction so a process kill
            // mid-loop can't leave the mirror in a partial state (some rows updated, others stale).
            db.withTransaction {
                for (t in tasks) {
                    val id = "interop:$origin:${t.externalId}"
                    taskDao.upsertTask(
                        TaskEntity(
                            id = id,
                            title = t.title.ifBlank { "(untitled)" }.take(500),
                            description = null,
                            isCompleted = t.isCompleted,
                            priority = t.priority ?: 0,
                            dueDate = t.dueAt?.let(Instant::ofEpochMilli),
                            createdAt = now, // imports are treated as freshly mirrored each sync
                            updatedAt = now,
                            origin = origin,
                            externalId = t.externalId?.take(128),
                        ),
                    )
                    keepIds += id
                }

                if (keepIds.isEmpty()) {
                    taskDao.deleteAllImportedTasks(origin)
                } else {
                    taskDao.deleteImportedTasksNotIn(origin, keepIds)
                }
            }

            // Schedule/cancel reminders outside the transaction (alarm ops are not DB writes).
            for (t in tasks) {
                val id = "interop:$origin:${t.externalId}"
                scheduleReminder(id, t)
            }
            // Cancel reminders for imports that no longer exist upstream.
            (priorImportedIds - keepIds.toSet()).forEach(alarmScheduler::cancelAlarm)
            Log.i(TAG, "Interop sync from DevTime: ${keepIds.size} task(s) mirrored.")
        } catch (e: Exception) {
            Log.w(TAG, "Interop sync failed (continuing standalone): ${e.message}")
        }
    }

    /**
     * Schedules (or refreshes) a ChronosFlow reminder for an imported task so ChronosFlow is the one
     * app that notifies. Fires [REMINDER_LEAD_MILLIS] before the due time (or at the due time if the
     * lead has already passed); undated tasks and past times are simply not scheduled.
     */
    private fun scheduleReminder(id: String, task: InteropClient.RemoteTask) {
        if (task.isCompleted) {
            alarmScheduler.cancelAlarm(id)
            return
        }
        val dueAt = task.dueAt ?: return
        val lead = dueAt - REMINDER_LEAD_MILLIS
        val triggerAt = if (lead > System.currentTimeMillis()) lead else dueAt
        alarmScheduler.scheduleExactAlarm(
            id = id,
            time = Instant.ofEpochMilli(triggerAt),
            title = task.title.ifBlank { "Task" },
            message = "Reminder (shared from DevTime)",
        )
    }

    private companion object {
        const val TAG = "InteropSyncManager"
        const val REMINDER_LEAD_MILLIS = 10 * 60 * 1000L
        const val MAX_INTEROP_TASKS = 500
    }
}
