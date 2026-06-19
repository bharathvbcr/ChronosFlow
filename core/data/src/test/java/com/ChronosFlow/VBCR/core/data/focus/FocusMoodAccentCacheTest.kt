package com.ChronosFlow.VBCR.core.data.focus

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FocusMoodAccentCacheTest {
    private val stored = mutableMapOf<String, Any?>()

    @Before
    fun setup() {
        stored.clear()
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.getInt(any(), any()) } answers {
            stored[firstArg()] as? Int ?: secondArg()
        }
        every { prefs.edit() } returns editor
        every { editor.putInt(any(), any()) } answers {
            stored[firstArg()] = secondArg()
            editor
        }
        every { editor.apply() } just Runs
        val context = mockk<Context>()
        every {
            context.getSharedPreferences("chronos_focus_mood_accent", Context.MODE_PRIVATE)
        } returns prefs
        cache = FocusMoodAccentCache(context)
    }

    private lateinit var cache: FocusMoodAccentCache

    @Test
    fun `block-specific scores override general cache`() {
        cache.save(blockId = "b-1", moodScore = 5, energyScore = 4)
        cache.save(blockId = null, moodScore = 2, energyScore = 2)
        assertEquals(5 to 4, cache.moodEnergyForBlock("b-1"))
        assertEquals(2 to 2, cache.moodEnergyForBlock(null))
    }
}
