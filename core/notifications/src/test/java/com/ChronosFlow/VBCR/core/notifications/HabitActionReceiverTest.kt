package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.ChronosFlow.VBCR.core.domain.usecase.CompleteHabitByIdUseCase
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HabitActionReceiverTest {

    private val completeHabitByIdUseCase: CompleteHabitByIdUseCase = mockk(relaxed = true)
    private val currentBlockNotificationCoordinator: CurrentBlockNotificationCoordinator = mockk(relaxed = true)

    private val receiver = HabitActionReceiver().apply {
        this.completeHabitByIdUseCase = this@HabitActionReceiverTest.completeHabitByIdUseCase
        this.currentBlockNotificationCoordinator = this@HabitActionReceiverTest.currentBlockNotificationCoordinator
        markInjected(this)
    }

    private fun markInjected(receiver: HabitActionReceiver) {
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
    fun `ACTION_COMPLETE marks habit completed for today and cancels notification`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val habitId = "habit-123"
        val notificationId = 456

        val intent = Intent(context, HabitActionReceiver::class.java).apply {
            action = HabitActionReceiver.ACTION_COMPLETE
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, habitId)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
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

        // Verify that complete habit use case is invoked
        coVerify(timeout = 2000) {
            completeHabitByIdUseCase(habitId, LocalDate.now())
        }

        // Check that notification is cancelled
        val shadowNotificationManager = shadowOf(notificationManager)
        assertNull(shadowNotificationManager.getNotification(notificationId))
    }
}
