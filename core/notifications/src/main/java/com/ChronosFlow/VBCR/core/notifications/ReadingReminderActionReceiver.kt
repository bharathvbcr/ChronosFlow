package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.ReadingStatus
import com.ChronosFlow.VBCR.core.domain.repository.ReadingListRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ReadingReminderAction"

/**
 * Handles the "remind me to read later" notification actions: **Snooze** reschedules the same
 * reminder a couple of hours out, **Mark read** marks the item done. Mirrors
 * [MedicationActionReceiver] so reading reminders reuse the same alarm/snooze machinery.
 */
@AndroidEntryPoint
class ReadingReminderActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var readingListRepository: ReadingListRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val itemId = intent.getStringExtra(EXTRA_READING_ITEM_ID) ?: return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reading"

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (action) {
                    ACTION_MARK_READ -> {
                        readingListRepository.updateStatus(itemId, ReadingStatus.DONE)
                        readingListRepository.setReminder(itemId, null)
                    }
                    ACTION_SNOOZE -> {
                        val now = Instant.now()
                        val snoozedFor = now.plusSeconds(SNOOZE_HOURS * 3600L)
                        alarmScheduler.scheduleAlarmRequest(
                            AlarmRequest(
                                id = readingReminderRequestId(itemId),
                                type = AlarmRequestType.READING_REMINDER,
                                scheduledFor = snoozedFor,
                                title = title,
                                message = "Time to read this",
                                medicationPlanId = null,
                                blockId = null,
                                reliability = AlarmReliability.INEXACT,
                                deliveryState = AlarmDeliveryState.PENDING,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        readingListRepository.setReminder(itemId, snoozedFor)
                    }
                }

                if (notificationId != -1) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(notificationId)
                    ReminderNotificationGroups.refreshSummary(context)
                }
                if (requestId != null) {
                    alarmScheduler.cancelAlarm(requestId)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Reading action failed: ${ex.message}", ex)
            } finally {
                withContext(Dispatchers.Main) { pendingResult.finish() }
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.ChronosFlow.VBCR.core.notifications.ACTION_SNOOZE_READING"
        const val ACTION_MARK_READ = "com.ChronosFlow.VBCR.core.notifications.ACTION_MARK_READ_READING"

        const val EXTRA_READING_ITEM_ID = "EXTRA_READING_ITEM_ID"
        const val EXTRA_REQUEST_ID = "EXTRA_REQUEST_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_TITLE = "EXTRA_TITLE"

        private const val SNOOZE_HOURS = 2L

        /** Alarm request id convention for a reading reminder; the item id is recoverable from it. */
        fun readingReminderRequestId(itemId: String): String = "reading:$itemId"
    }
}
