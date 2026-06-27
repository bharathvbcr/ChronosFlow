package com.ChronosFlow.VBCR.core.data.focus

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusPhaseAdvanceBusTest {

    @Test
    fun `request issued before collection is still delivered`() = runTest {
        val bus = FocusPhaseAdvanceBus()
        // Notification tap can land before the ViewModel subscribes — the conflated channel buffers it.
        bus.requestAdvance()

        val delivered = withTimeoutOrNull(1_000) { bus.requests.first() }

        assertNotNull("a request issued before collection should still arrive", delivered)
    }

    @Test
    fun `request is delivered exactly once and not replayed`() = runTest {
        val bus = FocusPhaseAdvanceBus()
        bus.requestAdvance()

        bus.requests.test {
            awaitItem() // the single buffered request
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        // A fresh collector must NOT see the already-consumed request replayed (no double-advance).
        val replayed = withTimeoutOrNull(300) { bus.requests.first() }
        assertNull("a consumed request must not replay to a later collector", replayed)
    }
}
