package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StableNotificationCodesTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `same key maps to the same code across instances`() {
        val first = StableNotificationCodes(context)
        val second = StableNotificationCodes(context)
        val a1 = first.codeFor("notif:req-1")
        val a2 = second.codeFor("notif:req-1")
        assertEquals(a1, a2)
    }

    @Test
    fun `distinct keys never collide across a large allocation burst`() {
        val codes = StableNotificationCodes(context)
        val allocated = HashSet<Int>()
        val keys = (1..2_000).map { "entity-$it" }
        keys.forEach { key ->
            val code = codes.codeFor(key)
            assertTrue(
                "collision for key=$key code=$code",
                allocated.add(code)
            )
        }
    }

    @Test
    fun `counter self-heals after a process death lost the counter write`() {
        // First instance allocates 5 codes; simulate a crash between mapping and counter writes
        // by removing the counter key directly from the backing prefs.
        val first = StableNotificationCodes(context)
        (1..5).forEach { first.codeFor("heal-$it") }
        context.getSharedPreferences("chronos_notification_codes", Context.MODE_PRIVATE)
            .edit().remove("counter").commit()

        val second = StableNotificationCodes(context)
        val next = second.codeFor("heal-new")

        // Must not reuse any already-mapped code despite the lost counter.
        (1..5).forEach { assertNotEquals(next, second.codeFor("heal-$it")) }
    }

    @Test
    fun `released keys can be reallocated without breaking existing mappings`() {
        val codes = StableNotificationCodes(context)
        val keep = codes.codeFor("keep-1")
        val transient = codes.codeFor("transient-1")
        codes.release("transient-1")
        assertNotEquals(keep, codes.codeFor("other-1"))
        assertEquals(keep, codes.codeFor("keep-1"))
    }

    @Test
    fun `blank keys are rejected`() {
        val codes = StableNotificationCodes(context)
        assertThrows(IllegalArgumentException::class.java) { codes.codeFor(" ") }
    }
}
