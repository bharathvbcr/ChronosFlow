package com.chronosflow.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
class ReflowDayActionReceiverTest {

    private val plannerService: PlannerService = mockk()

    private val receiver = ReflowDayActionReceiver().apply {
        this.plannerService = this@ReflowDayActionReceiverTest.plannerService
        markInjected(this)
    }

    private fun markInjected(receiver: ReflowDayActionReceiver) {
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
    fun `ACTION_REFLOW_DAY reflows today from now and cancels notification`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val notificationId = 789
        coEvery { plannerService.rebalanceDay(any(), any()) } returns
            PlannerOperationResult.Applied("Day rebalance complete", "", null, listOf("b1"))

        val intent = Intent(context, ReflowDayActionReceiver::class.java).apply {
            action = ReflowDayActionReceiver.ACTION_REFLOW_DAY
            putExtra(ReflowDayActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = android.app.Notification.Builder(context, "channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Title")
            .setContentText("Text")
        notificationManager.notify(notificationId, builder.build())

        receiver.onReceive(context, intent)

        // today is reflowed from the current minute, not from midnight
        coVerify(timeout = 2000) {
            plannerService.rebalanceDay(LocalDate.now(), match { it != null && it != 0 })
        }

        val shadowNotificationManager = shadowOf(notificationManager)
        assertNull(shadowNotificationManager.getNotification(notificationId))
    }

    @Test
    fun `unknown action is ignored`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, ReflowDayActionReceiver::class.java).apply {
            action = "com.chronosflow.core.notifications.SOMETHING_ELSE"
        }

        receiver.onReceive(context, intent)

        coVerify(exactly = 0) { plannerService.rebalanceDay(any(), any()) }
    }
}
