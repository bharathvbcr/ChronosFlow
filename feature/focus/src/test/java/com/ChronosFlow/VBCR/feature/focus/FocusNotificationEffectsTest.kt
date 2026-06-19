package com.ChronosFlow.VBCR.feature.focus

import android.content.Context
import android.content.Intent
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusNotificationEffectsTest {
    private fun mockIntentChain() {
        io.mockk.mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setAction(any()) } answers { call ->
            call.invocation.self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any(), any<String>()) } answers { call ->
            call.invocation.self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any(), any<Int>()) } answers { call ->
            call.invocation.self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any(), any<Boolean>()) } answers { call ->
            call.invocation.self as Intent
        }
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `stop action sends command to startService with all configured extras`() {
        mockIntentChain()
        val context = mockk<Context>(relaxed = true)
        var sentIntent: Intent? = null
        var startForegroundCalls = 0
        var startCalls = 0
        every { context.startService(any()) } answers {
            startCalls++
            sentIntent = firstArg()
            null
        }
        every { context.startForegroundService(any()) } answers {
            startForegroundCalls++
            null
        }

        context.sendFocusServiceCommand(
            action = FocusService.ACTION_STOP,
            timeLeft = 123,
            totalSeconds = 456,
            sessionId = "session-1",
            blockId = "block-1",
            archiveOnStop = true,
            logActualOnStop = false,
            wasSkip = true,
            adjustSeconds = 77
        )

        val intent = sentIntent
        assertTrue(intent != null)
        verify(exactly = 1) { anyConstructed<Intent>().setAction(FocusService.ACTION_STOP) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_SESSION_ID, "session-1") }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_BLOCK_ID, "block-1") }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_TIME_LEFT_SECONDS, 123) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_TOTAL_SECONDS, 456) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_ARCHIVE_ON_STOP, true) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_LOG_ACTUAL_ON_STOP, false) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_WAS_SKIP, true) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_ADJUST_SECONDS, 77) }
        assertEquals(1, startCalls)
        assertEquals(0, startForegroundCalls)
    }

    @Test
    fun `non-stop action dispatches through a service-starting API path`() {
        mockIntentChain()
        val context = mockk<Context>(relaxed = true)
        var foregroundIntent: Intent? = null
        var serviceIntent: Intent? = null
        var foregroundCalls = 0
        var serviceCalls = 0
        every { context.startService(any()) } answers {
            serviceCalls++
            serviceIntent = firstArg()
            null
        }
        every { context.startForegroundService(any()) } answers {
            foregroundCalls++
            foregroundIntent = firstArg()
            null
        }

        context.sendFocusServiceCommand(
            action = FocusService.ACTION_START,
            timeLeft = 333,
            totalSeconds = 999,
            sessionId = "session-2",
            blockId = null,
            archiveOnStop = false,
            logActualOnStop = true,
            wasSkip = false,
            adjustSeconds = null
        )

        assertTrue(foregroundIntent != null || serviceIntent != null)
        verify(exactly = 1) { anyConstructed<Intent>().setAction(FocusService.ACTION_START) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_SESSION_ID, "session-2") }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_TIME_LEFT_SECONDS, 333) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_TOTAL_SECONDS, 999) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_ARCHIVE_ON_STOP, false) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_LOG_ACTUAL_ON_STOP, true) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_WAS_SKIP, false) }
        assertEquals(1, foregroundCalls + serviceCalls)
    }
}
