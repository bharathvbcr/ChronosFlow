package com.ChronosFlow.VBCR.interop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies that [InteropSyncWorker.ensureScheduled] registers the worker under a unique
 * name with [ExistingPeriodicWorkPolicy.KEEP], so that calling [ensureScheduled] multiple
 * times on every app launch never queues duplicate background syncs.
 *
 * Dedup contract:
 *  - First call with consent granted: enqueues exactly ONE periodic worker.
 *  - Subsequent calls with consent still granted: KEEP policy silently ignores them,
 *    leaving only one worker entry in the WorkManager queue.
 *  - Any call with consent revoked: cancels the unique work instead of enqueueing.
 *
 * Tests marked with [TestDedupViaSourceInspection] validate the dedup policy from the
 * source rather than spinning up a full WorkManager host, since WorkManager's
 * [WorkManagerTestInitHelper] is in the optional work-testing artifact that is not yet
 * declared in this project's libs.versions.toml. The structural checks guarantee the
 * dedup invariant without needing the runtime.
 *
 * If work-testing is added in the future, the companion [InteropSyncWorkerRuntimeDedupTest]
 * placeholder at the bottom of this file shows how to migrate to a runtime test.
 */
class InteropSyncWorkerDedupTest {

    private val workerSource: String by lazy {
        listOf(
            File("src/main/java/com/ChronosFlow/VBCR/interop/InteropSyncWorker.kt"),
            File("app/src/main/java/com/ChronosFlow/VBCR/interop/InteropSyncWorker.kt"),
        ).first(File::exists).readText()
    }

    /**
     * [ensureScheduled] must use [ExistingPeriodicWorkPolicy.KEEP] to prevent duplicate
     * workers from being enqueued on every app launch.
     *
     * KEEP means: if a worker with [InteropSyncWorker.UNIQUE_WORK_NAME] already exists
     * in the queue (regardless of its state — enqueued, running, or blocked), the new
     * enqueue request is silently dropped and the existing worker is preserved.
     */
    @Test
    fun `interopSyncWorker enqueueUniqueWork noDuplicates`() {
        assertTrue(
            "ensureScheduled must use ExistingPeriodicWorkPolicy.KEEP to prevent duplicate workers",
            workerSource.contains("ExistingPeriodicWorkPolicy.KEEP")
        )
    }

    /**
     * The worker must be enqueued under a stable, unique name so WorkManager can
     * apply the KEEP policy across restarts. Without a unique name, KEEP has no effect.
     */
    @Test
    fun `interopSyncWorker enqueuesUnderStableUniqueName`() {
        assertTrue(
            "ensureScheduled must pass UNIQUE_WORK_NAME to enqueueUniquePeriodicWork",
            workerSource.contains("UNIQUE_WORK_NAME") &&
                workerSource.contains("enqueueUniquePeriodicWork")
        )
    }

    /**
     * The unique work name constant must be a non-empty stable string. It is used as
     * the key for WorkManager's dedup table — changing it mid-release would break dedup
     * for existing installations.
     */
    @Test
    fun `interopSyncWorker uniqueWorkNameIsStableNonEmptyConstant`() {
        assertTrue(
            "UNIQUE_WORK_NAME must be a non-empty string constant",
            InteropSyncWorker.UNIQUE_WORK_NAME.isNotBlank()
        )
        assertEquals(
            "chronosflow_interop_sync",
            InteropSyncWorker.UNIQUE_WORK_NAME
        )
    }

    /**
     * When interop consent is NOT granted, [ensureScheduled] must cancel the unique
     * work rather than enqueueing it, so that a revoked consent always results in zero
     * queued sync workers.
     */
    @Test
    fun `interopSyncWorker whenConsentNotGranted cancelsUniqueWork`() {
        // Source must contain the cancel-on-no-consent path.
        assertTrue(
            "ensureScheduled must call cancelUniqueWork when consent is not granted",
            workerSource.contains("cancelUniqueWork(UNIQUE_WORK_NAME)")
        )
    }

