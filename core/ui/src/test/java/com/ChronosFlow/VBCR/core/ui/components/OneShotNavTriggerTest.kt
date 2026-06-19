package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Locks in the structural contract of [OneShotNavTrigger] — the exact behavior whose absence caused
 * the "add FAB does nothing on repeat" bug: a one-shot must re-fire when its request generation
 * changes even though nothing else did, and must NOT fire on unrelated recompositions.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OneShotNavTriggerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `fires once when active`() {
        var fires = 0
        composeTestRule.setContent {
            OneShotNavTrigger(active = true, requestGeneration = 0) { fires++ }
        }
        composeTestRule.waitForIdle()
        assertEquals(1, fires)
    }

    @Test
    fun `does not fire when inactive`() {
        var fires = 0
        composeTestRule.setContent {
            OneShotNavTrigger(active = false, requestGeneration = 0) { fires++ }
        }
        composeTestRule.waitForIdle()
        assertEquals(0, fires)
    }

    @Test
    fun `re-fires when request generation changes even though the key is otherwise unchanged`() {
        var fires = 0
        val generation = mutableIntStateOf(0)
        composeTestRule.setContent {
            OneShotNavTrigger(active = true, requestGeneration = generation.intValue) { fires++ }
        }
        composeTestRule.waitForIdle()
        assertEquals(1, fires)

        // Mirrors re-navigating to the same Tasks(target="add") key: only the generation moves.
        composeTestRule.runOnUiThread { generation.intValue = 1 }
        composeTestRule.waitForIdle()
        assertEquals(2, fires)
    }

    @Test
    fun `re-fires when a discriminator changes`() {
        var fires = 0
        val capture = mutableStateOf("first")
        composeTestRule.setContent {
            OneShotNavTrigger(active = true, requestGeneration = 0, capture.value) { fires++ }
        }
        composeTestRule.waitForIdle()
        assertEquals(1, fires)

        composeTestRule.runOnUiThread { capture.value = "second" }
        composeTestRule.waitForIdle()
        assertEquals(2, fires)
    }

    @Test
    fun `does not re-fire on an unrelated recomposition`() {
        var fires = 0
        val unrelated = mutableIntStateOf(0)
        composeTestRule.setContent {
            // Read an unrelated state so the content recomposes when it changes, but it is NOT a key
            // of the trigger — the one-shot must stay consumed.
            unrelated.intValue
            OneShotNavTrigger(active = true, requestGeneration = 0) { fires++ }
        }
        composeTestRule.waitForIdle()
        assertEquals(1, fires)

        composeTestRule.runOnUiThread { unrelated.intValue = 1 }
        composeTestRule.waitForIdle()
        assertEquals(1, fires)
    }
}
