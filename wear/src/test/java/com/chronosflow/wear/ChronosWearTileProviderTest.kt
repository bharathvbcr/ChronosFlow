package com.chronosflow.wear

import android.content.Context
import android.content.ContextWrapper
import androidx.wear.tiles.RequestBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ChronosWearTileProviderTest {
    private lateinit var provider: ChronosWearTileProvider
    private val tileRequestMethod = ChronosWearTileProvider::class.java.getDeclaredMethod(
        "onTileRequest",
        RequestBuilders.TileRequest::class.java
    ).apply { isAccessible = true }
    private val resourcesRequestMethod = ChronosWearTileProvider::class.java.getDeclaredMethod(
        "onTileResourcesRequest",
        RequestBuilders.ResourcesRequest::class.java
    ).apply { isAccessible = true }
    private val attachBaseContextMethod = ContextWrapper::class.java.getDeclaredMethod(
        "attachBaseContext",
        Context::class.java
    ).apply { isAccessible = true }

    @Before
    fun setUp() {
        provider = ChronosWearTileProvider()
        attachBaseContextMethod.invoke(provider, RuntimeEnvironment.getApplication())
    }

    @Test
    fun `onTileRequest returns immediate tile future with resting focus state`() {
        val request: RequestBuilders.TileRequest = mockk(relaxed = true)
        val tileFuture = tileRequestMethod.invoke(provider, request) as ListenableFuture<TileBuilders.Tile>

        assertTrue(tileFuture.isDone)
        val tile = tileFuture.get() as TileBuilders.Tile

        assertNotNull(tile)
        assertTrue(tile.toString().contains(ChronosWearTileProvider.RESTING_LABEL))
    }

    @Test
    fun `onTileResourcesRequest returns resources with version`() {
        val resourcesRequest: RequestBuilders.ResourcesRequest = mockk(relaxed = true)
        val resourcesFuture = resourcesRequestMethod.invoke(
            provider,
            resourcesRequest
        ) as ListenableFuture<ResourceBuilders.Resources>

        assertTrue(resourcesFuture.isDone)
        val resources = resourcesFuture.get() as ResourceBuilders.Resources

        assertNotNull(resources)
        assertEquals("1", resources::class.java.getMethod("getVersion").invoke(resources))
    }
}
