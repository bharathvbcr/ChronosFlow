package com.chronosflow.core.data.health

import com.chronosflow.core.domain.model.SleepSource
import com.chronosflow.core.domain.model.SleepTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Provenance rules for the read-only Health Connect importer (see [HealthConnectSleepSyncManager.mergeImported]). */
class HealthConnectSleepMergeTest {

    private val date = LocalDate.of(2026, 6, 12)

    private fun imported() = SleepTrack(
        id = "hc-$date",
        date = date,
        plannedStartMinute = null,
        plannedEndMinute = null,
        actualStartMinute = 23 * 60,
        actualEndMinute = 7 * 60,
        sleepQuality = 3,
        windDownNotes = null,
        interruptedCount = 2,
        source = SleepSource.HEALTH_CONNECT
    )

    @Test
    fun `no existing row writes the imported track as-is`() {
        val merged = HealthConnectSleepSyncManager.mergeImported(imported(), existing = null)
        assertEquals(imported(), merged)
    }

    @Test
    fun `existing manual row is never overwritten`() {
        val manual = imported().copy(
            id = "user-1",
            sleepQuality = 5,
            windDownNotes = "Read before bed",
            source = SleepSource.MANUAL
        )
        val merged = HealthConnectSleepSyncManager.mergeImported(imported(), existing = manual)
        assertNull(merged)
    }

    @Test
    fun `existing health-connect row refreshes measured fields but keeps id and attached data`() {
        val previous = SleepTrack(
            id = "hc-$date",
            date = date,
            plannedStartMinute = 1380,
            plannedEndMinute = 420,
            actualStartMinute = 22 * 60,
            actualEndMinute = 6 * 60,
            sleepQuality = 4,
            windDownNotes = "imported notes",
            interruptedCount = 0,
            source = SleepSource.HEALTH_CONNECT
        )

        val merged = HealthConnectSleepSyncManager.mergeImported(imported(), existing = previous)!!

        // Refreshed from the new import:
        assertEquals(23 * 60, merged.actualStartMinute)
        assertEquals(7 * 60, merged.actualEndMinute)
        assertEquals(2, merged.interruptedCount)
        // Kept from the existing imported row:
        assertEquals("hc-$date", merged.id)
        assertEquals(1380, merged.plannedStartMinute)
        assertEquals(420, merged.plannedEndMinute)
        assertEquals(4, merged.sleepQuality)
        assertEquals("imported notes", merged.windDownNotes)
        assertEquals(SleepSource.HEALTH_CONNECT, merged.source)
    }
}
