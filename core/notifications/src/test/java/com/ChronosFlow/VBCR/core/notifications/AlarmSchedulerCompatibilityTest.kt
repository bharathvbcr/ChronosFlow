package com.ChronosFlow.VBCR.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSchedulerCompatibilityTest {
    @Test
    fun `uses exact alarm path when notification and exact permissions are available`() {
        val path = resolveAlarmSchedulePath(
            canPostReminders = true,
            isFutureTime = true,
            canScheduleExactAlarms = true,
            allowFallback = true
        )

        assertEquals(AlarmSchedulePath.EXACT, path)
    }

    @Test
    fun `degrades to inexact fallback when exact alarm permission is revoked mid session`() {
        val path = resolveAlarmSchedulePath(
            canPostReminders = true,
            isFutureTime = true,
            canScheduleExactAlarms = false,
            allowFallback = true
        )

        assertEquals(AlarmSchedulePath.INEXACT_FALLBACK, path)
    }

    @Test
    fun `blocks exact-only reminders when exact alarm permission is revoked`() {
        val path = resolveAlarmSchedulePath(
            canPostReminders = true,
            isFutureTime = true,
            canScheduleExactAlarms = false,
            allowFallback = false
        )

        assertEquals(AlarmSchedulePath.EXACT_DENIED, path)
    }

    @Test
    fun `blocks reminders when notification permission is missing`() {
        val path = resolveAlarmSchedulePath(
            canPostReminders = false,
            isFutureTime = true,
            canScheduleExactAlarms = true,
            allowFallback = true
        )

        assertEquals(AlarmSchedulePath.PERMISSION_DENIED, path)
    }

    @Test
    fun `skips reminders scheduled in the past before checking exact alarm fallback`() {
        val path = resolveAlarmSchedulePath(
            canPostReminders = true,
            isFutureTime = false,
            canScheduleExactAlarms = false,
            allowFallback = true
        )

        assertEquals(AlarmSchedulePath.PAST_TIME, path)
    }
}
