package com.ChronosFlow.VBCR.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChronosCompletionCelebrationTest {

    @Test
    fun `peak scale token matches Focus celebration contract`() {
        assertEquals(1.08f, ChronosCompletionCelebrationPeakScale, 0.0001f)
    }

    @Test
    fun `play is no-op under reduced motion`() = runTest(UnconfinedTestDispatcher()) {
        val haptics = RecordingHaptics()
        val celebration = celebration(reduceMotion = true, haptics = haptics)
        celebration.play(withHaptic = true)
        assertEquals(1f, celebration.scale, 0.0001f)
        assertFalse(celebration.isCelebrating)
        assertTrue(haptics.types.isEmpty())
    }

    @Test
    fun `reduced motion skips haptic even when withHaptic is true`() = runTest(UnconfinedTestDispatcher()) {
        val haptics = RecordingHaptics()
        val celebration = celebration(reduceMotion = true, haptics = haptics)
        celebration.celebrate(withHaptic = true)
        assertTrue(haptics.types.isEmpty())
        assertEquals(listOf<HapticFeedbackType>(), haptics.types)
    }

    private fun TestScope.celebration(
        reduceMotion: Boolean,
        haptics: HapticFeedback,
    ): ChronosCompletionCelebration =
        ChronosCompletionCelebration(
            scaleAnimatable = Animatable(1f),
            haptics = haptics,
            scope = this,
            reduceMotionEnabled = reduceMotion,
        )

    private class RecordingHaptics : HapticFeedback {
        val types = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            types += hapticFeedbackType
        }
    }
}
