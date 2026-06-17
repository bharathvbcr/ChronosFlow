package com.chronosflow.wear

import android.content.Context
import android.content.ContextWrapper
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.chronosflow.wear.model.WearDaySummary
import com.chronosflow.wear.model.WearHabit
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ChronosHabitsTileProviderTest {
    private lateinit var provider: ChronosHabitsTileProvider
    private val tileRequestMethod = ChronosHabitsTileProvider::class.java.getDeclaredMethod(
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
        provider = ChronosHabitsTileProvider()
        attachBaseContextMethod.invoke(provider, RuntimeEnvironment.getApplication())
    }

    @Test
    fun `a never-synced store prompts to open the phone`() {
        assertTrue(requestTile().toString().contains(ChronosHabitsTileProvider.SYNC_LABEL))
    }

    @Test
    fun `a synced but empty day renders the no-habits state`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(receivedAtMillis = System.currentTimeMillis())
        )
        assertTrue(requestTile().toString().contains(ChronosHabitsTileProvider.EMPTY_LABEL))
    }

    @Test
    fun `tile is tappable and opens the app`() {
        assertTrue(requestTile().toString().contains("MainActivity"))
    }

    @Test
    fun `a stale mirror surfaces a sync-age warning on the tile`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(habitsTotal = 2, receivedAtMillis = 1_000L)
        )

        assertTrue(requestTile().toString().contains("Synced"))
    }

    @Test
    fun `stored summary renders progress and habit lines`() {
        DaySummaryStore.write(
            RuntimeEnvironment.getApplication(),
            WearDaySummary(
                habitsDone = 1,
                habitsTotal = 2,
                habits = listOf(
                    WearHabit(id = "h1", title = "Morning walk", streak = 7, done = false),
                    WearHabit(id = "h2", title = "Stretch", streak = 0, done = true)
                )
            )
        )

        val rendered = requestTile().toString()

        assertTrue(rendered.contains("1/2 done today"))
        assertTrue(rendered.contains("Morning walk"))
        assertTrue(rendered.contains("✓ Stretch"))
    }

    private fun requestTile(): TileBuilders.Tile {
        val request: RequestBuilders.TileRequest = mockk(relaxed = true)
        val future = tileRequestMethod.invoke(provider, request) as ListenableFuture<TileBuilders.Tile>
        assertTrue(future.isDone)
        return future.get()
    }
}
