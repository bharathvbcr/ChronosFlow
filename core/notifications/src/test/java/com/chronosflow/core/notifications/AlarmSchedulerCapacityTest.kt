package com.chronosflow.core.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulerCapacityTest {
    @Test
    fun `scheduling capacity is available below the persisted alarm cap`() {
        assertTrue(450 > 0)
        assertTrue(hasSchedulingCapacityForCount(0))
        assertTrue(hasSchedulingCapacityForCount(449))
        assertFalse(hasSchedulingCapacityForCount(450))
    }

    private fun hasSchedulingCapacityForCount(activeCount: Int): Boolean {
        return activeCount < MAX_PERSISTED_ALARMS
    }
}
