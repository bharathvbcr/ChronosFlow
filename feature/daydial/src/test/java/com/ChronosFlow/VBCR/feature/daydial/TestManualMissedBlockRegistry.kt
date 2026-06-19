package com.ChronosFlow.VBCR.feature.daydial

import android.content.Context
import android.content.SharedPreferences
import com.ChronosFlow.VBCR.core.data.focus.ManualMissedBlockRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs

internal fun testManualMissedBlockRegistry(): ManualMissedBlockRegistry {
    var storedIds: Set<String>? = emptySet()
    val prefs = mockk<SharedPreferences>(relaxed = true)
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    every { prefs.getStringSet("block_entries", emptySet()) } answers { storedIds }
    every { prefs.edit() } returns editor
    every { editor.putStringSet("block_entries", any()) } answers {
        @Suppress("UNCHECKED_CAST")
        storedIds = (arg(1) as Set<String>).toSet()
        editor
    }
    every { editor.apply() } just Runs
    val context = mockk<Context>()
    every {
        context.getSharedPreferences("chronos_manual_missed_blocks", Context.MODE_PRIVATE)
    } returns prefs
    return ManualMissedBlockRegistry(context)
}
