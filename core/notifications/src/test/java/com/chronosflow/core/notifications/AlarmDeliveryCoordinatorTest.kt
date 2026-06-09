package com.chronosflow.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmReliability
import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
            alarmScheduler
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
    }
}
