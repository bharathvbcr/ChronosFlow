package com.chronosflow.core.data.focus

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ManualMissedBlockRegistryTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var storedEntries: Set<String>? = emptySet()

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        storedEntries = emptySet()
        every { prefs.getStringSet("block_entries", emptySet()) } answers { storedEntries }
        every { prefs.edit() } returns editor
        every { editor.putStringSet("block_entries", any()) } answers {
            @Suppress("UNCHECKED_CAST")
            storedEntries = (arg(1) as Set<String>).toSet()
            editor
        }
        every { editor.apply() } just Runs
    }

    private fun registry(): ManualMissedBlockRegistry {
        val context = mockk<Context>()
        every { context.getSharedPreferences("chronos_manual_missed_blocks", Context.MODE_PRIVATE) } returns prefs
        return ManualMissedBlockRegistry(context)
    }

    @Test
    fun `mark and clear missed blocks`() {
        val registry = registry()
        val today = LocalDate.parse("2026-05-25")
        registry.markMissed("block-a", today)
        assertEquals(setOf("block-a"), registry.missedIdsForDate(today))
        registry.markMissed("block-b", today)
        assertEquals(setOf("block-a", "block-b"), registry.missedIdsForDate(today))
        registry.clearMissed("block-a", today)
        assertEquals(setOf("block-b"), registry.missedIdsForDate(today))
    }

    @Test
    fun `persisted entries reload on new registry instance`() {
        val today = LocalDate.parse("2026-05-25")
        val first = registry()
        first.markMissed("persist-me", today)
        val second = registry()
        assertEquals(setOf("persist-me"), second.missedIdsForDate(today))
    }

    @Test
    fun `missed ids are scoped to plan date`() {
        val registry = registry()
        val today = LocalDate.parse("2026-05-25")
        val tomorrow = today.plusDays(1)
        registry.markMissed("today-block", today)
        registry.markMissed("tomorrow-block", tomorrow)
        assertEquals(setOf("today-block"), registry.missedIdsForDate(today))
        assertEquals(setOf("tomorrow-block"), registry.missedIdsForDate(tomorrow))
        assertFalse(registry.missedIdsForDate(today).contains("tomorrow-block"))
    }

    @Test
    fun `markMissedFromFocusScreen marks block missed`() {
        val registry = registry()
        val today = LocalDate.parse("2026-05-25")
        registry.markMissedFromFocusScreen("block-focus", "Deep work", today)
        assertEquals(setOf("block-focus"), registry.missedIdsForDate(today))
        assertEquals("Deep work marked missed on Today", registry.missedFromFocusMessage.value)
    }

    @Test
    fun `legacy block id entries migrate to dated entries`() {
        storedEntries = setOf("legacy-block")
        val registry = registry()
        val today = LocalDate.now()
        assertTrue(registry.missedIdsForDate(today).contains("legacy-block"))
    }
}
