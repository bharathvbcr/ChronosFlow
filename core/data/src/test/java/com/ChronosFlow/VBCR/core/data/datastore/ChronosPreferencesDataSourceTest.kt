package com.ChronosFlow.VBCR.core.data.datastore

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ChronosPreferencesDataSourceTest {
    private val context: Context = mockk()
    private val preferences: SharedPreferences = mockk()
    private val editor: SharedPreferences.Editor = mockk()
    private lateinit var dataSource: ChronosPreferencesDataSource

    @Before
    fun setUp() {
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } just runs

        dataSource = ChronosPreferencesDataSource(context)
    }

    @Test
    fun `boolean getter uses default fallback value`() {
        every { preferences.getBoolean("feature-enabled", false) } returns true

        assertEquals(true, dataSource.getBoolean("feature-enabled", false))
    }

    @Test
    fun `string getter returns fallback when shared preferences stores null`() {
        every { preferences.getString("notes", "fallback") } returns null

        assertEquals("fallback", dataSource.getString("notes", "fallback"))
    }

    @Test
    fun `boolean setter writes via shared preferences editor`() {
        dataSource.putBoolean("feature-enabled", true)

        verify {
            editor.putBoolean("feature-enabled", true)
            editor.apply()
        }
    }

    @Test
    fun `string setter writes via shared preferences editor`() {
        dataSource.putString("nickname", "ChronosFlow")

        verify {
            editor.putString("nickname", "ChronosFlow")
            editor.apply()
        }
    }

    @Test
    fun `remove clears a key`() {
        dataSource.remove("legacy-flag")

        verify {
            editor.remove("legacy-flag")
            editor.apply()
        }
    }
}

