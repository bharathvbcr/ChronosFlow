package com.ChronosFlow.VBCR.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldedActionSlotsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val codes = StableNotificationCodes(context)

    @Test
    fun `empty folded list yields no action slots`() {
        val slots = buildFoldedActionSlots(context, emptyList(), maxFoldedActions = 2, codes = codes)
        assertNull(slots.primaryIntent)
        assertNull(slots.primaryLabel)
        assertNull(slots.secondaryIntent)
    }

    @Test
    fun `maxFoldedActions zero yields no slots even with candidates`() {
        val slots = buildFoldedActionSlots(context, listOf(reminder("med-1")), maxFoldedActions = 0, codes = codes)
        assertNull(slots.primaryIntent)
        assertNull(slots.secondaryIntent)
    }

    @Test
    fun `maxFoldedActions one exposes only the primary chip`() {
        val folded = listOf(
            reminder("med-1", FoldedReminderKind.MEDICATION),
            reminder("task-1", FoldedReminderKind.TASK)
        )
        val slots = buildFoldedActionSlots(context, folded, maxFoldedActions = 1, codes = codes)
        assertNotNull(slots.primaryIntent)
        assertEquals(context.getString(R.string.folded_reminder_action_take), slots.primaryLabel)
        assertNull(slots.secondaryIntent)
        assertNull(slots.secondaryLabel)
    }

    @Test
    fun `maxFoldedActions two exposes primary and secondary chips`() {
        val folded = listOf(
            reminder("med-1", FoldedReminderKind.MEDICATION),
            reminder("task-1", FoldedReminderKind.TASK),
            reminder("habit-1", FoldedReminderKind.HABIT)
        )
        val slots = buildFoldedActionSlots(context, folded, maxFoldedActions = 2, codes = codes)
        assertNotNull(slots.primaryIntent)
        assertNotNull(slots.secondaryIntent)
        assertEquals(context.getString(R.string.folded_reminder_action_complete), slots.secondaryLabel)
    }

    @Test
    fun `request codes are stable and distinct per entity`() {
        val medA = reminder("med-a", FoldedReminderKind.MEDICATION)
        val medB = reminder("med-b", FoldedReminderKind.MEDICATION)
        assertEquals(
            foldedReminderActionRequestCode(medA, codes),
            foldedReminderActionRequestCode(medA, codes)
        )
        assert(
            foldedReminderActionRequestCode(medA, codes) != foldedReminderActionRequestCode(medB, codes)
        )
    }

    private fun reminder(
        id: String,
        kind: FoldedReminderKind = FoldedReminderKind.MEDICATION
    ): FoldedReminder = FoldedReminder(
        kind = kind,
        entityId = id,
        title = id,
        detail = "due",
        dueMinute = 9 * 60,
        isOverdue = false
    )
}
