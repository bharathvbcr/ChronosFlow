package com.chronosflow.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmReliability
import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.MedicationDoseEvent
import com.chronosflow.core.domain.model.MedicationDoseEventType
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.MedicationSafetyProfile
import com.chronosflow.core.domain.repository.MedicationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MedicationActionReceiverTest {

    private val medicationRepository: MedicationRepository = mockk(relaxed = true)
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private val alarmDeliveryCoordinator: AlarmDeliveryCoordinator = mockk(relaxed = true)

    private val receiver = MedicationActionReceiver().apply {
        this.medicationRepository = this@MedicationActionReceiverTest.medicationRepository
        this.alarmScheduler = this@MedicationActionReceiverTest.alarmScheduler
        this.alarmDeliveryCoordinator = this@MedicationActionReceiverTest.alarmDeliveryCoordinator
        markInjected(this)
    }

    private fun markInjected(receiver: MedicationActionReceiver) {
        try {
            val superClass = receiver.javaClass.superclass
            val injectedField = superClass.getDeclaredField("injected")
            injectedField.isAccessible = true
            injectedField.set(receiver, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Test
    fun `ACTION_TAKE records dose event, decrements supply, and cancels notification`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val planId = "plan-123"
        val requestId = "request-456"
        val notificationId = 789

        val plan = MedicationPlan(
            id = planId,
            name = "Aspirin",
            dosage = "1",
            unit = "pill",
            notes = null,
            startAt = LocalDateTime.now(),
            endAt = null,
            reminderMinuteOfDay = 480,
            takeWithFood = true,
            missedCount = 0,
            refillNeededAfterDoses = null,
            isActive = true,
            safetyProfile = MedicationSafetyProfile(
                medicationPlanId = planId,
                supplyRemaining = 30,
                refillThreshold = 5
            )
        )

        coEvery { medicationRepository.getMedicationPlanById(planId) } returns plan

        val intent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = MedicationActionReceiver.ACTION_TAKE
            putExtra(MedicationActionReceiver.EXTRA_MEDICATION_PLAN_ID, planId)
            putExtra(MedicationActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(MedicationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        // Post a mock notification so we can check if it gets cancelled
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = android.app.Notification.Builder(context, "channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Title")
            .setContentText("Text")
        notificationManager.notify(notificationId, builder.build())

        // Execute receiver
        receiver.onReceive(context, intent)

        // Wait for coroutine inside receiver to execute
        val doseSlot = slot<MedicationDoseEvent>()
        coVerify(timeout = 2000) {
            medicationRepository.addMedicationDoseEvent(capture(doseSlot))
        }
        assertEquals(MedicationDoseEventType.TAKEN, doseSlot.captured.type)
        assertEquals("1", doseSlot.captured.doseAmount)

        val planSlot = slot<MedicationPlan>()
        coVerify(timeout = 2000) {
            medicationRepository.saveMedicationPlan(capture(planSlot))
        }
        assertEquals(29, planSlot.captured.safetyProfile?.supplyRemaining)

        verify(timeout = 2000) {
            alarmScheduler.cancelAlarm(requestId)
        }

        // Check that notification is cancelled
        val shadowNotificationManager = shadowOf(notificationManager)
        assertNull(shadowNotificationManager.getNotification(notificationId))
    }

    @Test
    fun `ACTION_SNOOZE schedules new alarm, records snooze event, and cancels notification`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val planId = "plan-123"
        val requestId = "request-456"
        val notificationId = 789

        val plan = MedicationPlan(
            id = planId,
            name = "Aspirin",
            dosage = "1",
            unit = "pill",
            notes = null,
            startAt = LocalDateTime.now(),
            endAt = null,
            reminderMinuteOfDay = 480,
            takeWithFood = true,
            missedCount = 0,
            refillNeededAfterDoses = null,
            isActive = true
        )

        coEvery { medicationRepository.getMedicationPlanById(planId) } returns plan

        val intent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = MedicationActionReceiver.ACTION_SNOOZE
            putExtra(MedicationActionReceiver.EXTRA_MEDICATION_PLAN_ID, planId)
            putExtra(MedicationActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(MedicationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        // Post a mock notification so we can check if it gets cancelled
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = android.app.Notification.Builder(context, "channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Title")
            .setContentText("Text")
        notificationManager.notify(notificationId, builder.build())

        // Execute receiver
        receiver.onReceive(context, intent)

        // Verify that snooze dose event is added
        val doseSlot = slot<MedicationDoseEvent>()
        coVerify(timeout = 2000) {
            medicationRepository.addMedicationDoseEvent(capture(doseSlot))
        }
        assertEquals(MedicationDoseEventType.SNOOZED, doseSlot.captured.type)
        assertNull(doseSlot.captured.doseAmount)

        // Verify new alarm request is scheduled
        val requestSlot = slot<AlarmRequest>()
        coVerify(timeout = 2000) {
            alarmScheduler.scheduleAlarmRequest(capture(requestSlot))
        }
        assertEquals(AlarmRequestType.MEDICATION, requestSlot.captured.type)
        assertEquals(AlarmReliability.EXACT, requestSlot.captured.reliability)
        assertEquals(planId, requestSlot.captured.medicationPlanId)

        verify(timeout = 2000) {
            alarmScheduler.cancelAlarm(requestId)
        }

        // Check that notification is cancelled
        val shadowNotificationManager = shadowOf(notificationManager)
        assertNull(shadowNotificationManager.getNotification(notificationId))
    }

    @Test
    fun `ACTION_SKIP records skipped dose event, cancels alarm, and cancels notification`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val planId = "plan-123"
        val requestId = "request-456"
        val notificationId = 789

        val plan = MedicationPlan(
            id = planId,
            name = "Aspirin",
            dosage = "1",
            unit = "pill",
            notes = null,
            startAt = LocalDateTime.now(),
            endAt = null,
            reminderMinuteOfDay = 480,
            takeWithFood = true,
            missedCount = 0,
            refillNeededAfterDoses = null,
            isActive = true
        )

        coEvery { medicationRepository.getMedicationPlanById(planId) } returns plan

        val intent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = MedicationActionReceiver.ACTION_SKIP
            putExtra(MedicationActionReceiver.EXTRA_MEDICATION_PLAN_ID, planId)
            putExtra(MedicationActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(MedicationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        // Post a mock notification so we can check if it gets cancelled
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = android.app.Notification.Builder(context, "channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Title")
            .setContentText("Text")
        notificationManager.notify(notificationId, builder.build())

        // Execute receiver
        receiver.onReceive(context, intent)

        // Verify that skipped dose event is added
        val doseSlot = slot<MedicationDoseEvent>()
        coVerify(timeout = 2000) {
            medicationRepository.addMedicationDoseEvent(capture(doseSlot))
        }
        assertEquals(MedicationDoseEventType.SKIPPED, doseSlot.captured.type)
        assertNull(doseSlot.captured.doseAmount)

        verify(timeout = 2000) {
            alarmScheduler.cancelAlarm(requestId)
        }

        // Check that notification is cancelled
        val shadowNotificationManager = shadowOf(notificationManager)
        assertNull(shadowNotificationManager.getNotification(notificationId))
    }

    @Test
    fun `ACTION_TAKE triggers low supply warning notification when remaining drops below refill threshold`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val planId = "plan-123"
        val requestId = "request-456"
        val notificationId = 789

        val plan = MedicationPlan(
            id = planId,
            name = "Aspirin",
            dosage = "1",
            unit = "pill",
            notes = null,
            startAt = LocalDateTime.now(),
            endAt = null,
            reminderMinuteOfDay = 480,
            takeWithFood = true,
            missedCount = 0,
            refillNeededAfterDoses = null,
            isActive = true,
            safetyProfile = MedicationSafetyProfile(
                medicationPlanId = planId,
                supplyRemaining = 5,
                refillThreshold = 5
            )
        )

        coEvery { medicationRepository.getMedicationPlanById(planId) } returns plan

        val intent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = MedicationActionReceiver.ACTION_TAKE
            putExtra(MedicationActionReceiver.EXTRA_MEDICATION_PLAN_ID, planId)
            putExtra(MedicationActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(MedicationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        // Execute receiver
        receiver.onReceive(context, intent)

        // Verify that updated plan is saved with remaining supply decremented to 4
        val planSlot = slot<MedicationPlan>()
        coVerify(timeout = 2000) {
            medicationRepository.saveMedicationPlan(capture(planSlot))
        }
        assertEquals(4, planSlot.captured.safetyProfile?.supplyRemaining)

        // Verify low supply warning is delivered
        verify(timeout = 2000) {
            alarmDeliveryCoordinator.deliverLowSupplyWarning(any(), 4)
        }
    }
}
