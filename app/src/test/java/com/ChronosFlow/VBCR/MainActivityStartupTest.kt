package com.ChronosFlow.VBCR

import com.ChronosFlow.VBCR.core.notifications.NotificationLaunch
import com.ChronosFlow.VBCR.navigation.ChronosRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.inject.Provider

class MainActivityStartupTest {
    @Test
    fun `full shell waits for the first focus window to settle`() {
        val delayMillis = Class.forName("com.ChronosFlow.VBCR.MainActivityKt")
            .getDeclaredField("STARTUP_SHELL_DEFER_MILLIS")
            .getLong(null)

        assertTrue(delayMillis >= 300L)
    }

    @Test
    fun `application startup work waits until the first shell can draw`() {
        val shellDelayMillis = Class.forName("com.ChronosFlow.VBCR.MainActivityKt")
            .getDeclaredField("STARTUP_SHELL_DEFER_MILLIS")
            .getLong(null)
        val badgeDataDelayMillis = Class.forName("com.ChronosFlow.VBCR.MainActivityKt")
            .getDeclaredField("SHELL_BADGE_DATA_DEFER_MILLIS")
            .getLong(null)
        val applicationDelayMillis = Class.forName("com.ChronosFlow.VBCR.ChronosApplicationKt")
            .getDeclaredField("APPLICATION_STARTUP_WORK_DEFER_MILLIS")
            .getLong(null)

        assertTrue(badgeDataDelayMillis > shellDelayMillis)
        assertTrue(badgeDataDelayMillis >= 20_000L)
        assertTrue(applicationDelayMillis > shellDelayMillis)
        assertTrue(applicationDelayMillis >= 45_000L)
    }

    @Test
    fun `notification channels are created synchronously on startup`() {
        val source = listOf(
            File("src/main/java/com/ChronosFlow/VBCR/ChronosApplication.kt"),
            File("app/src/main/java/com/ChronosFlow/VBCR/ChronosApplication.kt")
        ).first(File::exists).readText()

        assertTrue(source.contains("ensureNotificationChannels()"))
        assertTrue(source.contains("private fun ensureNotificationChannels()"))
        assertTrue(!source.contains("scheduleDeferredNotificationChannelSetup()"))
    }

    @Test
    fun `command palette providers wait until palette interaction`() {
        val source = listOf(
            File("src/main/java/com/ChronosFlow/VBCR/MainActivity.kt"),
            File("app/src/main/java/com/ChronosFlow/VBCR/MainActivity.kt")
        ).first(File::exists).readText()

        assertTrue(source.contains("val commandPaletteCommands = remember"))
        assertTrue(!source.contains("val commands = remember("))
        assertTrue(source.contains("private fun CommandPaletteHost"))
        assertTrue(!source.contains("val commandSearchViewModel: CommandSearchViewModel = hiltViewModel()"))
    }

    @Test
    fun `shell badge summary waits until after the first frame`() {
        val source = listOf(
            File("src/main/java/com/ChronosFlow/VBCR/MainActivity.kt"),
            File("app/src/main/java/com/ChronosFlow/VBCR/MainActivity.kt")
        ).first(File::exists).readText()

        assertTrue(source.contains("private fun rememberDeferredShellState()"))
        assertTrue(source.contains("withFrameNanos { }"))
        assertTrue(source.contains("delay(SHELL_BADGE_DATA_DEFER_MILLIS)"))
        assertTrue(source.contains("return ChronosShellState()"))
        assertTrue(source.contains("val shellState = rememberDeferredShellState()"))
    }

    @Test
    fun `device auth capability refresh waits until post frame background work`() {
        val source = listOf(
            File("src/main/java/com/ChronosFlow/VBCR/MainActivity.kt"),
            File("app/src/main/java/com/ChronosFlow/VBCR/MainActivity.kt")
        ).first(File::exists).readText()

        assertTrue(source.contains("private suspend fun refreshDeviceAuthAfterFirstFrame"))
        assertTrue(source.contains("appLockState.appLockEnabled && appLockState.isAppLocked"))
        assertTrue(source.contains("delay(LOCKED_APP_AUTH_DEFER_MILLIS)"))
        assertTrue(source.contains("withContext(Dispatchers.IO)"))
        assertTrue(source.contains("appLockViewModel.refreshDeviceAuth(activity)"))
    }

