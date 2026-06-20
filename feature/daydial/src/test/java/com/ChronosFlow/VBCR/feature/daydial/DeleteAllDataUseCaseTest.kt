package com.ChronosFlow.VBCR.feature.daydial

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that [DeleteAllDataUseCase] clears every persistence layer — Room tables,
 * WorkManager queue, and scheduled alarms — without throwing.
 *
 * Uses Robolectric for the [Context] dependency (WorkManager, SharedPreferences,
 * DataStore file path) and MockK to isolate [ChronosDatabase] and [AlarmScheduler].
 *
 * WorkManager requires manual initialization in these tests because ChronosFlow's
 * manifest removes the default ContentProvider-based auto-initializer
 * (`WorkManagerInitializer`) — [WorkManager.initialize] is called in [setUp] before
 * the use case is constructed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DeleteAllDataUseCaseTest {

    private val database: ChronosDatabase = mockk(relaxed = true)
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private lateinit var context: Context
    private lateinit var useCase: DeleteAllDataUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // WorkManager's ContentProvider auto-initializer is removed from the app manifest
        // (ChronosFlow uses on-demand init). Initialize it manually for the test process.
        try {
            WorkManager.initialize(
                context,
                Configuration.Builder().build()
            )
        } catch (_: IllegalStateException) {
            // Already initialized by a previous test in the same JVM process — safe to ignore.
        }

        // Pre-seed a SharedPreferences entry so the clear step has something to act on.
        context.getSharedPreferences("chronos_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("test_key", "test_value")
            .commit()

        useCase = DeleteAllDataUseCase(
            context = context,
            database = database,
            alarmScheduler = alarmScheduler
        )
    }

    /**
     * The primary contract: invoking DeleteAllDataUseCase must call
     * [ChronosDatabase.clearAllTables] so every Room table is wiped.
     */
    @Test
    fun `deleteAllData clearsAllRoomTables`() = runTest {
        useCase.invoke()

        // clearAllTables() is not a suspend function on RoomDatabase — use verify.
        verify(exactly = 1) { database.clearAllTables() }
    }

    /**
     * All scheduled alarms must be cancelled before the DB is wiped so that a
     * PendingIntent firing after the wipe cannot re-write data to the now-empty database.
     */
    @Test
    fun `deleteAllData cancelsAllScheduledAlarms`() = runTest {
        useCase.invoke()

        // cancelAllAlarms is not a suspend function — use verify, not coVerify.
        verify(exactly = 1) { alarmScheduler.cancelAllAlarms() }
    }

    /**
     * The use case must complete without throwing even when the DataStore preference
     * file does not exist on disk (normal state during a fresh install).
     */
    @Test
    fun `deleteAllData completesWithoutException whenDataStoreFileAbsent`() = runTest {
        // No DataStore file is created by default in a Robolectric environment.
        // The use case must handle the missing file gracefully.
        useCase.invoke() // Must not throw
    }

    /**
     * SharedPreferences entries written by ChronosFlow components must be erased
     * so no stale config survives the data wipe.
     */
    @Test
    fun `deleteAllData clearsChronosSharedPreferences`() = runTest {
        useCase.invoke()

        val prefs = context.getSharedPreferences("chronos_preferences", Context.MODE_PRIVATE)
        assertNotNull("SharedPreferences instance should exist after clear", prefs)
        // After the use case runs the pre-seeded key must be gone.
        org.junit.Assert.assertNull(prefs.getString("test_key", null))
    }

    /**
     * Alarm cancellation must happen BEFORE the database wipe so that an alarm
     * firing between the two operations cannot write stale data into an already-cleared DB.
     * MockK captures call order; verify both calls occurred in the right sequence.
     */
    @Test
    fun `deleteAllData cancelsAlarmsBeforeWipingDatabase`() = runTest {
        val callOrder = mutableListOf<String>()
        // cancelAllAlarms is not suspend; use every{}
        every { alarmScheduler.cancelAllAlarms() } answers { callOrder += "cancelAlarms" }
        // clearAllTables is not suspend on RoomDatabase — use every{} not coEvery{}.
        every { database.clearAllTables() } answers { callOrder += "clearTables" }

        useCase.invoke()

        org.junit.Assert.assertEquals(
            "Alarms must be cancelled before Room tables are cleared",
            listOf("cancelAlarms", "clearTables"),
            callOrder
        )
    }
}
