package com.chronosflow.core.data.usage

import android.content.Context
import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import com.chronosflow.core.domain.diagnostics.AppEventLog
import com.chronosflow.core.domain.model.AppUsageDay
import com.chronosflow.core.domain.model.AppUsageSample
import com.chronosflow.core.domain.model.UsageCategory
import com.chronosflow.core.domain.repository.AppUsageOverrideRepository
import com.chronosflow.core.domain.repository.AppUsageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScreenTimeSyncManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val dataSource = mockk<UsageStatsDataSource>()
    private val repository = mockk<AppUsageRepository>(relaxed = true)
    private val overrideRepository = mockk<AppUsageOverrideRepository>(relaxed = true)
    private val preferences = mockk<ChronosPreferencesDataSource>(relaxed = true)
    private val appEventLog = mockk<AppEventLog>(relaxed = true)
    private val manager = ScreenTimeSyncManager(
        context, dataSource, repository, overrideRepository, preferences, appEventLog
    )

    private val sampleDay = listOf(
        AppUsageSample("docs", "Docs", UsageCategory.PRODUCTIVE, 60),
        AppUsageSample("mail", "Mail", UsageCategory.PRODUCTIVE, 30),
        AppUsageSample("social", "Social", UsageCategory.DISTRACTING, 45),
        AppUsageSample("music", "Music", UsageCategory.NEUTRAL, 15)
    )

    @Test
    fun `runSync sums foreground minutes by category and stores the day`() = runTest {
        every { dataSource.hasUsageAccess() } returns true
        every { dataSource.usageForDay(any(), any()) } returns sampleDay
        coEvery { overrideRepository.getOverrides() } returns emptyMap()

        val stored = slot<AppUsageDay>()
        val outcome = manager.runSync(today = LocalDate.of(2026, 6, 15), days = 1)

        assertTrue(outcome is ScreenTimeSyncOutcome.Success)
        coVerify { repository.upsert(capture(stored)) }
        assertEquals(90, stored.captured.productiveMinutes)
        assertEquals(45, stored.captured.distractingMinutes)
        assertEquals(15, stored.captured.neutralMinutes)
        assertEquals(150, stored.captured.totalMinutes)
    }

    @Test
    fun `runSync applies a user override over the system category`() = runTest {
        every { dataSource.hasUsageAccess() } returns true
        every { dataSource.usageForDay(any(), any()) } returns sampleDay
        // User re-tags the distracting "social" app as productive.
        coEvery { overrideRepository.getOverrides() } returns mapOf("social" to UsageCategory.PRODUCTIVE)

        val stored = slot<AppUsageDay>()
        manager.runSync(today = LocalDate.of(2026, 6, 15), days = 1)

        coVerify { repository.upsert(capture(stored)) }
        assertEquals(135, stored.captured.productiveMinutes) // 60 + 30 + 45 (re-tagged)
        assertEquals(0, stored.captured.distractingMinutes)
        assertEquals(15, stored.captured.neutralMinutes)
    }

    @Test
    fun `isDistractionAboveUsualToday is true when today runs well above the window average`() = runTest {
        val today = LocalDate.of(2026, 6, 15)
        every { dataSource.hasUsageAccess() } returns true
        coEvery { repository.getForDateRange(any(), any()) } returns listOf(
            AppUsageDay(today.minusDays(2), productiveMinutes = 60, distractingMinutes = 20, neutralMinutes = 0),
            AppUsageDay(today.minusDays(1), productiveMinutes = 60, distractingMinutes = 20, neutralMinutes = 0),
            AppUsageDay(today, productiveMinutes = 30, distractingMinutes = 120, neutralMinutes = 0)
        )

        assertTrue(manager.isDistractionAboveUsualToday(today))
    }

    @Test
    fun `isDistractionAboveUsualToday is false without usage access`() = runTest {
        every { dataSource.hasUsageAccess() } returns false

        assertEquals(false, manager.isDistractionAboveUsualToday(LocalDate.of(2026, 6, 15)))
    }

    @Test
    fun `runSync skips and stores nothing when usage access is missing`() = runTest {
        every { dataSource.hasUsageAccess() } returns false

        val outcome = manager.runSync(today = LocalDate.of(2026, 6, 15), days = 1)

        assertTrue(outcome is ScreenTimeSyncOutcome.Skipped)
        coVerify(exactly = 0) { repository.upsert(any()) }
    }
}
