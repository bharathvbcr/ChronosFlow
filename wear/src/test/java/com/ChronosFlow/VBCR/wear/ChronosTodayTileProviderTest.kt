package com.ChronosFlow.VBCR.wear

import android.content.Context
import android.content.ContextWrapper
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.ChronosFlow.VBCR.wear.model.WearDaySummary
import com.ChronosFlow.VBCR.wear.model.WearTask
import com.ChronosFlow.VBCR.wear.presentation.WearStartPage
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
class ChronosTodayTileProviderTest {
    private lateinit var provider: ChronosTodayTileProvider
    private val tileRequestMethod = ChronosTodayTileProvider::class.java.getDeclaredMethod(
        "onTileRequest",
        RequestBuilders.TileRequest::class.java
    ).apply { isAccessible = true }
    private val resourcesRequestMethod = ChronosTodayTileProvider::class.java.getDeclaredMethod(
        "onTileResourcesRequest",
        RequestBuilders.ResourcesRequest::class.java
    ).apply { isAccessible = true }
    private val attachBaseContextMethod = ContextWrapper::class.java.getDeclaredMethod(
        "attachBaseContext",
        Context::class.java
    ).apply { isAccessible = true }

    @Before
    fun setUp() {
        DaySummaryStore.clear(RuntimeEnvironment.getApplication())
        WearFocusStateStore.clear(RuntimeEnvironment.getApplication())
        provider = ChronosTodayTileProvider()
        attachBaseContextMethod.invoke(provider, RuntimeEnvironment.getApplication())
    }

    @Test
    fun `a never-synced store prompts to open the phone`() {
        val tile = requestTile()

        assertTrue(tile.toString().contains(ChronosTodayTileProvider.SYNC_LABEL))
        assertTrue(tile.toString().contains("No open tasks"))
    }

    @Test
    fun `a synced but empty day renders the nothing-scheduled state`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(receivedAtMillis = System.currentTimeMillis())
        )

        assertTrue(requestTile().toString().contains(ChronosTodayTileProvider.EMPTY_LABEL))
    }

    @Test
    fun `stored summary renders now and next lines with task count`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(
                nowTitle = "Deep work",
                nowEndMinute = 14 * 60 + 30,
                nextTitle = "Gym",
                nextStartMinute = 16 * 60,
                openTaskCount = 3,
                tasks = listOf(WearTask(id = "t1", title = "File taxes"))
            )
        )

        val rendered = requestTile().toString()

        assertTrue(rendered.contains("Deep work"))
        // "until HH:mm" stays; the leading "Xm left" / next "in Xm" are now-relative (covered
        // deterministically by WearFormatTest), so assert only the time-stable substrings here.
        assertTrue(rendered.contains("until 14:30"))
        assertTrue(rendered.contains("Next: Gym"))
        assertTrue(rendered.contains("3 tasks open"))
        assertTrue(rendered.contains("File taxes"))
    }

    @Test
    fun `a stale mirror surfaces a sync-age warning on the tile`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(nowTitle = "Deep work", receivedAtMillis = 1_000L)
        )

        assertTrue(requestTile().toString().contains("Synced"))
    }

    @Test
    fun `a freshly-synced mirror shows no sync-age warning`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(nowTitle = "Deep work", receivedAtMillis = System.currentTimeMillis())
        )

        assertTrue(!requestTile().toString().contains("Synced"))
    }

    @Test
    fun `schedule tile is tappable and opens the app at the Now page`() {
        val rendered = requestTile().toString()

        assertTrue(rendered.contains("MainActivity"))
        assertTrue(rendered.contains(WearStartPage.NOW))
    }

    @Test
    fun `active focus tile is tappable and opens the Focus screen`() {
        WearFocusStateStore.write(
            RuntimeEnvironment.getApplication(),
            WearFocusStateStore.FocusState(
                active = true,
                title = "Deep work",
                plannedEndAtMillis = System.currentTimeMillis() + 25 * 60 * 1000L,
                totalSeconds = 25 * 60
            )
        )

        val rendered = requestTile().toString()

        assertTrue(rendered.contains("MainActivity"))
        assertTrue(rendered.contains(WearStartPage.FOCUS))
    }

    @Test
    fun `onTileResourcesRequest returns resources with version`() {
        val resourcesFuture = resourcesRequestMethod.invoke(
            provider,
            mockk<RequestBuilders.ResourcesRequest>(relaxed = true)
        ) as ListenableFuture<ResourceBuilders.Resources>

        assertTrue(resourcesFuture.isDone)
        val resources = resourcesFuture.get() as ResourceBuilders.Resources
        assertEquals(ChronosTileUi.RESOURCES_VERSION, resources.version)
    }

    private fun requestTile(): TileBuilders.Tile {
        val request: RequestBuilders.TileRequest = mockk(relaxed = true)
        val future = tileRequestMethod.invoke(provider, request) as ListenableFuture<TileBuilders.Tile>
        assertTrue(future.isDone)
        return future.get()
    }
}
