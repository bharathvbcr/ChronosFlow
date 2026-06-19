package com.ChronosFlow.VBCR.core.data.assist

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ProactiveAssistCacheTest {
    private val store = mutableMapOf<String, String>()
    private val dataSource: ChronosPreferencesDataSource = mockk()
    private val cache = ProactiveAssistCache(dataSource)
    private val today: LocalDate = LocalDate.of(2026, 6, 11)

    @Before
    fun setup() {
        every { dataSource.putString(any(), any()) } answers { store[firstArg()] = secondArg() }
        every { dataSource.getString(any(), any()) } answers { store[firstArg()] ?: secondArg() }
    }

    @Test
    fun `daily coach line round-trips for the same date`() {
        cache.putDailyCoachLine(today, "Stayed close to plan", "Keep the anchor block", "GEMINI_NANO")

        val line = cache.dailyCoachLine(today)

        assertEquals(
            CachedAssistLine("Stayed close to plan", "Keep the anchor block", "GEMINI_NANO"),
            line
        )
    }

    @Test
    fun `daily coach line is null for a different date`() {
        cache.putDailyCoachLine(today, "Stayed close to plan", "Keep the anchor block", "LOCAL")

        assertNull(cache.dailyCoachLine(today.plusDays(1)))
    }

    @Test
    fun `focus next block line requires matching date and block id`() {
        cache.putFocusNextBlockLine(today, "block-1", "Next up: Deep work — protected slot")

        assertEquals(
            "Next up: Deep work — protected slot",
            cache.focusNextBlockLine(today, "block-1")
        )
        assertNull(cache.focusNextBlockLine(today, "block-2"))
        assertNull(cache.focusNextBlockLine(today.plusDays(1), "block-1"))
    }

    @Test
    fun `daily coach write emits a refresh signal`() = runTest {
        cache.dailyCoachWrites.test {
            cache.putDailyCoachLine(today, "Headline", "Next step", "LOCAL")
            assertEquals(today, awaitItem())
        }
    }
}
