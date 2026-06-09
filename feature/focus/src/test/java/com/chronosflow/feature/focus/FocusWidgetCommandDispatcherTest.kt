package com.chronosflow.feature.focus

import android.content.Context
import android.content.Intent
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Test
class FocusWidgetCommandDispatcherTest {
    private fun mockIntentChain() {
        mockkConstructor(Intent::class)
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
    fun `dispatch maps unknown action to no-op`() {
        val context = mockk<Context>(relaxed = true)
        every { context.startService(any()) } returns null
        every { context.startForegroundService(any()) } returns null

        FocusWidgetCommandDispatcher().dispatch(context, "unknown")

        verify(exactly = 0) { context.startService(any()) }
        verify(exactly = 0) { context.startForegroundService(any()) }
    }

    @Test
    fun `dispatch stop command enables archive on stop`() {
        mockIntentChain()
        val context = mockk<Context>(relaxed = true)
        every { context.startService(any()) } returns null
        every { context.startForegroundService(any()) } returns null

        FocusWidgetCommandDispatcher().dispatch(context, FocusWidgetCommand.ACTION_STOP)

        verify(exactly = 1) { context.startService(any()) }
        verify(exactly = 0) { context.startForegroundService(any()) }
        verify(exactly = 1) { anyConstructed<Intent>().setAction(FocusService.ACTION_STOP) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_ARCHIVE_ON_STOP, true) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_TIME_LEFT_SECONDS, 0) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(FocusService.EXTRA_TOTAL_SECONDS, 0) }
    }
}
