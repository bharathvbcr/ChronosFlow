package com.chronosflow.core.data.sync

import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarSyncStatusStoreTest {
    private val key = "calendar_sync.last_success_at_millis"

    private fun preferencesReturning(stored: Long): ChronosPreferencesDataSource =
        mockk(relaxed = true) {
            every { getLong(key, -1L) } returns stored
        }

    @Test
    fun `seeds the snapshot and stream from the persisted value`() {
        val store = CalendarSyncStatusStore(preferencesReturning(1_700_000_000_000L))

        assertEquals(1_700_000_000_000L, store.lastSuccessfulSyncAtMillis())
        assertEquals(1_700_000_000_000L, store.observeLastSuccessfulSyncAtMillis().value)
    }

    @Test
    fun `is null before any sync`() {
        val store = CalendarSyncStatusStore(preferencesReturning(-1L))

        assertNull(store.lastSuccessfulSyncAtMillis())
        assertNull(store.observeLastSuccessfulSyncAtMillis().value)
    }

    @Test
    fun `recordSuccessfulSync persists and emits the clock timestamp`() {
        val preferences = preferencesReturning(-1L)
        val store = CalendarSyncStatusStore(preferences).apply { clock = { 1_700_000_000_000L } }

        store.recordSuccessfulSync()

        verify { preferences.putLong(key, 1_700_000_000_000L) }
        assertEquals(1_700_000_000_000L, store.observeLastSuccessfulSyncAtMillis().value)
    }
}
