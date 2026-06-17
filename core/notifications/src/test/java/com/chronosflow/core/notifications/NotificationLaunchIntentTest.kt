package com.chronosflow.core.notifications

import com.chronosflow.core.domain.model.AlarmRequestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationLaunchIntentTest {
    @Test
    fun `medication receiver opens medication screen`() {
        val launch = resolveNotificationLaunch(
            requestId = "any-id",
            receiverClass = MedicationAlarmReceiver::class.java
        )
        assertEquals(SECTION_MEDICATION, launch.section)
    }

    @Test
    fun `daily review reminder opens review screen`() {
        val launch = resolveNotificationLaunch(
            requestId = "daydial:2026-05-24:day:review",
            receiverClass = AlarmReceiver::class.java
        )
        assertEquals(SECTION_REVIEW, launch.section)
    }

    @Test
    fun `sleep journal log reminder opens sleep log sheet`() {
        val launch = resolveNotificationLaunch(
            requestId = "daydial:2026-05-24:day:logsleep",
            receiverClass = AlarmReceiver::class.java
        )
        assertEquals(SECTION_DAY, launch.section)
        assertEquals(DAY_TARGET_SLEEP, launch.dayTarget)
    }

    @Test
    fun `block reminder opens focus screen with block id`() {
        val launch = resolveNotificationLaunch(
            requestId = "daydial:2026-05-24:block-42:start",
            receiverClass = AlarmReceiver::class.java
        )
        assertEquals(SECTION_FOCUS, launch.section)
        assertEquals("block-42", launch.focusBlockId)
    }

    @Test
    fun `alarm request type routes medication and review`() {
        assertEquals(
            SECTION_MEDICATION,
            resolveNotificationLaunch("uuid", AlarmRequestType.MEDICATION).section
        )
        // Evening companion: the daily-review tap lands on the journal sheet.
        val dailyReview = resolveNotificationLaunch(
            "daydial:2026-05-24:day:review",
            AlarmRequestType.DAILY_REVIEW
        )
        assertEquals(SECTION_DAY, dailyReview.section)
        assertEquals(DAY_TARGET_JOURNAL, dailyReview.dayTarget)
        // The sleep & journal log nudge lands on the sleep log sheet.
        val logReminder = resolveNotificationLaunch(
            "daydial:2026-05-24:day:logsleep",
            AlarmRequestType.LOG_REMINDER
        )
        assertEquals(SECTION_DAY, logReminder.section)
        assertEquals(DAY_TARGET_SLEEP, logReminder.dayTarget)
        assertEquals(
            SECTION_TASKS,
            resolveNotificationLaunch("task:abc", AlarmRequestType.URGENT_TASK).section
        )
        assertEquals(
            "abc",
            resolveNotificationLaunch("task:abc", AlarmRequestType.URGENT_TASK).taskId
        )
    }

    @Test
    fun `habit-style daydial id still routes to focus launch`() {
        assertEquals(
            SECTION_FOCUS,
            resolveNotificationLaunch("daydial:2026-05-24:habit-journal:start", AlarmRequestType.BLOCK_START).section
        )
        assertEquals(
            "habit-journal",
            resolveNotificationLaunch("daydial:2026-05-24:habit-journal:start", AlarmRequestType.BLOCK_START).focusBlockId
        )
    }

    @Test
    fun `task reminder opens tasks section`() {
        val launch = resolveNotificationLaunch(
            requestId = "task:abc",
            receiverClass = AlarmReceiver::class.java
        )

        assertEquals(SECTION_TASKS, launch.section)
        assertEquals("abc", launch.taskId)
    }

    @Test
    fun `task context launch carries task target`() {
        val launch = NotificationLaunch(
            section = SECTION_TASKS,
            taskId = "abc",
            target = TASK_LAUNCH_TARGET_CONTEXT
        )

        assertEquals("abc", launch.taskId)
        assertEquals(TASK_LAUNCH_TARGET_CONTEXT, launch.target)
    }

    @Test
    fun `shared task capture prefers primary text and trims`() {
        assertEquals("Buy milk", sharedTaskCapture("  Buy milk  ", "subject"))
    }

    @Test
    fun `shared task capture falls back to subject when text is blank`() {
        assertEquals("Page title", sharedTaskCapture("   ", "Page title"))
        assertEquals("Page title", sharedTaskCapture(null, "Page title"))
    }

    @Test
    fun `shared task capture returns null when nothing usable`() {
        assertNull(sharedTaskCapture(null, null))
        assertNull(sharedTaskCapture("  ", "  "))
    }

    @Test
    fun `shared task capture caps very long text`() {
        val long = "x".repeat(SHARED_CAPTURE_MAX_LENGTH + 500)
        assertEquals(SHARED_CAPTURE_MAX_LENGTH, sharedTaskCapture(long, null)?.length)
    }
}
