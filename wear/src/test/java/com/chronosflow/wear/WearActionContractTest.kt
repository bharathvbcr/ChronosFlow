package com.chronosflow.wear

import com.chronosflow.core.domain.wear.WearActionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the watch→phone action encoding, the cross-device seam between the two apps. */
class WearActionContractTest {

    @Test
    fun `encode then decode round-trips type and arg`() {
        val payload = WearActionContract.encode(WearActionContract.TYPE_HABIT, "habit-42")
        assertEquals(WearActionContract.TYPE_HABIT to "habit-42", WearActionContract.decode(payload))
    }

    @Test
    fun `decode rejects a malformed payload`() {
        assertNull(WearActionContract.decode("no-separator".toByteArray()))
    }

    @Test
    fun `sync request round-trips with an empty arg`() {
        // The watch sends a bare sync request (no arg) when it opens; the phone must still decode
        // it so its listener can re-publish the day summary.
        val payload = WearActionContract.encode(WearActionContract.TYPE_SYNC, "")
        assertEquals(WearActionContract.TYPE_SYNC to "", WearActionContract.decode(payload))
    }

    @Test
    fun `focusStart encodes an explicit duration but stays plain without one`() {
        assertEquals("start", WearActionContract.focusStart(null))
        assertEquals("start", WearActionContract.focusStart(0))
        assertEquals("start:1500", WearActionContract.focusStart(1500))
    }

    @Test
    fun `isFocusStart and focusStartSeconds read the duration back`() {
        assertTrue(WearActionContract.isFocusStart("start"))
        assertTrue(WearActionContract.isFocusStart("start:1500"))
        assertFalse(WearActionContract.isFocusStart(WearActionContract.FOCUS_PAUSE))

        assertEquals(1500, WearActionContract.focusStartSeconds("start:1500"))
        assertNull(WearActionContract.focusStartSeconds("start"))
    }
}
