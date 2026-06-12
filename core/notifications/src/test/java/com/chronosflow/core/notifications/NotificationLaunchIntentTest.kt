package com.chronosflow.core.notifications

import com.chronosflow.core.domain.model.AlarmRequestType
import org.junit.Assert.assertEquals
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
}
