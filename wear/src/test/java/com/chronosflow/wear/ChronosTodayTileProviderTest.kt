package com.chronosflow.wear

import android.content.Context
import android.content.ContextWrapper
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.chronosflow.wear.model.WearDaySummary
import com.chronosflow.wear.model.WearTask
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
    fun `empty store renders the nothing-scheduled state`() {
        val tile = requestTile()

        assertTrue(tile.toString().contains(ChronosTodayTileProvider.EMPTY_LABEL))
        assertTrue(tile.toString().contains("No open tasks"))
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
        assertTrue(rendered.contains("until 14:30"))
        assertTrue(rendered.contains("Next: Gym · 16:00"))
        assertTrue(rendered.contains("3 tasks open"))
        assertTrue(rendered.contains("File taxes"))
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