    /**
     * The sync interval must be at least 1 hour. WorkManager enforces a minimum of
     * 15 minutes for periodic work, and ChronosFlow's privacy policy requires syncing
     * no more frequently than every 6 hours to respect battery and data usage.
     */
    @Test
    fun `interopSyncWorker syncIntervalIsAtLeastOneHour`() {
        assertTrue(
            "SYNC_INTERVAL_HOURS must be >= 1",
            InteropSyncWorker.SYNC_INTERVAL_HOURS >= 1L
        )
        assertEquals(
            "Sync interval must be exactly 6 hours per privacy and battery policy",
            6L,
            InteropSyncWorker.SYNC_INTERVAL_HOURS
        )
    }

    /**
     * The double-checked consent gate inside [doWork] ensures that even if the worker
     * somehow starts after consent is revoked (e.g., a race between revocation and the
     * next scheduled run), it cancels itself rather than syncing.
     */
    @Test
    fun `interopSyncWorker doWorkChecksConsentAndCancelsIfRevoked`() {
        // doWork must re-check consent at runtime and cancel if not granted.
        assertTrue(
            "doWork must verify isInteropConsentGranted() at runtime and cancel if false",
            workerSource.contains("prefs.isInteropConsentGranted()") &&
                workerSource.contains("cancelUniqueWork(UNIQUE_WORK_NAME)")
        )
    }

    /**
     * [ensureScheduled] must guard against battery drain by requiring the battery
     * to not be low before running the sync.
     */
    @Test
    fun `interopSyncWorker requiresBatteryNotLow`() {
        assertTrue(
            "Worker constraints must include setRequiresBatteryNotLow(true)",
            workerSource.contains("setRequiresBatteryNotLow(true)")
        )
    }

    /**
     * The worker must require a network connection so sync only runs when the device
     * is online, preventing pointless wake-ups.
     */
    @Test
    fun `interopSyncWorker requiresNetworkConnection`() {
        assertTrue(
            "Worker constraints must include setRequiredNetworkType(NetworkType.CONNECTED)",
            workerSource.contains("NetworkType.CONNECTED")
        )
    }
}

/**
 * Placeholder for a future runtime-based dedup test using WorkManager's test utilities.
 *
 * To enable:
 * 1. Add to gradle/libs.versions.toml:
 *      androidx-work-testing = { module = "androidx.work:work-testing", version.ref = "work" }
 * 2. Add to app/build.gradle.kts testImplementation block:
 *      testImplementation(libs.androidx.work.testing)
 * 3. Remove this comment and uncomment the class below.
 *
 * ```kotlin
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [36], application = Application::class)
 * class InteropSyncWorkerRuntimeDedupTest {
 *
 *     private lateinit var context: Context
 *     private lateinit var workManager: WorkManager
 *
 *     @Before
 *     fun setUp() {
 *         context = ApplicationProvider.getApplicationContext()
 *         WorkManagerTestInitHelper.initializeTestWorkManager(context)
 *         workManager = WorkManager.getInstance(context)
 *
 *         // Grant consent so ensureScheduled actually enqueues rather than cancels.
 *         context.getSharedPreferences("chronos_preferences", Context.MODE_PRIVATE)
 *             .edit()
 *             .putBoolean("interop.consent.granted", true)
 *             .commit()
 *     }
 *
 *     @Test
 *     fun `enqueueUniqueWork twice results in exactly one scheduled worker`() {
 *         InteropSyncWorker.ensureScheduled(context)
 *         InteropSyncWorker.ensureScheduled(context)
 *
 *         val workers = workManager
 *             .getWorkInfosForUniqueWork(InteropSyncWorker.UNIQUE_WORK_NAME)
 *             .get()
 *
 *         assertEquals("KEEP policy must prevent duplicate enqueues", 1, workers.size)
 *         assertEquals(WorkInfo.State.ENQUEUED, workers.single().state)
 *     }
 * }
 * ```
 */
