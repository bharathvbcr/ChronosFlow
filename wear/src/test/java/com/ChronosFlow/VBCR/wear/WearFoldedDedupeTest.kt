package com.ChronosFlow.VBCR.wear

import com.ChronosFlow.VBCR.wear.model.WearFoldedReminder
import com.ChronosFlow.VBCR.wear.model.WearFoldedReminderKind
import com.ChronosFlow.VBCR.wear.model.dedupeFoldedRemindersAgainstTopTask
import org.junit.Assert.assertEquals
import org.junit.Test

class WearFoldedDedupeTest {
    private val taskFold = WearFoldedReminder(
        kind = WearFoldedReminderKind.TASK,
        entityId = "t1",
        title = "File taxes",
        detail = "Due 9:00 AM",
        isOverdue = true
    )
    private val medFold = WearFoldedReminder(
        kind = WearFoldedReminderKind.MEDICATION,
        entityId = "m1",
        title = "Aspirin",
        detail = "Due now",
        isOverdue = false
    )

    @Test
    fun dropsMatchingTopTaskFold() {
        val result = dedupeFoldedRemindersAgainstTopTask("t1", listOf(taskFold, medFold))
        assertEquals(listOf(medFold), result)
    }

    @Test
    fun keepsAllWhenTopTaskDiffers() {
        val result = dedupeFoldedRemindersAgainstTopTask("t2", listOf(taskFold, medFold))
        assertEquals(listOf(taskFold, medFold), result)
    }

    @Test
    fun noOpWhenNoTopTask() {
        val folded = listOf(taskFold)
        assertEquals(folded, dedupeFoldedRemindersAgainstTopTask(null, folded))
        assertEquals(folded, dedupeFoldedRemindersAgainstTopTask("", folded))
    }
}