    @Test
    fun `startup does not force root shortcut focus`() {
        val source = listOf(
            File("src/main/java/com/ChronosFlow/VBCR/MainActivity.kt"),
            File("app/src/main/java/com/ChronosFlow/VBCR/MainActivity.kt")
        ).first(File::exists).readText()

        assertTrue(!source.contains("FocusRequester()"))
        assertTrue(!source.contains("requestFocus()"))
    }

    @Test
    fun `daydial sidebar launch does not eagerly request the data view model`() {
        val source = listOf(
            File("../feature/daydial/src/main/java/com/ChronosFlow/VBCR/feature/daydial/DayDialScreen.kt"),
            File("feature/daydial/src/main/java/com/ChronosFlow/VBCR/feature/daydial/DayDialScreen.kt")
        ).first(File::exists).readText()

        assertTrue(source.contains("launchTargetUsesLightweightSidebar(launchTarget)"))
        assertTrue(!source.contains("viewModel: DayDialViewModel = hiltViewModel()"))
    }

    @Test
    fun `work manager auto initializer is removed from cold startup`() {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first(File::exists).readText()

        assertTrue(
            Regex(
                pattern = """<meta-data\s+android:name="androidx.work.WorkManagerInitializer"\s+tools:node="remove"\s*/>""",
                option = RegexOption.DOT_MATCHES_ALL
            ).containsMatchIn(manifest)
        )
    }

    @Test
    fun `launch window background is opaque while compose warms`() {
        val defaultStyle = listOf(
            File("src/main/res/values/styles.xml"),
            File("app/src/main/res/values/styles.xml")
        ).first(File::exists).readText()
        val v27Style = listOf(
            File("src/main/res/values-v27/styles.xml"),
            File("app/src/main/res/values-v27/styles.xml")
        ).first(File::exists).readText()
        val launchBackground = listOf(
            File("src/main/res/drawable/chronos_launch_window_background.xml"),
            File("app/src/main/res/drawable/chronos_launch_window_background.xml")
        ).first(File::exists).readText()

        assertTrue(defaultStyle.contains("android:windowBackground"))
        assertTrue(v27Style.contains("android:windowBackground"))
        assertTrue(!defaultStyle.contains("android:windowBackground\">@android:color/transparent"))
        assertTrue(!v27Style.contains("android:windowBackground\">@android:color/transparent"))
        assertTrue(defaultStyle.contains("@drawable/chronos_launch_window_background"))
        assertTrue(v27Style.contains("@drawable/chronos_launch_window_background"))
        assertTrue(launchBackground.contains("""<solid android:color="#F6F4EF" />"""))
    }

    @Test
    fun `debug runtime coverage is opt in for deployable builds`() {
        val buildFile = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts")
        ).first(File::exists).readText()

        assertTrue(buildFile.contains("chronos.enableDebugCoverage"))
        assertTrue(!buildFile.contains("enableUnitTestCoverage = true"))
        assertTrue(!buildFile.contains("enableAndroidTestCoverage = true"))
    }

    @Test
    fun `portable backup startup is provider backed to avoid eager database open`() {
        val initializerField = Class.forName("com.ChronosFlow.VBCR.ChronosApplication")
            .getDeclaredField("portableBackupInitializer")

        assertEquals(Provider::class.java, initializerField.type)
    }

    @Test
    fun `sidebar notification day target seeds the first shell state`() {
        assertEquals(
            ChronosRoute.Day.TARGET_TASKS,
            initialDayTargetForNotificationLaunch(
                NotificationLaunch(
                    section = ChronosRoute.Day.section,
                    dayTarget = ChronosRoute.Day.TARGET_TASKS
                )
            )
        )
        assertEquals(
            null,
            initialDayTargetForNotificationLaunch(
                NotificationLaunch(
                    section = ChronosRoute.Tasks.section,
                    dayTarget = ChronosRoute.Day.TARGET_TASKS
                )
            )
        )
    }
}
