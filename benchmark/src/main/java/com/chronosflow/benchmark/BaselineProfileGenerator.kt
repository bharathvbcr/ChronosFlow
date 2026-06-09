package com.chronosflow.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.chronosflow"
private const val UI_TIMEOUT_MILLIS = 5_000L
private const val PROFILE_BLOCK_TITLE = "Focus Block"

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        openCommandPalette()
        waitForText("Command palette")
        device.pressBack()
        device.waitForIdle()

        clickDescription("Plan")
        waitForText("Generate")
        createEditAndStartFocusBlock()

        // Warm relaunch path used by returning users.
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openCommandPalette() {
        clickDescription("Open command palette")
    }

    private fun MacrobenchmarkScope.createEditAndStartFocusBlock() {
        clickDescription("Add a block manually")
        waitForText("New Block")
        clickText("Save block")

        waitForText(PROFILE_BLOCK_TITLE)
        clickText(PROFILE_BLOCK_TITLE)
        waitForText("Calendar export")
        clickDescription("Save changes to $PROFILE_BLOCK_TITLE")
        clickDescription("Start focus for $PROFILE_BLOCK_TITLE")
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.clickDescription(description: String) {
        val selector = By.desc(description)
        check(device.wait(Until.hasObject(selector), UI_TIMEOUT_MILLIS)) {
            "$description was not visible during baseline profile capture"
        }
        device.findObject(selector).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.clickText(text: String) {
        val selector = By.text(text)
        check(device.wait(Until.hasObject(selector), UI_TIMEOUT_MILLIS)) {
            "$text was not visible during baseline profile capture"
        }
        device.findObject(selector).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitForText(text: String) {
        val selector = By.text(text)
        check(device.wait(Until.hasObject(selector), UI_TIMEOUT_MILLIS)) {
            "$text was not visible during baseline profile capture"
        }
    }
}
