package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.domain.usecase.LogActualTimeUseCase
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.feature.daydial.FocusExecutionState
import com.ChronosFlow.VBCR.feature.daydial.FocusExecutionStatus
import com.ChronosFlow.VBCR.feature.daydial.testManualMissedBlockRegistry
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DayDialFocusDelegateSkipTest {
    @Test
    fun `skipIfActiveBlock only skips running or paused sessions for that block`() {
        val delegate = DayDialFocusDelegate(
            mockk(relaxed = true),
            mockk(relaxed = true),
            testManualMissedBlockRegistry()
        )
        delegate.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                status = FocusExecutionStatus.RUNNING
            )
        )

        delegate.skipIfActiveBlock("block-2")
        assertEquals(FocusExecutionStatus.RUNNING, delegate.focusExecutionState.value.status)

        val skipped = delegate.skipIfActiveBlock("block-1")
        assertEquals(FocusExecutionStatus.SKIPPED, delegate.focusExecutionState.value.status)
        assertEquals(true, skipped)
    }
}
