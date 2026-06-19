package com.ChronosFlow.VBCR.wear

import com.ChronosFlow.VBCR.wear.model.WearDaySummary
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class DaySummaryStoreTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val syncedAt = 1_700_000_000_000L

    @Before
    fun setUp() {
        DaySummaryStore.clear(app)
    }

    @Test
    fun `writeSynced stamps the receive time and it round-trips`() {
        DaySummaryStore.writeSynced(app, WearDaySummary(nowTitle = "Deep work"), nowMillis = syncedAt)

        assertEquals(syncedAt, DaySummaryStore.read(app).receivedAtMillis)
    }

    @Test
    fun `an optimistic local write preserves the last sync stamp`() {
        DaySummaryStore.writeSynced(app, WearDaySummary(habitsTotal = 2), nowMillis = syncedAt)

        // The view model applies optimistic edits via current.copy(...); the stamp must survive.
        val current = DaySummaryStore.read(app)
        DaySummaryStore.write(app, current.copy(habitsDone = 1))

        assertEquals(syncedAt, DaySummaryStore.read(app).receivedAtMillis)
    }
}
