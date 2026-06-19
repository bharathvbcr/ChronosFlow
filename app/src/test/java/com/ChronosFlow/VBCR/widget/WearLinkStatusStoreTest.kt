package com.ChronosFlow.VBCR.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Locks the Wear link timestamps + the background-refresh keep-alive window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearLinkStatusStoreTest {

    private val store = WearLinkStatusStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `last published defaults to zero and records the stamp`() {
        assertEquals(0L, store.lastPublishedAtMillis())
        store.recordPublished(1_000L)
        assertEquals(1_000L, store.lastPublishedAtMillis())
    }

    @Test
    fun `watch is never active before any activity is recorded`() {
        assertEquals(0L, store.lastWatchActivityMillis())
        assertFalse(store.watchActiveWithin(windowMillis = 60_000L, nowMillis = 10_000L))
    }

    @Test
    fun `watch counts as active only within the window`() {
        store.recordWatchActivity(nowMillis = 1_000L)

        // Inside the window (and exactly on its edge) the watch keeps the loop alive.
        assertTrue(store.watchActiveWithin(windowMillis = 5_000L, nowMillis = 3_000L))
        assertTrue(store.watchActiveWithin(windowMillis = 5_000L, nowMillis = 6_000L))
        // Past the window it no longer does, so the refresh loop can self-terminate.
        assertFalse(store.watchActiveWithin(windowMillis = 5_000L, nowMillis = 6_001L))
    }
}
