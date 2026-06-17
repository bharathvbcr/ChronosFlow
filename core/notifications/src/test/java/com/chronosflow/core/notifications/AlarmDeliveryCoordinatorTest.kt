package com.chronosflow.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmReliability
import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.ProactiveDigestKeys
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmDeliveryCoordinatorTest {
    private lateinit var context: Context
    private val alarmRequestRepository: AlarmRequestRepository = mockk(relaxed = true)
    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val timeBlockRepository: TimeBlockRepository = mockk(relaxed = true)
    private val habitRepository: HabitRepository = mockk(relaxed = true)
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private val currentBlockNotificationCoordinator: CurrentBlockNotificationCoordinator = mockk(relaxed = true)
    private lateinit var coordinator: AlarmDeliveryCoordinator
    private lateinit var notificationManager: NotificationManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        coordinator = AlarmDeliveryCoordinator(
            context,
            alarmRequestRepository,
            taskRepository,
            timeBlockRepository,
            habitRepository,
            alarmScheduler,
            currentBlockNotificationCoordinator
        )
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)
    }

    @Test
    fun `deliverFromAlarmIntent posts notification and marks as delivered`() = runTest {
        val requestId = "req-1"
        val intent = Intent().apply {
            putExtra(AlarmDeliveryCoordinator.EXTRA_ID, requestId)
            putExtra(AlarmDeliveryCoordinator.EXTRA_TITLE, "Test Title")
            putExtra(AlarmDeliveryCoordinator.EXTRA_MESSAGE, "Test Message")
        }
        
        val alarmRequest = AlarmRequest(
            id = requestId,
            type = AlarmRequestType.URGENT_TASK,
            scheduledFor = Instant.now(),
            title = "Test Title",
            message = "Test Message",
            medicationPlanId = null,
            blockId = null,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        coEvery { alarmRequestRepository.getAlarmRequest(requestId) } returns alarmRequest

        coordinator.deliverFromAlarmIntent(intent, AlarmReceiver::class.java)

        // Verify notification
        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        val notification = notifications[0]
        assertEquals("Test Title", shadowOf(notification).contentTitle)
        assertEquals("Test Message", shadowOf(notification).contentText)
        // URGENT_TASK routes to the critical channel, so the (single, effective) accent must be
        // the warm critical tint — guards against a reintroduced double-setColor override.
        assertEquals(
            ContextCompat.getColor(context, R.color.notification_accent_critical),
            notification.color
        )

        // Verify repository update
        coVerify { 
            alarmRequestRepository.saveAlarmRequest(match { 
                it.id == requestId && it.deliveryState == AlarmDeliveryState.DELIVERED 
            }) 
        }
        
        // Verify alarm cancelled (removed from system scheduler)
        coVerify { alarmScheduler.cancelAlarm(requestId) }
    }

    @Test
    fun `planner block start reroutes to the live now notification instead of a transient reminder`() = runTest {
        val requestId = "daydial:${LocalDate.now()}:block-1:start"
        val intent = Intent().apply {
            putExtra(AlarmDeliveryCoordinator.EXTRA_ID, requestId)
            putExtra(AlarmDeliveryCoordinator.EXTRA_TITLE, "Deep work")
            putExtra(AlarmDeliveryCoordinator.EXTRA_MESSAGE, "Your planned block starts now.")
        }
        coEvery { alarmRequestRepository.getAlarmRequest(requestId) } returns AlarmRequest(
            id = requestId,
            type = AlarmRequestType.BLOCK_START,
            scheduledFor = Instant.now(),
            title = "Deep work",
            message = "Your planned block starts now.",
            medicationPlanId = null,
            blockId = "block-1",
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        every { currentBlockNotificationCoordinator.isEnabled() } returns true

        coordinator.deliverFromAlarmIntent(intent, AlarmReceiver::class.java)

        // No separate "starts now" reminder is posted — the live "now" notification owns the alert.
        assertEquals(0, shadowNotificationManager.allNotifications.size)
        coVerify { currentBlockNotificationCoordinator.refresh(any(), any()) }
        coVerify { alarmScheduler.cancelAlarm(requestId) }
    }

    @Test
    fun `planner block start still posts a reminder when the now notification is disabled`() = runTest {
        val requestId = "daydial:${LocalDate.now()}:block-2:start"
        val intent = Intent().apply {
            putExtra(AlarmDeliveryCoordinator.EXTRA_ID, requestId)
            putExtra(AlarmDeliveryCoordinator.EXTRA_TITLE, "Deep work")
            putExtra(AlarmDeliveryCoordinator.EXTRA_MESSAGE, "Your planned block starts now.")
        }
        coEvery { alarmRequestRepository.getAlarmRequest(requestId) } returns AlarmRequest(
            id = requestId,
            type = AlarmRequestType.BLOCK_START,
            scheduledFor = Instant.now(),
            title = "Deep work",
            message = "Your planned block starts now.",
            medicationPlanId = null,
            blockId = "block-2",
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        every { currentBlockNotificationCoordinator.isEnabled() } returns false

        coordinator.deliverFromAlarmIntent(intent, AlarmReceiver::class.java)

        // Fallback: the user turned the live "now" notification off, so keep the heads-up reminder.
        assertEquals(1, shadowNotificationManager.allNotifications.size)
        coVerify(exactly = 0) { currentBlockNotificationCoordinator.refresh(any(), any()) }
    }

    @Test
    fun `daily review reminder shows today's cached proactive digest`() = runTest {
        val requestId = "daydial:${LocalDate.now()}:day:review"
        context.getSharedPreferences(ProactiveDigestKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ProactiveDigestKeys.KEY_TEXT, "3 blocks done; 2 tasks left to close out.")
            .putString(ProactiveDigestKeys.KEY_FOR_DATE, LocalDate.now().toString())
            .apply()
        coEvery { alarmRequestRepository.getAlarmRequest(requestId) } returns reviewRequest(requestId)

        coordinator.deliverFromAlarmIntent(reviewIntent(requestId), AlarmReceiver::class.java)

        val notification = shadowNotificationManager.allNotifications.single()
        assertEquals("3 blocks done; 2 tasks left to close out.", shadowOf(notification).contentText)
    }

    @Test
    fun `daily review reminder keeps default copy when cached digest is stale`() = runTest {
        val requestId = "daydial:${LocalDate.now()}:day:review"
        context.getSharedPreferences(ProactiveDigestKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ProactiveDigestKeys.KEY_TEXT, "Yesterday's digest.")
            .putString(ProactiveDigestKeys.KEY_FOR_DATE, LocalDate.now().minusDays(1).toString())
            .apply()
        coEvery { alarmRequestRepository.getAlarmRequest(requestId) } returns reviewRequest(requestId)

        coordinator.deliverFromAlarmIntent(reviewIntent(requestId), AlarmReceiver::class.java)

        val notification = shadowNotificationManager.allNotifications.single()
        assertEquals("Review planned, actual, and missed blocks.", shadowOf(notification).contentText)
    }

    private fun reviewIntent(requestId: String): Intent = Intent().apply {
        putExtra(AlarmDeliveryCoordinator.EXTRA_ID, requestId)
        putExtra(AlarmDeliveryCoordinator.EXTRA_TITLE, "Daily review")
        putExtra(AlarmDeliveryCoordinator.EXTRA_MESSAGE, "Review planned, actual, and missed blocks.")
    }

    private fun reviewRequest(requestId: String): AlarmRequest = AlarmRequest(
        id = requestId,
        type = AlarmRequestType.DAILY_REVIEW,
        scheduledFor = Instant.now(),
        title = "Daily review",
        message = "Review planned, actual, and missed blocks.",
        medicationPlanId = null,
        blockId = null,
        reliability = AlarmReliability.INEXACT,
        deliveryState = AlarmDeliveryState.PENDING,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `deliverLowSupplyWarning posts critical notification`() {
        val plan = MedicationPlan(
            id = "plan-1",
            name = "Aspirin",
            dosage = "81mg",
            unit = "tablet",
            notes = null,
            startAt = LocalDateTime.now(),
            endAt = null,
            reminderMinuteOfDay = 480,
            takeWithFood = false,
            missedCount = 0,
            refillNeededAfterDoses = 5,
            isActive = true
        )

        coordinator.deliverLowSupplyWarning(plan, 3)

        val notifications = shadowNotificationManager.allNotifications
        assertEquals(1, notifications.size)
        val notification = notifications[0]
        assertEquals("Low Medication Supply: Aspirin", shadowOf(notification).contentTitle)
        assertTrue(shadowOf(notification).contentText?.contains("3 tablet(s) left") == true)
        // Low-supply always uses the critical channel → warm critical accent (single effective
        // setColor); guards the second alarm builder against a reintroduced double-setColor.
        assertEquals(
            ContextCompat.getColor(context, R.color.notification_accent_critical),
            notification.color
        )
    }
}
