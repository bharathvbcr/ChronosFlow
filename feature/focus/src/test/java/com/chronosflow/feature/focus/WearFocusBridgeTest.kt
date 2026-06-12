package com.chronosflow.feature.focus

import com.chronosflow.core.domain.wear.WearFocusContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearFocusBridgeTest {

    @Test
    fun runningPayloadPublishesPlannedEndAndOmitsPausedFields() {
        val entries = runningFocusPayload(
            title = "Deep Work",
            plannedEndAtMillis = 1_700_000_000_000L,
            totalSeconds = 1_500
        ).toDataEntries()

        assertEquals(true, entries[WearFocusContract.KEY_ACTIVE])
        assertEquals(false, entries[WearFocusContract.KEY_PAUSED])
        assertEquals("Deep Work", entries[WearFocusContract.KEY_TITLE])
        assertEquals(1_700_000_000_000L, entries[WearFocusContract.KEY_PLANNED_END_AT_MILLIS])
        assertEquals(1_500, entries[WearFocusContract.KEY_TOTAL_SECONDS])
        // Per-tick / paused-only fields are absent so running updates stay de-duplicatable.
        assertNull(entries[WearFocusContract.KEY_PAUSED_TIME_LEFT_SECONDS])
    }

    @Test
    fun pausedPayloadPublishesFrozenRemainingAndOmitsPlannedEnd() {
        val entries = pausedFocusPayload(
            title = "Deep Work",
            timeLeftSeconds = 754,
            totalSeconds = 1_500
        ).toDataEntries()

        assertEquals(true, entries[WearFocusContract.KEY_ACTIVE])
        assertEquals(true, entries[WearFocusContract.KEY_PAUSED])
        assertEquals(754, entries[WearFocusContract.KEY_PAUSED_TIME_LEFT_SECONDS])
        assertNull(entries[WearFocusContract.KEY_PLANNED_END_AT_MILLIS])
    }

    @Test
    fun clearedPayloadOnlyCarriesInactiveFlag() {
        val entries = clearedFocusPayload().toDataEntries()

        assertEquals(1, entries.size)
        assertEquals(false, entries[WearFocusContract.KEY_ACTIVE])
    }

    @Test
    fun activeStateIsDistinguishableFromCleared() {
        assertTrue(runningFocusPayload("t", 1L, 1).active)
        assertTrue(pausedFocusPayload("t", 1, 1).active)
        assertFalse(clearedFocusPayload().active)
    }
}
