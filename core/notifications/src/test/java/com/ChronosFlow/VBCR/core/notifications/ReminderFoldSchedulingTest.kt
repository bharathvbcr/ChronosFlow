package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ReminderFoldSchedulingTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun `skips when both block live and fold are on`() {
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("notifications.currentBlockLive", true)
            .putBoolean("notifications.foldReminders", true)
            .apply()

        assertTrue(ReminderFoldScheduling.skipsSeparateRemindersWhenFolded(context))
    }

    @Test
    fun `does not skip when fold is off`() {
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("notifications.currentBlockLive", true)
            .putBoolean("notifications.foldReminders", false)
            .apply()

        assertFalse(ReminderFoldScheduling.skipsSeparateRemindersWhenFolded(context))
    }

    @Test
    fun `does not skip when block live is off`() {
        context.getSharedPreferences("daydial_ui_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("notifications.currentBlockLive", false)
            .putBoolean("notifications.foldReminders", true)
            .apply()

        assertFalse(ReminderFoldScheduling.skipsSeparateRemindersWhenFolded(context))
    }

    @Test
    fun `detects foldable alarm request types`() {
        val medication = AlarmRequest(
            id = "med-1",
            type = AlarmRequestType.MEDICATION,
            scheduledFor = Instant.now().plusSeconds(60),
            title = "Meds",
            message = "Take meds",
            medicationPlanId = "plan-1",
            blockId = null,
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val habitBlock = medication.copy(
            id = "daydial:2026-07-03:habit-h1:start",
            type = AlarmRequestType.BLOCK_START,
            medicationPlanId = null,
            blockId = "habit-h1"
        )
        val focusBlock = habitBlock.copy(id = "daydial:2026-07-03:block-1:start", blockId = "block-1")

        assertTrue(ReminderFoldScheduling.isSeparateFoldableReminder(medication))
        assertTrue(ReminderFoldScheduling.isSeparateFoldableReminder(habitBlock))
        assertFalse(ReminderFoldScheduling.isSeparateFoldableReminder(focusBlock))
    }

    @Test
    fun `detects foldable alarm ids without request type`() {
        assertTrue(ReminderFoldScheduling.isSeparateFoldableReminderAlarmId("task:abc"))
        assertTrue(
            ReminderFoldScheduling.isSeparateFoldableReminderAlarmId("daydial:2026-07-03:habit-h1:start")
        )
        assertFalse(ReminderFoldScheduling.isSeparateFoldableReminderAlarmId("daydial:2026-07-03:day:review"))
    }
}
