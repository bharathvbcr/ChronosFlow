package com.ChronosFlow.VBCR.wear

import android.content.Context
import android.content.ContextWrapper
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.ChronosFlow.VBCR.wear.model.WearDaySummary
import com.ChronosFlow.VBCR.wear.model.WearMed
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ChronosMedsTileProviderTest {
    private lateinit var provider: ChronosMedsTileProvider
    private val tileRequestMethod = ChronosMedsTileProvider::class.java.getDeclaredMethod(
        "onTileRequest",
        RequestBuilders.TileRequest::class.java
    ).apply { isAccessible = true }
    private val attachBaseContextMethod = ContextWrapper::class.java.getDeclaredMethod(
        "attachBaseContext",
        Context::class.java
    ).apply { isAccessible = true }

    @Before
    fun setUp() {
        DaySummaryStore.clear(RuntimeEnvironment.getApplication())
        provider = ChronosMedsTileProvider()
        attachBaseContextMethod.invoke(provider, RuntimeEnvironment.getApplication())
    }

    @Test
    fun `a never-synced store prompts to open the phone`() {
        assertTrue(requestTile().toString().contains(ChronosMedsTileProvider.SYNC_LABEL))
    }

    @Test
    fun `a synced but empty day renders the no-meds state`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(receivedAtMillis = System.currentTimeMillis())
        )
        assertTrue(requestTile().toString().contains(ChronosMedsTileProvider.EMPTY_LABEL))
    }

    @Test
    fun `tile is tappable and opens the app`() {
        assertTrue(requestTile().toString().contains("MainActivity"))
    }

    @Test
    fun `a stale mirror surfaces a sync-age warning on the tile`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(medsDueCount = 1, receivedAtMillis = 1_000L)
        )
        assertTrue(requestTile().toString().contains("Synced"))
    }

    @Test
    fun `stored summary renders due count and dose lines`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(
                medsDueCount = 1,
                meds = listOf(
                    WearMed(id = "m1", name = "Metformin", doseLabel = "500mg", reminderMinute = 8 * 60, taken = false),
                    WearMed(id = "m2", name = "Vitamin D", doseLabel = "1 pill", reminderMinute = 9 * 60, taken = true)
                )
            )
        )

        val rendered = requestTile().toString()

        assertTrue(rendered.contains("1 dose due"))
        assertTrue(rendered.contains("Metformin"))
        assertTrue(rendered.contains("✓ Vitamin D"))
    }

    @Test
    fun `all-taken day renders the all-taken state`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(
                medsDueCount = 0,
                meds = listOf(
                    WearMed(id = "m1", name = "Metformin", doseLabel = "500mg", reminderMinute = 8 * 60, taken = true)
                )
            )
        )
        assertTrue(requestTile().toString().contains(ChronosMedsTileProvider.ALL_TAKEN_LABEL))
    }

    @Test
    fun `meds tile description summarises the glance for TalkBack`() {
        assertEquals("Medication, 2 doses due", medsTileDescription(WearDaySummary(medsDueCount = 2)))
        assertEquals("Medication, 1 dose due", medsTileDescription(WearDaySummary(medsDueCount = 1)))
        assertEquals(
            "Medication, all doses taken",
            medsTileDescription(
                WearDaySummary(meds = listOf(WearMed("m1", "Metformin", "500mg", 8 * 60, true)))
            )
        )
        assertEquals("Medication, none today", medsTileDescription(WearDaySummary(receivedAtMillis = 1L)))
    }

    private fun requestTile(): TileBuilders.Tile {
        val request: RequestBuilders.TileRequest = mockk(relaxed = true)
        val future = tileRequestMethod.invoke(provider, request) as ListenableFuture<TileBuilders.Tile>
        assertTrue(future.isDone)
        return future.get()
    }
}
