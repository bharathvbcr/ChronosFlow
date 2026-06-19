package com.ChronosFlow.VBCR.core.notifications

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReconcileSchedulerTest {
    @Test
    fun `reconcile work is kept and deferred to avoid startup job ANRs`() {
        assertEquals(ExistingWorkPolicy.KEEP, ReminderReconcileScheduler.reconcileWorkPolicy)
        assertTrue(ReminderReconcileScheduler.reconcileInitialDelaySeconds >= 60L)
    }
}
