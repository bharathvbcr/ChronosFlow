package com.ChronosFlow.VBCR.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.ChronosFlow.VBCR"
private const val UI_TIMEOUT_MILLIS = 5_000L
private const val MACRO_BLOCK_TITLE = "Focus Block"

@RunWith(AndroidJUnit4::class)
class ChronosMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 5
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun warmLaunchDayDial() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 5
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    @Test
    fun dayPlanFocusAndCommandPaletteInteractions() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        clickDescription("Plan")
        waitForText("Generate")

        clickDescription("Focus")
        waitForAnyText(
            "Pick a session length, then start a protected interval with live notification support.",
            "Ready to focus",
            "Next block ready"
        )

        clickDescription("Today")
        openCommandPalette()
        waitForText("Command palette")
        device.pressBack()
        device.waitForIdle()
    }

    @Test
    fun dayDialDragScrollAndEditInteractions() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        clickDescription("Plan")
        waitForText("Generate")
        createAndOpenPlannerBlockEditor()
        clickDescription("Save changes to $MACRO_BLOCK_TITLE")
        device.pressBack()
        device.waitForIdle()

        clickDescription("Today")
        scrollCurrentSurface()
        dragChronosDialSurface()
    }

    @Test
    fun tasksRouteEdgeBackTransition() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        openTasksRouteFromQuickCreate()
        edgeBackGesture()
        waitForText("Open time")
    }

    private fun MacrobenchmarkScope.openCommandPalette() {
        clickDescription("Open command palette")
    }

    private fun MacrobenchmarkScope.openTasksRouteFromQuickCreate() {
        clickDescription("Open quick create")
        waitForText("New task")
        clickText("New task")

        waitForText("Enter a task title to continue.")
        device.pressBack()
        device.waitForIdle()

        waitForText("Tasks")
    }

    private fun MacrobenchmarkScope.createAndOpenPlannerBlockEditor() {
        clickDescription("Add a block manually")
        waitForText("New Block")
        clickText("Save block")

        waitForText(MACRO_BLOCK_TITLE)
        clickText(MACRO_BLOCK_TITLE)
        waitForText("Calendar export")
    }

    private fun MacrobenchmarkScope.clickDescription(description: String) {
        val selector = By.desc(description)
        check(device.wait(Until.hasObject(selector), UI_TIMEOUT_MILLIS)) {
            "$description was not visible during macrobenchmark capture"
        }
        device.findObject(selector).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.clickText(text: String) {
        val selector = By.text(text)
        check(device.wait(Until.hasObject(selector), UI_TIMEOUT_MILLIS)) {
            "$text was not visible during macrobenchmark capture"
        }
        device.findObject(selector).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitForText(text: String) {
        val selector = By.text(text)
        check(device.wait(Until.hasObject(selector), UI_TIMEOUT_MILLIS)) {
            "$text was not visible during macrobenchmark capture"
        }
    }

    private fun MacrobenchmarkScope.waitForAnyText(vararg texts: String) {
        val visibleText = texts.firstOrNull { text ->
            device.wait(Until.hasObject(By.text(text)), UI_TIMEOUT_MILLIS)
        }
        check(visibleText != null) {
            "${texts.joinToString()} was not visible during macrobenchmark capture"
        }
    }

    private fun MacrobenchmarkScope.scrollCurrentSurface() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 2, height * 3 / 4, width / 2, height / 3, 12)
        device.waitForIdle()
        device.swipe(width / 2, height / 3, width / 2, height * 3 / 4, 12)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.dragChronosDialSurface() {
        val width = device.displayWidth
        val height = device.displayHeight
        val centerX = width / 2
        val centerY = height * 2 / 5
        val radius = minOf(width, height) / 5
        device.swipe(centerX + radius, centerY, centerX, centerY + radius, 18)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.edgeBackGesture() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(1, height / 2, width * 2 / 3, height / 2, 30)
        device.waitForIdle()
    }
}
