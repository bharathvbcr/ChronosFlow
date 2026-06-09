package com.chronosflow.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.chronosflow.core.domain.usecase.ToggleTaskCompletionUseCase
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TaskActionReceiverTest {

    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase = mockk(relaxed = true)

    private val receiver = TaskActionReceiver().apply {
        this.toggleTaskCompletionUseCase = this@TaskActionReceiverTest.toggleTaskCompletionUseCase
        markInjected(this)
    }

    private fun markInjected(receiver: TaskActionReceiver) {
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
    fun `ACTION_COMPLETE toggles task completion status and cancels notification`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val taskId = "task-123"
        val notificationId = 456

        val intent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_COMPLETE
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = android.app.Notification.Builder(context, "channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Title")
            .setContentText("Text")
        notificationManager.notify(notificationId, builder.build())

        receiver.onReceive(context, intent)

        coVerify(timeout = 2000) {
            toggleTaskCompletionUseCase(taskId)
        }

        val shadowNotificationManager = shadowOf(notificationManager)
        assertNull(shadowNotificationManager.getNotification(notificationId))
    }
}
