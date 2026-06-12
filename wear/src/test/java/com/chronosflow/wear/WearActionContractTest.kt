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
