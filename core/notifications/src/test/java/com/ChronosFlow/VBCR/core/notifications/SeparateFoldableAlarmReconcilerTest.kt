package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeparateFoldableAlarmReconcilerTest {
    private lateinit var context: Context
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var alarmRequestRepository: AlarmRequestRepository
    private lateinit var reconciler: SeparateFoldableAlarmReconciler

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarmScheduler = mockk(relaxed = true)
        alarmRequestRepository = mockk(relaxed = true)
        reconciler = SeparateFoldableAlarmReconciler(context, alarmScheduler, alarmRequestRepository)
    }

    @Test
    fun `no-op when fold mode is off`() = runTest {
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notifications.currentBlockLive", true)
            .putBoolean("notifications.foldReminders", false)
            .apply()

        assertEquals(0, reconciler.cancelWhenFoldModeActive())
        verify(exactly = 0) { alarmScheduler.cancelAlarm(any()) }
    }

    @Test
    fun `cancels persisted foldable alarm ids when fold mode is on`() = runTest {
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notifications.currentBlockLive", true)
            .putBoolean("notifications.foldReminders", true)
            .apply()
        every { alarmScheduler.getPersistedAlarmIds() } returns setOf(
            "task:abc",
            "daydial:2026-07-03:day:review",
        )
        coEvery { alarmRequestRepository.observeRequestsByType(any()) } returns flowOf(emptyList())

        assertEquals(1, reconciler.cancelWhenFoldModeActive())
        verify { alarmScheduler.cancelAlarm("task:abc") }
        verify(exactly = 0) { alarmScheduler.cancelAlarm("daydial:2026-07-03:day:review") }
    }

    @Test
    fun `cancels foldable repository requests when fold mode is on`() = runTest {
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notifications.currentBlockLive", true)
            .putBoolean("notifications.foldReminders", true)
            .apply()
        every { alarmScheduler.getPersistedAlarmIds() } returns emptySet()
        val now = Instant.parse("2026-07-03T12:00:00Z")
        val medication = AlarmRequest(
            id = "med-uuid",
            type = AlarmRequestType.MEDICATION,
            scheduledFor = now.plusSeconds(3600),
            title = "Meds",
            message = "Take meds",
            medicationPlanId = "plan-1",
            blockId = null,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        coEvery { alarmRequestRepository.observeRequestsByType(AlarmRequestType.MEDICATION) } returns
            flowOf(listOf(medication))
        coEvery { alarmRequestRepository.observeRequestsByType(AlarmRequestType.URGENT_TASK) } returns
            flowOf(emptyList())
        coEvery { alarmRequestRepository.observeRequestsByType(AlarmRequestType.BLOCK_START) } returns
            flowOf(emptyList())

        assertEquals(1, reconciler.cancelWhenFoldModeActive())
        verify { alarmScheduler.cancelAlarm("med-uuid") }
        coVerify {
            alarmRequestRepository.saveAlarmRequest(
                match {
                    it.id == "med-uuid" &&
                        it.deliveryState == AlarmDeliveryState.CANCELLED &&
                        it.failureReason == FOLDED_REMINDER_SKIP_REASON
                }
            )
        }
    }
}
