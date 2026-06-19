package com.ChronosFlow.VBCR.core.notifications

import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderBootReceiverCompatibilityTest {
    @Test
    fun `restores reminders after reboot and locked boot broadcasts`() {
        assertTrue(isReminderRestoreAction(ACTION_BOOT_COMPLETED))
        assertTrue(isReminderRestoreAction(ACTION_LOCKED_BOOT_COMPLETED))
    }

    @Test
    fun `restores reminders after time and timezone changes`() {
        assertTrue(isReminderRestoreAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(isReminderRestoreAction(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test
    fun `restores reminders after app update and exact alarm permission changes`() {
        assertTrue(isReminderRestoreAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(isReminderRestoreAction(ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
    }

    @Test
    fun `ignores unrelated broadcasts`() {
        assertFalse(isReminderRestoreAction(null))
        assertFalse(isReminderRestoreAction(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        assertFalse(isReminderRestoreAction("com.ChronosFlow.VBCR.UNRELATED"))
    }
}
