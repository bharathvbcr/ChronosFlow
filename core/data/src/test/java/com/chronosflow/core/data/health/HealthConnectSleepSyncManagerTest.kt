package com.chronosflow.core.data.health

import android.content.Context
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import com.chronosflow.core.domain.model.SleepSource
import com.chronosflow.core.domain.model.SleepTrack
import com.chronosflow.core.domain.repository.SleepTrackRepository
import com.chronosflow.core.domain.usecase.RecordSleepUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HealthConnectSleepSyncManagerTest {

    private val dataSource: HealthConnectSleepDataSource = mockk()
    private val repository: SleepTrackRepository = mockk(relaxed = true)
    private val recordSleepUseCase: RecordSleepUseCase = mockk(relaxed = true)
    private val preferences: ChronosPreferencesDataSource = mockk(relaxed = true)
    private val manager = HealthConnectSleepSyncManager(
        context = mockk<Context>(relaxed = true),
        dataSource = dataSource,
        sleepTrackRepository = repository,
        recordSleepUseCase = recordSleepUseCase,
        preferences = preferences
    )

    private val overnight = SleepSessionRecord(
        startTime = Instant.parse("2026-06-11T23:00:00Z"),
        startZoneOffset = ZoneOffset.UTC,
        endTime = Instant.parse("2026-06-12T07:00:00Z"),
        endZoneOffset = ZoneOffset.UTC,
        stages = emptyList(),
        metadata = Metadata.manualEntry()
    )

    /** Local imported row for the night [overnight] folds into (id, date, measured fields match it). */
    private val importedNight = SleepTrack(
        id = "hc-2026-06-12",
        date = LocalDate.of(2026, 6, 12),
        plannedStartMinute = null,
        plannedEndMinute = null,
        actualStartMinute = 23 * 60,
        actualEndMinute = 7 * 60,
        sleepQuality = 3,
        windDownNotes = null,
        interruptedCount = 0,
        source = SleepSource.HEALTH_CONNECT
    )

    /** No token persisted yet → runSync takes the seed path (full window reconcile + arm a token). */
    private fun availableWithSession() {
        every { dataSource.isAvailable() } returns true
        coEvery { dataSource.hasSleepReadPermission() } returns true
        coEvery { dataSource.readSessions(any(), any()) } returns listOf(overnight)
        coEvery { dataSource.changesToken() } returns "fresh-token"
        every { preferences.getString(any(), any()) } returns ""
    }

    @Test
    fun `imports a new night stamped HEALTH_CONNECT when no row exists`() = runTest {
        availableWithSession()
        coEvery { repository.observeForDate(any()) } returns flowOf(null)
        val saved = slot<SleepTrack>()
        coEvery { recordSleepUseCase(capture(saved)) } just Runs

        val outcome = manager.runSync()

        assertEquals(HealthConnectSleepSyncOutcome.Success(1), outcome)
        assertEquals(SleepSource.HEALTH_CONNECT, saved.captured.source)
    }

    @Test
    fun `never overwrites an existing manual night`() = runTest {
        availableWithSession()
        val manual = SleepTrack(
            id = "user-1",
            date = LocalDate.of(2026, 6, 12),
            plannedStartMinute = null,
            plannedEndMinute = null,
            actualStartMinute = 1380,
            actualEndMinute = 420,
            sleepQuality = 5,
            windDownNotes = "Read before bed",
            interruptedCount = 0,
            source = SleepSource.MANUAL
        )
        coEvery { repository.observeForDate(any()) } returns flowOf(manual)

        val outcome = manager.runSync()

        assertEquals(HealthConnectSleepSyncOutcome.Success(0), outcome)
        coVerify(exactly = 0) { recordSleepUseCase(any()) }
    }

    @Test
    fun `skips without retrying when sleep permission is not granted`() = runTest {
        every { dataSource.isAvailable() } returns true
        coEvery { dataSource.hasSleepReadPermission() } returns false

        val outcome = manager.runSync()

        assert(outcome is HealthConnectSleepSyncOutcome.Skipped)
        coVerify(exactly = 0) { repository.upsert(any()) }
    }

    @Test
    fun `classifies a security exception as a non-retryable skip`() = runTest {
        every { dataSource.isAvailable() } returns true
        coEvery { dataSource.hasSleepReadPermission() } returns true
        coEvery { dataSource.readSessions(any(), any()) } throws SecurityException("revoked")

        val outcome = manager.runSync()

        assert(outcome is HealthConnectSleepSyncOutcome.Skipped)
    }

    @Test
    fun `idle incremental cycle reads nothing and writes nothing`() = runTest {
        every { dataSource.isAvailable() } returns true
        coEvery { dataSource.hasSleepReadPermission() } returns true
        every { preferences.getString(any(), any()) } returns "stored-token"
        coEvery { dataSource.changesSince("stored-token") } returns
            SleepChangesResult.Changes(upserted = emptyList(), hasDeletions = false, nextToken = "next-token")

        val outcome = manager.runSync()

        assertEquals(HealthConnectSleepSyncOutcome.Success(0), outcome)
        coVerify(exactly = 0) { dataSource.readSessions(any(), any()) }
        coVerify(exactly = 0) { recordSleepUseCase(any()) }
        verify { preferences.putString("health_connect.sleep_sync.changes_token", "next-token") }
    }

    @Test
    fun `a deletion upstream removes the matching imported night`() = runTest {
        every { dataSource.isAvailable() } returns true
        coEvery { dataSource.hasSleepReadPermission() } returns true
        every { preferences.getString(any(), any()) } returns "stored-token"
        coEvery { dataSource.changesSince("stored-token") } returns
            SleepChangesResult.Changes(upserted = emptyList(), hasDeletions = true, nextToken = "next-token")
        // Health Connect now reports no sessions in the window, but a stale imported row lingers.
        coEvery { dataSource.readSessions(any(), any()) } returns emptyList()
        coEvery { repository.getForDateRange(any(), any()) } returns listOf(importedNight)

        val outcome = manager.runSync()

        assertEquals(HealthConnectSleepSyncOutcome.Success(1), outcome)
        coVerify(exactly = 1) { repository.delete(importedNight.id) }
    }

    @Test
    fun `does not delete a manual night when its imported session disappears`() = runTest {
        every { dataSource.isAvailable() } returns true
        coEvery { dataSource.hasSleepReadPermission() } returns true
        every { preferences.getString(any(), any()) } returns "stored-token"
        coEvery { dataSource.changesSince("stored-token") } returns
            SleepChangesResult.Changes(upserted = emptyList(), hasDeletions = true, nextToken = "next-token")
        coEvery { dataSource.readSessions(any(), any()) } returns emptyList()
        coEvery { repository.getForDateRange(any(), any()) } returns
            listOf(importedNight.copy(id = "user-1", source = SleepSource.MANUAL))

        manager.runSync()

        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `re-importing an unchanged night does not rewrite it`() = runTest {
        every { dataSource.isAvailable() } returns true
        coEvery { dataSource.hasSleepReadPermission() } returns true
        every { preferences.getString(any(), any()) } returns "stored-token"
        coEvery { dataSource.changesSince("stored-token") } returns
            SleepChangesResult.Changes(upserted = listOf(overnight), hasDeletions = false, nextToken = "next-token")
        coEvery { dataSource.readSessions(any(), any()) } returns listOf(overnight)
        // The local row already matches what the import folds to, so nothing should be written.
        coEvery { repository.observeForDate(any()) } returns flowOf(importedNight)

        val outcome = manager.runSync()

        assertEquals(HealthConnectSleepSyncOutcome.Success(0), outcome)
        coVerify(exactly = 0) { recordSleepUseCase(any()) }
    }

    @Test
    fun `an expired token falls back to a full reconcile and re-arms`() = runTest {
        availableWithSession()
        every { preferences.getString(any(), any()) } returns "stale-token"
        coEvery { dataSource.changesSince("stale-token") } returns SleepChangesResult.Expired
        coEvery { repository.observeForDate(any()) } returns flowOf(null)

        val outcome = manager.runSync()

        assertEquals(HealthConnectSleepSyncOutcome.Success(1), outcome)
        coVerify(exactly = 1) { recordSleepUseCase(any()) }
    }
}
