package com.ChronosFlow.VBCR.core.notifications

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusAdvanceLaunchTest {

    @Test
    fun `focus advance flag round-trips through parse`() {
        val intent = Intent().apply {
            putExtra(EXTRA_INITIAL_SECTION, SECTION_FOCUS)
            putExtra(EXTRA_FOCUS_ADVANCE, true)
        }

        val launch = parseNotificationLaunch(intent)

        assertEquals(SECTION_FOCUS, launch?.section)
        assertTrue(launch?.focusAdvance == true)
    }

    @Test
    fun `focus launch without advance flag defaults to false`() {
        val intent = Intent().apply {
            putExtra(EXTRA_INITIAL_SECTION, SECTION_FOCUS)
        }

        assertFalse(parseNotificationLaunch(intent)?.focusAdvance ?: true)
    }

    @Test
    fun `consuming launch extras strips the advance flag`() {
        val intent = Intent().apply {
            putExtra(EXTRA_INITIAL_SECTION, SECTION_FOCUS)
            putExtra(EXTRA_FOCUS_ADVANCE, true)
        }

        val consumed = consumeNotificationLaunchExtras(intent)

        assertFalse(consumed.getBooleanExtra(EXTRA_FOCUS_ADVANCE, false))
        // The section is cleared too, so a recomposition can't re-fire the same advance.
        assertEquals(null, parseNotificationLaunch(consumed))
    }
}
